package in.railconnect.core.reservation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.reservation.ReservationModels.AllocationAuditEvent;
import in.railconnect.core.reservation.ReservationModels.AllocationCounts;
import in.railconnect.core.reservation.ReservationModels.AllocationPolicyAuditEvent;
import in.railconnect.core.reservation.ReservationModels.AllocationPolicyRequest;
import in.railconnect.core.reservation.ReservationModels.AllocationPolicyVersion;
import in.railconnect.core.reservation.ReservationModels.CreateReservationRequest;
import in.railconnect.core.reservation.ReservationModels.Page;
import in.railconnect.core.reservation.ReservationModels.PassengerAllocation;
import in.railconnect.core.reservation.ReservationModels.PassengerInput;
import in.railconnect.core.reservation.ReservationModels.ReservationDetails;
import in.railconnect.core.reservation.ReservationModels.ReservationSummary;
import in.railconnect.core.reservation.ReservationModels.StateChange;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReservationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final String REQUESTED = "REQUESTED";
    private static final String PENDING_BOOKING = "ALLOCATED_PENDING_BOOKING";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String RELEASED = "RELEASED";
    private static final String EXPIRED = "EXPIRED";
    private static final String FAILED = "FAILED";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final int maximumPassengers;
    private final int maximumRequestsPerMinute;

    public ReservationService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${railconnect.reservations.maximum-passengers-per-request:4}") int maximumPassengers,
            @Value("${railconnect.reservations.requests-per-minute:10}") int maximumRequestsPerMinute
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.maximumPassengers = Math.max(1, Math.min(maximumPassengers, 4));
        this.maximumRequestsPerMinute = Math.max(1, maximumRequestsPerMinute);
    }

    @Transactional
    public ReservationDetails createRequest(CreateReservationRequest request, String idempotencyKey, UUID owner) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw failure(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key header is required");
        }
        if (request.passengers().size() > maximumPassengers) {
            throw failure(HttpStatus.BAD_REQUEST, "PASSENGER_COUNT_INVALID",
                    "The passenger count exceeds the configured request limit");
        }

        String classCode = normalizeClassCode(request.coachClassCode());
        List<PassengerInput> passengers = normalizePassengers(request.passengers());
        if (request.inventoryHoldIds().size() != passengers.size()) {
            throw failure(HttpStatus.BAD_REQUEST, "HOLD_COUNT_MISMATCH",
                    "Provide exactly one active inventory hold for each passenger");
        }
        String fingerprint = fingerprint(request, classCode, passengers);
        advisoryLock("reservation-user:" + owner);
        ReservationIdentity existing = findByIdempotency(owner, idempotencyKey);
        if (existing != null) {
            if (!fingerprint.equals(existing.fingerprint())) {
                throw failure(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "This idempotency key was already used for a different request");
            }
            return details(existing.requestId(), owner);
        }

        enforceRequestRateLimit(owner);
        Journey journey = validateJourney(request.trainRunId(), request.journeyDate(),
                request.fromStationId(), request.toStationId(), classCode);
        List<UUID> distinctHolds = request.inventoryHoldIds().stream()
                .distinct()
                .sorted()
                .toList();
        if (distinctHolds.size() != passengers.size()) {
            throw failure(HttpStatus.BAD_REQUEST, "HOLD_REFERENCE_DUPLICATED",
                    "Each passenger must reference a different inventory hold");
        }
        lockAndValidateHolds(distinctHolds, owner, request.trainRunId(), journey, classCode);

        UUID requestId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO reservation_requests(
                    reservation_request_id, owner_user_id, train_run_id, journey_date,
                    from_stop_index, to_stop_index, coach_class_code, group_seating_requested,
                    idempotency_key, request_fingerprint
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requestId, owner, request.trainRunId(), request.journeyDate(),
                journey.fromIndex(), journey.toIndex(), classCode, request.groupSeatingRequested(),
                idempotencyKey, fingerprint);
        for (int index = 0; index < passengers.size(); index++) {
            PassengerInput passenger = passengers.get(index);
            jdbc.update(
                    """
                    INSERT INTO reservation_passengers(
                        reservation_passenger_id, reservation_request_id, passenger_number,
                        display_name, preferred_berth_type
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(), requestId, index + 1, passenger.displayName(),
                    passenger.preferredBerthType());
        }
        for (UUID holdId : distinctHolds) {
            jdbc.update(
                    """
                    INSERT INTO reservation_request_holds(reservation_request_id, inventory_hold_id)
                    VALUES (?, ?)
                    """,
                    requestId, holdId);
        }
        stateHistory(requestId, null, REQUESTED, "REQUEST_CREATED", owner);
        audit(requestId, owner, "REQUEST_CREATED", Map.of(
                "passengerCount", passengers.size(),
                "groupSeatingRequested", request.groupSeatingRequested(),
                "journeySegmentCount", journey.toIndex() - journey.fromIndex()));
        return details(requestId, owner);
    }

    @Transactional
    public ReservationDetails allocate(UUID requestId, UUID owner) {
        UUID runId = ownedRunId(requestId, owner);
        advisoryLock("inventory-run-claims:" + runId);
        RequestRow request = lockRequest(requestId, owner);
        if (PENDING_BOOKING.equals(request.status()) || CONFIRMED.equals(request.status())) {
            return details(requestId, owner);
        }
        if (!REQUESTED.equals(request.status())) {
            throw failure(HttpStatus.CONFLICT, "RESERVATION_NOT_ALLOCATABLE",
                    "This reservation request is no longer eligible for allocation");
        }

        List<RequestPassenger> passengers = requestPassengers(requestId);
        List<HoldRow> holds = attachedHolds(requestId, true);
        String runStatus = jdbc.query(
                "SELECT status FROM inventory_train_runs WHERE train_run_id = ?",
                result -> result.next() ? result.getString(1) : null,
                runId);
        if (!"READY".equals(runStatus)) {
            return failAndRelease(request, holds, "TRAIN_RUN_UNAVAILABLE",
                    "The selected train run is no longer available");
        }
        if (passengers.isEmpty() || passengers.size() != holds.size()) {
            return failAndRelease(request, holds, "HOLD_COUNT_MISMATCH",
                    "The request does not have exactly one active hold for every passenger");
        }

        for (HoldRow hold : holds) {
            if (!hold.active() || !hold.trainRunId().equals(runId)
                    || hold.fromIndex() != request.fromIndex()
                    || hold.toIndex() != request.toIndex()
                    || !hold.classCode().equals(request.classCode())
                    || !"AVAILABLE".equals(hold.baseStatus())
                    || hold.segmentCount() != request.toIndex() - request.fromIndex()) {
                return failAndRelease(request, holds, "INVENTORY_HOLD_INVALID",
                        "One or more inventory holds are no longer active for this journey");
            }
        }

        ReservationAllocationPolicy.Weights policy = publishedPolicy();
        List<ReservationAllocationPolicy.Person> policyPassengers = passengers.stream()
                .map(passenger -> new ReservationAllocationPolicy.Person(
                        passenger.passengerId(), passenger.preference()))
                .toList();
        List<ReservationAllocationPolicy.Candidate> candidates = holds.stream()
                .map(hold -> new ReservationAllocationPolicy.Candidate(
                        hold.holdId(), hold.berthId(), hold.coachNumber(), hold.displayOrder(), hold.berthType()))
                .toList();
        ReservationAllocationPolicy.Plan plan = ReservationAllocationPolicy.assign(
                policyPassengers, candidates, request.groupSeatingRequested(), policy);
        Map<UUID, HoldRow> holdsById = new LinkedHashMap<>();
        holds.forEach(hold -> holdsById.put(hold.holdId(), hold));
        Map<UUID, RequestPassenger> passengersById = new LinkedHashMap<>();
        passengers.forEach(passenger -> passengersById.put(passenger.passengerId(), passenger));

        List<AssignmentRow> assignments = new ArrayList<>();
        Instant expiry = null;
        for (ReservationAllocationPolicy.Assignment assignment : plan.assignments()) {
            UUID passengerId = assignment.passenger().passengerId();
            UUID holdId = assignment.candidate().holdId();
            HoldRow hold = holdsById.get(holdId);
            assignments.add(new AssignmentRow(
                    passengersById.get(passengerId), hold,
                    assignment.preferenceResult(), assignment.score()));
            if (expiry == null || hold.expiresAt().isBefore(expiry)) {
                expiry = hold.expiresAt();
            }
        }

        try {
            for (AssignmentRow assignment : assignments) {
                createAllocation(request, assignment, policy.version());
            }
        } catch (DataIntegrityViolationException exception) {
            meterRegistry.counter("railconnect.reservation.allocations", "result", "conflict").increment();
            throw failure(HttpStatus.CONFLICT, "ALLOCATION_CONFLICT",
                    "The requested berth inventory changed; retry with new active holds");
        }

        jdbc.update(
                """
                UPDATE reservation_requests
                SET status = ?, group_seating_result = ?, expires_at = ?,
                    allocation_policy_version = ?, updated_at = CURRENT_TIMESTAMP
                WHERE reservation_request_id = ? AND status = 'REQUESTED'
                """,
                PENDING_BOOKING, plan.groupSeatingResult(), Timestamp.from(expiry), policy.version(), requestId);
        stateHistory(requestId, REQUESTED, PENDING_BOOKING, "ALLOCATION_CREATED", owner);
        audit(requestId, owner, "ALLOCATION_CREATED", Map.of(
                "passengerCount", assignments.size(),
                "groupSeatingResult", plan.groupSeatingResult(),
                "allocationPolicyVersion", policy.version(),
                "allocationScore", plan.totalScore(),
                "preferenceAlternatives", assignments.stream()
                        .filter(assignment -> "ALTERNATIVE".equals(assignment.preferenceResult())).count()));
        meterRegistry.counter("railconnect.reservation.allocations", "result", "success").increment();
        return details(requestId, owner);
    }

    @Transactional(readOnly = true)
    public ReservationDetails readOwn(UUID requestId, UUID owner) {
        return details(requestId, owner);
    }

    @Transactional(readOnly = true)
    public List<PassengerAllocation> readOwnAllocations(UUID requestId, UUID owner) {
        return details(requestId, owner).passengers();
    }

    @Transactional
    public ReservationDetails releaseOwn(UUID requestId, UUID owner, String reasonCode) {
        UUID runId = ownedRunId(requestId, owner);
        advisoryLock("inventory-run-claims:" + runId);
        RequestRow request = lockRequest(requestId, owner);
        if (RELEASED.equals(request.status())) {
            return details(requestId, owner);
        }
        if (request.bookingCorrelationId() != null) {
            throw failure(HttpStatus.CONFLICT, "ALLOCATION_ATTACHED_TO_BOOKING",
                    "This provisional allocation can only be released through its booking workflow");
        }
        if (!REQUESTED.equals(request.status()) && !PENDING_BOOKING.equals(request.status())) {
            throw failure(HttpStatus.CONFLICT, "RESERVATION_NOT_RELEASABLE",
                    "Only unconfirmed reservation allocations can be released");
        }
        releaseRequest(request, owner, reasonCode == null ? "PASSENGER_RELEASED" : reasonCode);
        return details(requestId, owner);
    }

    @Transactional(readOnly = true)
    public Page<ReservationSummary> adminReservations(String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = boundedPageSize(size);
        String normalizedStatus = normalizeStatusFilter(status);
        String predicate = normalizedStatus == null ? "" : " AND request.status = ? ";
        List<Object> parameters = new ArrayList<>();
        if (normalizedStatus != null) {
            parameters.add(normalizedStatus);
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservation_requests request WHERE TRUE" + predicate,
                Long.class, parameters.toArray());
        parameters.add(safeSize);
        parameters.add((long) safePage * safeSize);
        List<ReservationSummary> items = jdbc.query(
                """
                SELECT request.reservation_request_id, request.train_run_id, request.journey_date,
                       request.coach_class_code, request.status, request.group_seating_requested,
                       request.expires_at, request.created_at, COUNT(passenger.reservation_passenger_id) AS passenger_count
                FROM reservation_requests request
                LEFT JOIN reservation_passengers passenger
                  ON passenger.reservation_request_id = request.reservation_request_id
                WHERE TRUE
                """ + predicate + """
                GROUP BY request.reservation_request_id
                ORDER BY request.created_at DESC, request.reservation_request_id
                LIMIT ? OFFSET ?
                """,
                (result, row) -> summary(result),
                parameters.toArray());
        return new Page<>(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ReservationDetails adminRead(UUID requestId) {
        return details(requestId, null);
    }

    @Transactional(readOnly = true)
    public List<AllocationPolicyVersion> allocationPolicyVersions() {
        return jdbc.query(
                """
                SELECT policy.version_number, policy.status, policy.preferred_berth_weight,
                       policy.same_coach_weight, policy.nearby_berth_weight,
                       creator.full_name AS created_by_name, approver.full_name AS approved_by_name,
                       policy.created_at, policy.approved_at, policy.published_at
                FROM reservation_allocation_policies policy
                LEFT JOIN users creator ON creator.user_id = policy.created_by
                LEFT JOIN users approver ON approver.user_id = policy.approved_by
                ORDER BY policy.version_number DESC
                """,
                (result, row) -> policyVersion(result));
    }

    @Transactional
    public AllocationPolicyVersion createAllocationPolicyDraft(AllocationPolicyRequest request, UUID actor) {
        advisoryLock("reservation-allocation-policy");
        Integer nextVersion = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) + 1 FROM reservation_allocation_policies",
                Integer.class);
        if (nextVersion == null || nextVersion > 32767) {
            throw failure(HttpStatus.CONFLICT, "ALLOCATION_POLICY_VERSION_LIMIT",
                    "No further allocation policy versions can be created");
        }
        jdbc.update(
                """
                INSERT INTO reservation_allocation_policies(
                    version_number, status, preferred_berth_weight, same_coach_weight,
                    nearby_berth_weight, created_by
                ) VALUES (?, 'DRAFT', ?, ?, ?, ?)
                """,
                nextVersion, request.preferredBerthWeight(), request.sameCoachWeight(),
                request.nearbyBerthWeight(), actor);
        policyAudit(nextVersion, actor, "POLICY_DRAFT_CREATED", Map.of(
                "preferredBerthWeight", request.preferredBerthWeight(),
                "sameCoachWeight", request.sameCoachWeight(),
                "nearbyBerthWeight", request.nearbyBerthWeight()));
        return allocationPolicyVersion(nextVersion);
    }

    @Transactional
    public AllocationPolicyVersion approveAllocationPolicy(int version, UUID actor) {
        advisoryLock("reservation-allocation-policy");
        AllocationPolicyVersion policy = allocationPolicyVersion(version);
        if (!"DRAFT".equals(policy.status())) {
            throw failure(HttpStatus.CONFLICT, "ALLOCATION_POLICY_NOT_APPROVABLE",
                    "Only a draft allocation policy can be approved");
        }
        UUID authorId = jdbc.queryForObject(
                "SELECT created_by FROM reservation_allocation_policies WHERE version_number = ?",
                UUID.class, version);
        if (actor.equals(authorId)) {
            throw failure(HttpStatus.FORBIDDEN, "ALLOCATION_POLICY_SELF_APPROVAL",
                    "The policy author cannot approve their own allocation policy");
        }
        jdbc.update(
                """
                UPDATE reservation_allocation_policies
                SET status = 'APPROVED', approved_by = ?, approved_at = CURRENT_TIMESTAMP
                WHERE version_number = ? AND status = 'DRAFT'
                """,
                actor, version);
        policyAudit(version, actor, "POLICY_APPROVED", Map.of());
        return allocationPolicyVersion(version);
    }

    @Transactional
    public AllocationPolicyVersion publishAllocationPolicy(int version, UUID actor) {
        advisoryLock("reservation-allocation-policy");
        AllocationPolicyVersion policy = allocationPolicyVersion(version);
        if (!"APPROVED".equals(policy.status())) {
            throw failure(HttpStatus.CONFLICT, "ALLOCATION_POLICY_NOT_PUBLISHABLE",
                    "Only an approved allocation policy can be published");
        }
        jdbc.update(
                """
                UPDATE reservation_allocation_policies
                SET status = 'RETIRED'
                WHERE status = 'PUBLISHED'
                """);
        jdbc.update(
                """
                UPDATE reservation_allocation_policies
                SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP
                WHERE version_number = ? AND status = 'APPROVED'
                """,
                version);
        policyAudit(version, actor, "POLICY_PUBLISHED", Map.of());
        return allocationPolicyVersion(version);
    }

    @Transactional(readOnly = true)
    public Page<AllocationPolicyAuditEvent> allocationPolicyAudit(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = boundedPageSize(size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM reservation_policy_audit_log", Long.class);
        List<AllocationPolicyAuditEvent> items = jdbc.query(
                """
                SELECT audit.reservation_policy_audit_id, audit.version_number,
                       actor.full_name AS actor_name,
                       event_type, details::text, occurred_at, correlation_id
                FROM reservation_policy_audit_log audit
                LEFT JOIN users actor ON actor.user_id = audit.actor_user_id
                ORDER BY audit.occurred_at DESC, audit.reservation_policy_audit_id
                LIMIT ? OFFSET ?
                """,
                (result, row) -> new AllocationPolicyAuditEvent(
                        result.getObject("reservation_policy_audit_id", UUID.class),
                        result.getInt("version_number"),
                        result.getString("actor_name"),
                        result.getString("event_type"),
                        result.getString("details"),
                        instant(result, "occurred_at"),
                        result.getString("correlation_id")),
                safeSize, (long) safePage * safeSize);
        return new Page<>(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public List<AllocationCounts> allocationSummary() {
        return jdbc.query(
                """
                SELECT status, COUNT(*) AS allocation_count
                FROM reservation_allocations
                GROUP BY status
                ORDER BY status
                """,
                (result, row) -> new AllocationCounts(result.getString("status"), result.getLong("allocation_count")));
    }

    @Transactional(readOnly = true)
    public Page<ReservationSummary> allocationFailures(int page, int size) {
        return adminReservations(FAILED, page, size);
    }

    @Transactional(readOnly = true)
    public Page<AllocationAuditEvent> auditEvents(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = boundedPageSize(size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM reservation_audit_log", Long.class);
        List<AllocationAuditEvent> items = jdbc.query(
                """
                SELECT audit.reservation_audit_id, audit.reservation_request_id,
                       actor.full_name AS actor_name,
                       event_type, details::text, occurred_at, correlation_id
                FROM reservation_audit_log audit
                LEFT JOIN users actor ON actor.user_id = audit.actor_user_id
                ORDER BY audit.occurred_at DESC, audit.reservation_audit_id
                LIMIT ? OFFSET ?
                """,
                (result, row) -> new AllocationAuditEvent(
                        result.getObject("reservation_audit_id", UUID.class),
                        result.getObject("reservation_request_id", UUID.class),
                        result.getString("actor_name"),
                        result.getString("event_type"), result.getString("details"),
                        result.getTimestamp("occurred_at").toInstant(), result.getString("correlation_id")),
                safeSize, (long) safePage * safeSize);
        return new Page<>(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public List<AllocationCounts> operationsSummary(UUID trainRunId) {
        return jdbc.query(
                """
                SELECT allocation.status, COUNT(*) AS allocation_count
                FROM reservation_allocations allocation
                WHERE allocation.train_run_id = ?
                GROUP BY allocation.status
                ORDER BY allocation.status
                """,
                (result, row) -> new AllocationCounts(result.getString("status"), result.getLong("allocation_count")),
                trainRunId);
    }

    @Transactional
    public ReservationDetails adminRelease(UUID requestId, UUID actor, String reasonCode) {
        UUID runId = runId(requestId);
        advisoryLock("inventory-run-claims:" + runId);
        RequestRow request = lockRequest(requestId, null);
        if (RELEASED.equals(request.status())) {
            return details(requestId, null);
        }
        if (request.bookingCorrelationId() != null) {
            throw failure(HttpStatus.CONFLICT, "ALLOCATION_ATTACHED_TO_BOOKING",
                    "This provisional allocation can only be released through its booking workflow");
        }
        if (!REQUESTED.equals(request.status()) && !PENDING_BOOKING.equals(request.status())) {
            throw failure(HttpStatus.CONFLICT, "RESERVATION_NOT_RELEASABLE",
                    "Only unconfirmed reservation allocations can be released");
        }
        releaseRequest(request, actor, reasonCode);
        return details(requestId, null);
    }

    @Transactional
    public ReservationDetails confirmAfterBooking(UUID requestId, UUID bookingCorrelationId) {
        Objects.requireNonNull(bookingCorrelationId, "bookingCorrelationId");
        UUID runId = runId(requestId);
        advisoryLock("inventory-run-claims:" + runId);
        RequestRow request = lockRequest(requestId, null);
        if (CONFIRMED.equals(request.status())
                && bookingCorrelationId.equals(request.bookingCorrelationId())) {
            return details(requestId, null);
        }
        if (!PENDING_BOOKING.equals(request.status())) {
            throw failure(HttpStatus.CONFLICT, "ALLOCATION_NOT_CONFIRMABLE",
                    "The provisional allocation is not eligible for booking confirmation");
        }
        if (request.bookingCorrelationId() != null
                && !bookingCorrelationId.equals(request.bookingCorrelationId())) {
            throw failure(HttpStatus.CONFLICT, "BOOKING_CORRELATION_MISMATCH",
                    "The booking reference does not match this allocation");
        }
        int confirmed = jdbc.update(
                """
                UPDATE reservation_requests
                SET status = 'CONFIRMED', booking_correlation_id = COALESCE(booking_correlation_id, ?),
                    updated_at = CURRENT_TIMESTAMP
                WHERE reservation_request_id = ? AND status = 'ALLOCATED_PENDING_BOOKING'
                  AND expires_at > CURRENT_TIMESTAMP
                """,
                bookingCorrelationId, requestId);
        if (confirmed != 1) {
            throw failure(HttpStatus.CONFLICT, "ALLOCATION_NOT_CONFIRMABLE",
                    "The provisional allocation is no longer eligible for booking confirmation");
        }
        jdbc.update(
                """
                UPDATE reservation_allocations
                SET status = 'CONFIRMED', updated_at = CURRENT_TIMESTAMP
                WHERE reservation_request_id = ? AND status = 'ALLOCATED_PENDING_BOOKING'
                """,
                requestId);
        stateHistory(requestId, PENDING_BOOKING, CONFIRMED, "BOOKING_CONFIRMED", null);
        audit(requestId, null, "ALLOCATION_CONFIRMED",
                Map.of("bookingCorrelationId", bookingCorrelationId));
        return details(requestId, null);
    }

    @Transactional
    public ReservationDetails attachToBooking(UUID requestId, UUID owner, UUID bookingId) {
        UUID runId = ownedRunId(requestId, owner);
        advisoryLock("inventory-run-claims:" + runId);
        RequestRow request = lockRequest(requestId, owner);
        if (!PENDING_BOOKING.equals(request.status())) {
            throw failure(HttpStatus.CONFLICT, "ALLOCATION_NOT_BOOKABLE",
                    "A live provisional allocation is required to create a booking");
        }
        if (request.bookingCorrelationId() != null) {
            if (bookingId.equals(request.bookingCorrelationId())) {
                return details(requestId, owner);
            }
            throw failure(HttpStatus.CONFLICT, "ALLOCATION_ALREADY_BOOKED",
                    "The provisional allocation is already attached to another booking");
        }
        int attached = jdbc.update(
                """
                UPDATE reservation_requests
                SET booking_correlation_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE reservation_request_id = ?
                  AND status = 'ALLOCATED_PENDING_BOOKING'
                  AND booking_correlation_id IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
                """,
                bookingId, requestId);
        if (attached != 1) {
            throw failure(HttpStatus.CONFLICT, "ALLOCATION_NOT_BOOKABLE",
                    "The provisional allocation expired before booking creation");
        }
        audit(requestId, owner, "BOOKING_ATTACHED", Map.of("bookingId", bookingId));
        return details(requestId, owner);
    }

    @Transactional
    public ReservationDetails releaseForBookingFailure(
            UUID requestId,
            UUID bookingCorrelationId,
            String reasonCode
    ) {
        UUID runId = runId(requestId);
        advisoryLock("inventory-run-claims:" + runId);
        RequestRow request = lockRequest(requestId, null);
        if (bookingCorrelationId != null && request.bookingCorrelationId() != null
                && !bookingCorrelationId.equals(request.bookingCorrelationId())) {
            throw failure(HttpStatus.CONFLICT, "BOOKING_CORRELATION_MISMATCH",
                    "The booking reference does not match this allocation");
        }
        if (RELEASED.equals(request.status()) || EXPIRED.equals(request.status())) {
            return details(requestId, null);
        }
        if (!PENDING_BOOKING.equals(request.status())) {
            throw failure(HttpStatus.CONFLICT, "RESERVATION_NOT_RELEASABLE",
                    "Only provisional allocations can be released by the booking service");
        }
        releaseRequest(request, null, reasonCode);
        return details(requestId, null);
    }

    @Transactional(readOnly = true)
    public ReservationDetails readForBooking(UUID requestId) {
        return details(requestId, null);
    }

    @Scheduled(fixedDelayString = "${railconnect.reservations.expiration-sweep-ms:30000}")
    @Transactional
    public void expireProvisionalAllocations() {
        List<UUID> runs = jdbc.query(
                """
                SELECT DISTINCT train_run_id
                FROM reservation_requests
                WHERE status = 'ALLOCATED_PENDING_BOOKING' AND expires_at <= CURRENT_TIMESTAMP
                ORDER BY train_run_id
                LIMIT 20
                """,
                (result, row) -> result.getObject(1, UUID.class));
        for (UUID runId : runs) {
            advisoryLock("inventory-run-claims:" + runId);
            List<UUID> expired = jdbc.query(
                    """
                    SELECT reservation_request_id
                    FROM reservation_requests
                    WHERE train_run_id = ? AND status = 'ALLOCATED_PENDING_BOOKING'
                      AND expires_at <= CURRENT_TIMESTAMP
                    ORDER BY expires_at, reservation_request_id
                    LIMIT 50
                    FOR UPDATE SKIP LOCKED
                    """,
                    (result, row) -> result.getObject(1, UUID.class), runId);
            for (UUID requestId : expired) {
                expireRequest(lockRequest(requestId, null));
            }
        }
    }

    private void createAllocation(RequestRow request, AssignmentRow assignment, int policyVersion) {
        RequestPassenger passenger = assignment.passenger();
        HoldRow hold = assignment.hold();
        UUID allocationId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO reservation_allocations(
                    reservation_allocation_id, reservation_request_id, reservation_passenger_id,
                    train_run_id, inventory_berth_id, inventory_hold_id, from_stop_index,
                    to_stop_index, coach_class_code, preferred_berth_type, preference_result,
                    allocation_policy_version, allocation_score
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                allocationId, request.requestId(), passenger.passengerId(), request.runId(), hold.berthId(),
                hold.holdId(), request.fromIndex(), request.toIndex(), request.classCode(),
                passenger.preference(), assignment.preferenceResult(), policyVersion, assignment.score());

        int segmentsInserted = jdbc.update(
                """
                INSERT INTO reservation_allocation_segments(
                    reservation_allocation_id, train_run_id, inventory_berth_id, segment_index
                )
                SELECT ?, train_run_id, inventory_berth_id, segment_index
                FROM inventory_hold_segments
                WHERE inventory_hold_id = ? AND train_run_id = ?
                  AND inventory_berth_id = ?
                """,
                allocationId, hold.holdId(), request.runId(), hold.berthId());
        int expectedSegments = request.toIndex() - request.fromIndex();
        if (segmentsInserted != expectedSegments) {
            throw failure(HttpStatus.CONFLICT, "INVENTORY_HOLD_SEGMENTS_INVALID",
                    "The inventory hold does not cover the complete requested journey");
        }

        int claimsTransferred = jdbc.update(
                """
                UPDATE inventory_segment_claims
                SET inventory_hold_id = NULL, reservation_allocation_id = ?
                WHERE inventory_hold_id = ? AND train_run_id = ? AND inventory_berth_id = ?
                """,
                allocationId, hold.holdId(), request.runId(), hold.berthId());
        if (claimsTransferred != expectedSegments) {
            throw failure(HttpStatus.CONFLICT, "INVENTORY_CLAIM_TRANSFER_FAILED",
                    "The inventory hold could not be transferred atomically");
        }
        int holdConsumed = jdbc.update(
                """
                UPDATE inventory_holds
                SET status = 'CONSUMED', updated_at = CURRENT_TIMESTAMP
                WHERE inventory_hold_id = ? AND status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP
                """,
                hold.holdId());
        if (holdConsumed != 1) {
            throw failure(HttpStatus.CONFLICT, "INVENTORY_HOLD_EXPIRED",
                    "An inventory hold expired before allocation completed");
        }
        inventoryAudit(request.runId(), hold.berthId(), request.ownerId(), "HOLD_CONSUMED",
                Map.of("holdId", hold.holdId(), "allocationId", allocationId));
    }

    private ReservationDetails failAndRelease(
            RequestRow request,
            List<HoldRow> holds,
            String failureCode,
            String failureReason
    ) {
        for (HoldRow hold : holds) {
            jdbc.update("DELETE FROM inventory_segment_claims WHERE inventory_hold_id = ?", hold.holdId());
            int released = jdbc.update(
                    """
                    UPDATE inventory_holds
                    SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    WHERE inventory_hold_id = ? AND status = 'ACTIVE'
                    """,
                    hold.holdId());
            if (released == 1) {
                inventoryAudit(request.runId(), hold.berthId(), request.ownerId(), "HOLD_RELEASED",
                        Map.of("holdId", hold.holdId(), "reason", failureCode));
            }
        }
        jdbc.update(
                """
                UPDATE reservation_requests
                SET status = 'FAILED', failure_code = ?, failure_reason = ?, updated_at = CURRENT_TIMESTAMP
                WHERE reservation_request_id = ? AND status = 'REQUESTED'
                """,
                failureCode, failureReason, request.requestId());
        stateHistory(request.requestId(), REQUESTED, FAILED, failureCode, request.ownerId());
        audit(request.requestId(), request.ownerId(), "ALLOCATION_FAILED",
                Map.of("failureCode", failureCode));
        meterRegistry.counter("railconnect.reservation.allocations", "result", "failed").increment();
        return details(request.requestId(), request.ownerId());
    }

    private void releaseRequest(RequestRow request, UUID actor, String reasonCode) {
        if (REQUESTED.equals(request.status())) {
            List<HoldRow> holds = attachedHolds(request.requestId(), true);
            for (HoldRow hold : holds) {
                jdbc.update("DELETE FROM inventory_segment_claims WHERE inventory_hold_id = ?", hold.holdId());
                int released = jdbc.update(
                        """
                        UPDATE inventory_holds
                        SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE inventory_hold_id = ? AND status = 'ACTIVE'
                        """,
                        hold.holdId());
                if (released == 1) {
                    inventoryAudit(request.runId(), hold.berthId(), actor, "HOLD_RELEASED",
                            Map.of("holdId", hold.holdId(), "reason", reasonCode));
                }
            }
        } else {
            List<UUID> allocationIds = jdbc.query(
                    """
                    SELECT reservation_allocation_id
                    FROM reservation_allocations
                    WHERE reservation_request_id = ? AND status = 'ALLOCATED_PENDING_BOOKING'
                    ORDER BY reservation_allocation_id
                    FOR UPDATE
                    """,
                    (result, row) -> result.getObject(1, UUID.class), request.requestId());
            for (UUID allocationId : allocationIds) {
                jdbc.update("DELETE FROM inventory_segment_claims WHERE reservation_allocation_id = ?", allocationId);
                jdbc.update(
                        """
                        UPDATE reservation_allocations
                        SET status = 'RELEASED', updated_at = CURRENT_TIMESTAMP
                        WHERE reservation_allocation_id = ? AND status = 'ALLOCATED_PENDING_BOOKING'
                        """,
                        allocationId);
            }
        }
        jdbc.update(
                """
                UPDATE reservation_requests
                SET status = 'RELEASED', updated_at = CURRENT_TIMESTAMP
                WHERE reservation_request_id = ? AND status IN ('REQUESTED', 'ALLOCATED_PENDING_BOOKING')
                """,
                request.requestId());
        stateHistory(request.requestId(), request.status(), RELEASED, reasonCode, actor);
        audit(request.requestId(), actor, "ALLOCATION_RELEASED", Map.of("reasonCode", reasonCode));
    }

    private void expireRequest(RequestRow request) {
        List<UUID> allocationIds = jdbc.query(
                """
                SELECT reservation_allocation_id
                FROM reservation_allocations
                WHERE reservation_request_id = ? AND status = 'ALLOCATED_PENDING_BOOKING'
                ORDER BY reservation_allocation_id
                FOR UPDATE
                """,
                (result, row) -> result.getObject(1, UUID.class), request.requestId());
        for (UUID allocationId : allocationIds) {
            jdbc.update("DELETE FROM inventory_segment_claims WHERE reservation_allocation_id = ?", allocationId);
            jdbc.update(
                    """
                    UPDATE reservation_allocations
                    SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                    WHERE reservation_allocation_id = ? AND status = 'ALLOCATED_PENDING_BOOKING'
                    """,
                    allocationId);
        }
        int updated = jdbc.update(
                """
                UPDATE reservation_requests
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE reservation_request_id = ? AND status = 'ALLOCATED_PENDING_BOOKING'
                  AND expires_at <= CURRENT_TIMESTAMP
                """,
                request.requestId());
        if (updated == 1) {
            stateHistory(request.requestId(), PENDING_BOOKING, EXPIRED, "ALLOCATION_EXPIRED", null);
            audit(request.requestId(), null, "ALLOCATION_EXPIRED", Map.of("reasonCode", "BOOKING_WINDOW_EXPIRED"));
        }
    }

    private List<HoldRow> lockAndValidateHolds(
            List<UUID> holdIds,
            UUID owner,
            UUID runId,
            Journey journey,
            String classCode
    ) {
        List<HoldRow> holds = new ArrayList<>();
        for (UUID holdId : holdIds) {
            List<HoldRow> rows = jdbc.query(
                    """
                    SELECT hold.inventory_hold_id, hold.train_run_id, hold.inventory_berth_id,
                           hold.owner_user_id, hold.from_stop_index, hold.to_stop_index, hold.status,
                           hold.expires_at,
                           (hold.status = 'ACTIVE' AND hold.expires_at > CURRENT_TIMESTAMP) AS is_active,
                           berth.base_status, berth.berth_type, berth.display_order, coach.coach_number,
                           coach.coach_class_code,
                           (SELECT COUNT(*) FROM inventory_segment_claims claim
                            WHERE claim.inventory_hold_id = hold.inventory_hold_id) AS claim_count
                    FROM inventory_holds hold
                    JOIN inventory_berths berth ON berth.inventory_berth_id = hold.inventory_berth_id
                    JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                    WHERE hold.inventory_hold_id = ?
                    FOR UPDATE OF hold
                    """,
                    (result, row) -> holdRow(result), holdId);
            if (rows.isEmpty()) {
                throw failure(HttpStatus.CONFLICT, "INVENTORY_HOLD_NOT_FOUND",
                        "One or more inventory holds are unavailable");
            }
            HoldRow hold = rows.get(0);
            if (!owner.equals(hold.ownerId()) || !runId.equals(hold.trainRunId())
                    || hold.fromIndex() != journey.fromIndex() || hold.toIndex() != journey.toIndex()
                    || !classCode.equals(hold.classCode())) {
                throw failure(HttpStatus.CONFLICT, "INVENTORY_HOLD_MISMATCH",
                        "Every inventory hold must belong to this user, train run, class, and journey");
            }
            if (!hold.active() || !"AVAILABLE".equals(hold.baseStatus())) {
                throw failure(HttpStatus.CONFLICT, "INVENTORY_HOLD_EXPIRED",
                        "One or more inventory holds are no longer active");
            }
            if (hold.segmentCount() != journey.toIndex() - journey.fromIndex()) {
                throw failure(HttpStatus.CONFLICT, "INVENTORY_HOLD_SEGMENTS_INVALID",
                        "An inventory hold does not reserve every segment of the selected journey");
            }
            holds.add(hold);
        }
        return holds;
    }

    private Journey validateJourney(UUID runId, LocalDate requestedDate, UUID fromStationId,
                                    UUID toStationId, String classCode) {
        List<RunRow> runs = jdbc.query(
                "SELECT service_date, status FROM inventory_train_runs WHERE train_run_id = ?",
                (result, row) -> new RunRow(result.getObject("service_date", LocalDate.class),
                        result.getString("status")),
                runId);
        if (runs.isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "TRAIN_RUN_NOT_FOUND", "Train run not found");
        }
        RunRow run = runs.get(0);
        if (!"READY".equals(run.status()) || !requestedDate.equals(run.serviceDate())) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "TRAIN_RUN_DATE_INVALID",
                    "The train run is not ready for the requested journey date");
        }
        StopRow from = stop(runId, fromStationId);
        StopRow to = stop(runId, toStationId);
        if (from.index() >= to.index()) {
            throw failure(HttpStatus.BAD_REQUEST, "JOURNEY_RANGE_INVALID",
                    "Journey origin must precede its destination on the train-run route");
        }
        Integer classCount = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM inventory_coaches
                WHERE train_run_id = ? AND coach_class_code = ?
                """,
                Integer.class, runId, classCode);
        if (classCount == null || classCount == 0) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "COACH_CLASS_UNAVAILABLE",
                    "The requested coach class is not present on this train run");
        }
        return new Journey(from.index(), to.index());
    }

    private StopRow stop(UUID runId, UUID stationId) {
        List<StopRow> rows = jdbc.query(
                """
                SELECT stop_index, station_name
                FROM inventory_run_stops
                WHERE train_run_id = ? AND station_id = ?
                """,
                (result, row) -> new StopRow(result.getInt("stop_index"), result.getString("station_name")),
                runId, stationId);
        if (rows.isEmpty()) {
            throw failure(HttpStatus.BAD_REQUEST, "JOURNEY_STATION_NOT_ON_ROUTE",
                    "The requested origin or destination is not on this train-run route");
        }
        return rows.get(0);
    }

    private ReservationDetails details(UUID requestId, UUID owner) {
        String ownerFilter = owner == null ? "" : " AND request.owner_user_id = ?";
        List<Object> parameters = new ArrayList<>();
        parameters.add(requestId);
        if (owner != null) {
            parameters.add(owner);
        }
        List<ReservationDetails> requests = jdbc.query(
                """
                SELECT request.reservation_request_id, request.train_run_id, request.journey_date,
                       request.coach_class_code, request.group_seating_requested,
                       request.group_seating_result, request.status, request.allocation_policy_version,
                       (SELECT SUM(allocation.allocation_score)
                        FROM reservation_allocations allocation
                        WHERE allocation.reservation_request_id = request.reservation_request_id) AS allocation_score,
                       request.expires_at,
                       request.failure_code, request.failure_reason, request.created_at,
                       request.updated_at, from_stop.station_id AS from_station_id,
                       from_stop.station_name AS from_station_name, to_stop.station_id AS to_station_id,
                       to_stop.station_name AS to_station_name
                FROM reservation_requests request
                JOIN inventory_run_stops from_stop
                  ON from_stop.train_run_id = request.train_run_id
                 AND from_stop.stop_index = request.from_stop_index
                JOIN inventory_run_stops to_stop
                  ON to_stop.train_run_id = request.train_run_id
                 AND to_stop.stop_index = request.to_stop_index
                WHERE request.reservation_request_id = ?
                """ + ownerFilter,
                (result, row) -> new ReservationDetails(
                        result.getObject("reservation_request_id", UUID.class),
                        result.getObject("train_run_id", UUID.class),
                        result.getObject("journey_date", LocalDate.class),
                        result.getObject("from_station_id", UUID.class),
                        result.getString("from_station_name"),
                        result.getObject("to_station_id", UUID.class),
                        result.getString("to_station_name"),
                        result.getString("coach_class_code"),
                        result.getBoolean("group_seating_requested"),
                        result.getString("group_seating_result"),
                        result.getString("status"),
                        integer(result, "allocation_score"),
                        integer(result, "allocation_policy_version"),
                        PENDING_BOOKING.equals(result.getString("status")),
                        instant(result, "expires_at"),
                        result.getString("failure_code"),
                        result.getString("failure_reason"),
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("updated_at").toInstant(),
                        List.of(),
                        List.of()),
                parameters.toArray());
        if (requests.isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation request not found");
        }
        ReservationDetails request = requests.get(0);
        List<PassengerAllocation> passengers = jdbc.query(
                """
                SELECT passenger.reservation_passenger_id, passenger.passenger_number,
                       passenger.display_name, passenger.preferred_berth_type,
                       allocation.inventory_hold_id, allocation.reservation_allocation_id,
                       allocation.inventory_berth_id, coach.coach_number, berth.berth_number,
                       coach.coach_class_code, COALESCE(allocation.status, request.status) AS allocation_status,
                       allocation.preference_result, allocation.allocation_score
                FROM reservation_passengers passenger
                JOIN reservation_requests request
                  ON request.reservation_request_id = passenger.reservation_request_id
                LEFT JOIN reservation_allocations allocation
                  ON allocation.reservation_passenger_id = passenger.reservation_passenger_id
                LEFT JOIN inventory_berths berth ON berth.inventory_berth_id = allocation.inventory_berth_id
                LEFT JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                WHERE passenger.reservation_request_id = ?
                ORDER BY passenger.passenger_number
                """,
                (result, row) -> new PassengerAllocation(
                        result.getObject("reservation_passenger_id", UUID.class),
                        result.getInt("passenger_number"),
                        result.getString("display_name"),
                        result.getString("preferred_berth_type"),
                        result.getString("preference_result"),
                        result.getObject("inventory_hold_id", UUID.class),
                        result.getObject("reservation_allocation_id", UUID.class),
                        result.getObject("inventory_berth_id", UUID.class),
                        result.getString("coach_number"),
                        result.getString("berth_number"),
                        result.getString("coach_class_code"),
                        result.getString("allocation_status")),
                requestId);
        List<StateChange> history = jdbc.query(
                """
                SELECT previous_status, new_status, reason_code, occurred_at
                FROM reservation_state_history
                WHERE reservation_request_id = ?
                ORDER BY occurred_at, reservation_state_history_id
                """,
                (result, row) -> new StateChange(
                        result.getString("previous_status"), result.getString("new_status"),
                        result.getString("reason_code"), result.getTimestamp("occurred_at").toInstant()),
                requestId);
        return new ReservationDetails(
                request.reservationRequestId(), request.trainRunId(), request.journeyDate(),
                request.fromStationId(), request.fromStationName(), request.toStationId(),
                request.toStationName(), request.coachClassCode(), request.groupSeatingRequested(),
                request.groupSeatingResult(), request.status(), request.allocationScore(),
                request.allocationPolicyVersion(), request.provisional(), request.expiresAt(),
                request.failureCode(), request.failureReason(), request.createdAt(), request.updatedAt(),
                passengers, history);
    }

    private RequestRow lockRequest(UUID requestId, UUID owner) {
        String ownerFilter = owner == null ? "" : " AND owner_user_id = ?";
        List<Object> parameters = new ArrayList<>();
        parameters.add(requestId);
        if (owner != null) {
            parameters.add(owner);
        }
        List<RequestRow> rows = jdbc.query(
                """
                SELECT reservation_request_id, owner_user_id, train_run_id, from_stop_index,
                       to_stop_index, coach_class_code, group_seating_requested, status,
                       expires_at, allocation_policy_version, booking_correlation_id
                FROM reservation_requests
                WHERE reservation_request_id = ?
                """ + ownerFilter + " FOR UPDATE",
                (result, row) -> new RequestRow(
                        result.getObject("reservation_request_id", UUID.class),
                        result.getObject("owner_user_id", UUID.class),
                        result.getObject("train_run_id", UUID.class),
                        result.getInt("from_stop_index"), result.getInt("to_stop_index"),
                        result.getString("coach_class_code"), result.getBoolean("group_seating_requested"),
                        result.getString("status"), instant(result, "expires_at"),
                        integer(result, "allocation_policy_version"),
                        result.getObject("booking_correlation_id", UUID.class)),
                parameters.toArray());
        if (rows.isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation request not found");
        }
        return rows.get(0);
    }

    private List<RequestPassenger> requestPassengers(UUID requestId) {
        return jdbc.query(
                """
                SELECT reservation_passenger_id, display_name, preferred_berth_type
                FROM reservation_passengers
                WHERE reservation_request_id = ?
                ORDER BY passenger_number
                """,
                (result, row) -> new RequestPassenger(
                        result.getObject("reservation_passenger_id", UUID.class),
                        result.getString("display_name"),
                        result.getString("preferred_berth_type")),
                requestId);
    }

    private List<HoldRow> attachedHolds(UUID requestId, boolean lock) {
        String lockClause = lock ? " FOR UPDATE OF hold" : "";
        return jdbc.query(
                """
                SELECT hold.inventory_hold_id, hold.train_run_id,
                       hold.inventory_berth_id, hold.owner_user_id, hold.from_stop_index,
                       hold.to_stop_index, hold.status, hold.expires_at,
                       (hold.status = 'ACTIVE' AND hold.expires_at > CURRENT_TIMESTAMP) AS is_active,
                       berth.base_status, berth.berth_type, berth.display_order, coach.coach_number,
                       coach.coach_class_code,
                       (SELECT COUNT(*) FROM inventory_segment_claims claim
                        WHERE claim.inventory_hold_id = hold.inventory_hold_id) AS claim_count
                FROM reservation_request_holds request_hold
                JOIN inventory_holds hold ON hold.inventory_hold_id = request_hold.inventory_hold_id
                JOIN inventory_berths berth ON berth.inventory_berth_id = hold.inventory_berth_id
                JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                WHERE request_hold.reservation_request_id = ?
                ORDER BY hold.inventory_hold_id
                """ + lockClause,
                (result, row) -> holdRow(result),
                requestId);
    }

    private HoldRow holdRow(ResultSet result) throws SQLException {
        return new HoldRow(
                result.getObject("inventory_hold_id", UUID.class),
                result.getObject("train_run_id", UUID.class),
                result.getObject("inventory_berth_id", UUID.class),
                result.getObject("owner_user_id", UUID.class),
                result.getInt("from_stop_index"),
                result.getInt("to_stop_index"),
                result.getString("status"),
                result.getBoolean("is_active"),
                result.getString("base_status"),
                result.getString("berth_type"),
                result.getInt("display_order"),
                result.getString("coach_number"),
                result.getString("coach_class_code"),
                result.getTimestamp("expires_at").toInstant(),
                result.getInt("claim_count"));
    }

    private void enforceRequestRateLimit(UUID owner) {
        Integer count = jdbc.queryForObject(
                """
                INSERT INTO reservation_request_rate_limits(owner_user_id, window_started_at, request_count)
                VALUES (?, CURRENT_TIMESTAMP, 1)
                ON CONFLICT (owner_user_id) DO UPDATE SET
                    request_count = CASE
                        WHEN reservation_request_rate_limits.window_started_at
                             <= CURRENT_TIMESTAMP - INTERVAL '1 minute' THEN 1
                        ELSE reservation_request_rate_limits.request_count + 1
                    END,
                    window_started_at = CASE
                        WHEN reservation_request_rate_limits.window_started_at
                             <= CURRENT_TIMESTAMP - INTERVAL '1 minute' THEN CURRENT_TIMESTAMP
                        ELSE reservation_request_rate_limits.window_started_at
                    END
                RETURNING request_count
                """,
                Integer.class, owner);
        if (count != null && count > maximumRequestsPerMinute) {
            throw failure(HttpStatus.TOO_MANY_REQUESTS, "RESERVATION_RATE_LIMITED",
                    "Too many reservation requests; wait before trying again");
        }
    }

    private ReservationIdentity findByIdempotency(UUID owner, String key) {
        List<ReservationIdentity> rows = jdbc.query(
                """
                SELECT reservation_request_id, request_fingerprint
                FROM reservation_requests
                WHERE owner_user_id = ? AND idempotency_key = ?
                """,
                (result, row) -> new ReservationIdentity(
                        result.getObject("reservation_request_id", UUID.class),
                        result.getString("request_fingerprint")),
                owner, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private UUID ownedRunId(UUID requestId, UUID owner) {
        List<UUID> runIds = jdbc.query(
                """
                SELECT train_run_id FROM reservation_requests
                WHERE reservation_request_id = ? AND owner_user_id = ?
                """,
                (result, row) -> result.getObject(1, UUID.class), requestId, owner);
        if (runIds.isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation request not found");
        }
        return runIds.get(0);
    }

    private UUID runId(UUID requestId) {
        List<UUID> runIds = jdbc.query(
                "SELECT train_run_id FROM reservation_requests WHERE reservation_request_id = ?",
                (result, row) -> result.getObject(1, UUID.class), requestId);
        if (runIds.isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation request not found");
        }
        return runIds.get(0);
    }

    private void stateHistory(UUID requestId, String previous, String next, String reason, UUID actor) {
        jdbc.update(
                """
                INSERT INTO reservation_state_history(
                    reservation_state_history_id, reservation_request_id, previous_status,
                    new_status, reason_code, actor_user_id, correlation_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), requestId, previous, next, reason, actor, RequestIds.current());
    }

    private void audit(UUID requestId, UUID actor, String event, Map<String, ?> details) {
        jdbc.update(
                """
                INSERT INTO reservation_audit_log(
                    reservation_audit_id, reservation_request_id, actor_user_id,
                    event_type, details, correlation_id
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?)
                """,
                UUID.randomUUID(), requestId, actor, event, json(details), RequestIds.current());
    }

    private void policyAudit(int version, UUID actor, String event, Map<String, ?> details) {
        jdbc.update(
                """
                INSERT INTO reservation_policy_audit_log(
                    reservation_policy_audit_id, version_number, actor_user_id,
                    event_type, details, correlation_id
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?)
                """,
                UUID.randomUUID(), version, actor, event, json(details), RequestIds.current());
    }

    private void inventoryAudit(UUID runId, UUID berthId, UUID actor, String event, Map<String, ?> details) {
        jdbc.update(
                """
                INSERT INTO inventory_audit_log(
                    inventory_audit_id, train_run_id, inventory_berth_id, actor_user_id,
                    event_type, details, correlation_id
                ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                UUID.randomUUID(), runId, berthId, actor, event, json(details), RequestIds.current());
    }

    private String json(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Reservation audit details could not be serialized", exception);
        }
    }

    private void advisoryLock(String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", result -> {
            if (!result.next()) {
                throw new IllegalStateException("Could not acquire inventory transaction lock");
            }
            return null;
        }, key);
    }

    private int boundedPageSize(int requested) {
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }

    private String normalizeClassCode(String classCode) {
        String value = classCode.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9_-]{1,32}")) {
            throw failure(HttpStatus.BAD_REQUEST, "COACH_CLASS_INVALID",
                    "The coach class code is invalid");
        }
        return value;
    }

    private List<PassengerInput> normalizePassengers(List<PassengerInput> passengers) {
        List<PassengerInput> normalized = new ArrayList<>();
        for (PassengerInput passenger : passengers) {
            String name = passenger.displayName().trim();
            if (name.isEmpty() || name.length() > 120 || name.chars().anyMatch(Character::isISOControl)) {
                throw failure(HttpStatus.BAD_REQUEST, "PASSENGER_NAME_INVALID",
                        "Passenger names must be non-empty and contain no control characters");
            }
            String preference;
            try {
                preference = ReservationAllocationPolicy.normalizePreference(passenger.preferredBerthType());
            } catch (IllegalArgumentException exception) {
                throw failure(HttpStatus.BAD_REQUEST, "BERTH_PREFERENCE_INVALID",
                        "The selected berth preference is not supported");
            }
            normalized.add(new PassengerInput(name, preference));
        }
        return List.copyOf(normalized);
    }

    private String fingerprint(CreateReservationRequest request, String classCode, List<PassengerInput> passengers) {
        StringBuilder canonical = new StringBuilder();
        appendFingerprintPart(canonical, request.trainRunId().toString());
        appendFingerprintPart(canonical, request.journeyDate().toString());
        appendFingerprintPart(canonical, request.fromStationId().toString());
        appendFingerprintPart(canonical, request.toStationId().toString());
        appendFingerprintPart(canonical, classCode);
        appendFingerprintPart(canonical, Boolean.toString(request.groupSeatingRequested()));
        for (PassengerInput passenger : passengers) {
            appendFingerprintPart(canonical, passenger.displayName());
            appendFingerprintPart(canonical, passenger.preferredBerthType());
        }
        request.inventoryHoldIds().stream().sorted()
                .forEach(holdId -> appendFingerprintPart(canonical, holdId.toString()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for reservation idempotency", exception);
        }
    }

    private void appendFingerprintPart(StringBuilder canonical, String value) {
        if (value == null) {
            canonical.append("-1:");
        } else {
            canonical.append(value.length()).append(':').append(value);
        }
        canonical.append('|');
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of(REQUESTED, PENDING_BOOKING, CONFIRMED, RELEASED, FAILED, EXPIRED).contains(normalized)) {
            throw failure(HttpStatus.BAD_REQUEST, "RESERVATION_STATUS_INVALID",
                    "The reservation status filter is invalid");
        }
        return normalized;
    }

    private ReservationSummary summary(ResultSet result) throws SQLException {
        return new ReservationSummary(
                result.getObject("reservation_request_id", UUID.class),
                result.getObject("train_run_id", UUID.class),
                result.getObject("journey_date", LocalDate.class),
                result.getString("coach_class_code"),
                result.getString("status"),
                result.getInt("passenger_count"),
                result.getBoolean("group_seating_requested"),
                instant(result, "expires_at"),
                result.getTimestamp("created_at").toInstant());
    }

    private ReservationAllocationPolicy.Weights publishedPolicy() {
        List<ReservationAllocationPolicy.Weights> policies = jdbc.query(
                """
                SELECT version_number, preferred_berth_weight, same_coach_weight, nearby_berth_weight
                FROM reservation_allocation_policies
                WHERE status = 'PUBLISHED'
                """,
                (result, row) -> new ReservationAllocationPolicy.Weights(
                        result.getInt("version_number"),
                        result.getInt("preferred_berth_weight"),
                        result.getInt("same_coach_weight"),
                        result.getInt("nearby_berth_weight")));
        if (policies.isEmpty()) {
            throw new IllegalStateException("No published reservation allocation policy is configured");
        }
        return policies.get(0);
    }

    private AllocationPolicyVersion allocationPolicyVersion(int version) {
        List<AllocationPolicyVersion> policies = jdbc.query(
                """
                SELECT policy.version_number, policy.status, policy.preferred_berth_weight,
                       policy.same_coach_weight, policy.nearby_berth_weight,
                       creator.full_name AS created_by_name, approver.full_name AS approved_by_name,
                       policy.created_at, policy.approved_at, policy.published_at
                FROM reservation_allocation_policies policy
                LEFT JOIN users creator ON creator.user_id = policy.created_by
                LEFT JOIN users approver ON approver.user_id = policy.approved_by
                WHERE policy.version_number = ?
                """,
                (result, row) -> policyVersion(result),
                version);
        if (policies.isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "ALLOCATION_POLICY_NOT_FOUND",
                    "Allocation policy version not found");
        }
        return policies.get(0);
    }

    private AllocationPolicyVersion policyVersion(ResultSet result) throws SQLException {
        return new AllocationPolicyVersion(
                result.getInt("version_number"),
                result.getString("status"),
                result.getInt("preferred_berth_weight"),
                result.getInt("same_coach_weight"),
                result.getInt("nearby_berth_weight"),
                result.getString("created_by_name"),
                result.getString("approved_by_name"),
                instant(result, "created_at"),
                instant(result, "approved_at"),
                instant(result, "published_at"));
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        java.sql.Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Integer integer(ResultSet result, String column) throws SQLException {
        Number value = (Number) result.getObject(column);
        return value == null ? null : value.intValue();
    }

    private ReservationException failure(HttpStatus status, String code, String message) {
        return new ReservationException(status, code, message);
    }

    private record Journey(int fromIndex, int toIndex) {
    }

    private record StopRow(int index, String stationName) {
    }

    private record RunRow(LocalDate serviceDate, String status) {
    }

    private record ReservationIdentity(UUID requestId, String fingerprint) {
    }

    private record RequestRow(
            UUID requestId,
            UUID ownerId,
            UUID runId,
            int fromIndex,
            int toIndex,
            String classCode,
            boolean groupSeatingRequested,
            String status,
            Instant expiresAt,
            Integer allocationPolicyVersion,
            UUID bookingCorrelationId
    ) {
    }

    private record HoldRow(
            UUID holdId,
            UUID trainRunId,
            UUID berthId,
            UUID ownerId,
            int fromIndex,
            int toIndex,
            String status,
            boolean active,
            String baseStatus,
            String berthType,
            int displayOrder,
            String coachNumber,
            String classCode,
            Instant expiresAt,
            int segmentCount
    ) {
    }

    private record RequestPassenger(
            UUID passengerId,
            String displayName,
            String preference
    ) {
    }

    private record AssignmentRow(
            RequestPassenger passenger,
            HoldRow hold,
            String preferenceResult,
            int score
    ) {
    }
}
