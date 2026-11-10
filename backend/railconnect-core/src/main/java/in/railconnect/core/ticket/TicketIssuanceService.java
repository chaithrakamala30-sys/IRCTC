package in.railconnect.core.ticket;

import in.railconnect.core.api.RequestIds;
import in.railconnect.core.ticket.TicketModels.TicketPassenger;
import in.railconnect.core.ticket.TicketModels.TicketPage;
import in.railconnect.core.ticket.TicketModels.TicketAllocation;
import in.railconnect.core.ticket.TicketModels.TicketDiagnostics;
import in.railconnect.core.ticket.TicketModels.TicketIssueEvent;
import in.railconnect.core.ticket.TicketModels.TicketOperationsVerification;
import in.railconnect.core.ticket.TicketModels.TicketIssuanceFailure;
import in.railconnect.core.ticket.TicketModels.TicketSummary;
import in.railconnect.core.ticket.TicketModels.TicketVerification;
import in.railconnect.core.ticket.TicketModels.TicketView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class TicketIssuanceService {
    private static final String NOTICE = "RailConnect is an independent simulated railway reservation platform. "
            + "This record is not an official Indian Railways or IRCTC ticket.";
    private final JdbcTemplate jdbc;
    private final PnrGenerator pnrGenerator;
    private final TicketTokenService tokenService;
    private final int verificationRequestsPerMinute;
    private final SecureRandom secureRandom = new SecureRandom();

    public TicketIssuanceService(
            JdbcTemplate jdbc,
            PnrGenerator pnrGenerator,
            TicketTokenService tokenService,
            @Value("${railconnect.tickets.verification-requests-per-minute:20}") int verificationRequestsPerMinute
    ) {
        if (verificationRequestsPerMinute < 1 || verificationRequestsPerMinute > 1000) {
            throw new IllegalArgumentException("Ticket verification request limit must be between 1 and 1000");
        }
        this.jdbc = jdbc;
        this.pnrGenerator = pnrGenerator;
        this.tokenService = tokenService;
        this.verificationRequestsPerMinute = verificationRequestsPerMinute;
    }

    @Transactional
    public void enqueueConfirmedBooking(UUID bookingId) {
        int inserted = jdbc.update("""
                INSERT INTO ticket_issuance_jobs(ticket_issuance_job_id, booking_id)
                SELECT ?, booking_id FROM bookings
                WHERE booking_id = ? AND status = 'CONFIRMED'
                ON CONFLICT (booking_id) DO NOTHING
                """, UUID.randomUUID(), bookingId);
        if (inserted == 1) {
            appendIssueHistory(bookingId, null, "QUEUED", "BOOKING_CONFIRMED");
        }
    }

    @Transactional
    public void issueJob(UUID jobId) {
        IssueJob job = jdbc.query("""
                SELECT booking_id, job_status FROM ticket_issuance_jobs
                WHERE ticket_issuance_job_id = ? FOR UPDATE
                """, result -> result.next()
                ? new IssueJob(result.getObject("booking_id", UUID.class), result.getString("job_status")) : null,
                jobId);
        if (job == null || "COMPLETED".equals(job.status()) || "FAILED".equals(job.status())) {
            return;
        }
        TicketIdentity existing = findTicketIdentity(job.bookingId());
        if (existing != null) {
            completeJob(jobId, job.bookingId(), existing.ticketId());
            appendIssueHistory(job.bookingId(), existing.ticketId(), "ISSUE_RETRY", "IDEMPOTENT_REPLAY");
            return;
        }

        IssueSource source = jdbc.query("""
                SELECT b.owner_user_id, b.status, b.booking_reference, b.train_run_id, b.journey_date,
                       b.from_station_id, b.to_station_id, b.coach_class_code, b.total_payable, b.currency,
                       b.created_at, b.reservation_request_id,
                       b.train_snapshot ->> 'trainNumber' AS train_number,
                       b.train_snapshot ->> 'trainName' AS train_name,
                       b.journey_snapshot ->> 'originName' AS origin_name,
                       b.journey_snapshot ->> 'destinationName' AS destination_name,
                       (SELECT COUNT(*) FROM booking_passengers bp WHERE bp.booking_id = b.booking_id) AS passenger_count,
                       (SELECT COUNT(*) FROM booking_passengers bp
                        JOIN reservation_allocations ra
                          ON ra.reservation_allocation_id = bp.reservation_allocation_id
                        WHERE bp.booking_id = b.booking_id AND ra.status = 'CONFIRMED') AS confirmed_allocation_count,
                       (SELECT COUNT(*) FROM payment_orders po
                        WHERE po.booking_id = b.booking_id AND po.payment_status = 'SUCCEEDED'
                          AND EXISTS (
                              SELECT 1 FROM payment_status_history ph
                              WHERE ph.payment_order_id = po.payment_order_id
                                AND ph.new_status = 'SUCCEEDED'
                                AND ph.source IN ('STATUS_QUERY', 'VERIFIED_WEBHOOK', 'RECONCILIATION')
                          )) AS verified_payment_count
                FROM bookings b
                WHERE b.booking_id = ?
                """, result -> result.next() ? source(result) : null, job.bookingId());
        if (source == null || !"CONFIRMED".equals(source.status())
                || source.passengerCount() < 1
                || source.passengerCount() != source.confirmedAllocationCount()
                || source.verifiedPaymentCount() != 1) {
            throw new TicketException(HttpStatus.CONFLICT, "TICKET_ISSUANCE_PRECONDITION_FAILED",
                    "The booking is not eligible for ticket issuance");
        }

        Instant expiry = tokenService.expiry();
        UUID ticketId = UUID.randomUUID();
        String publicHandle = newPublicHandle();
        tokenService.sign(publicHandle, expiry);
        String pnr = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = pnrGenerator.next();
            List<UUID> inserted = jdbc.query("""
                    INSERT INTO tickets(ticket_id, booking_id, owner_user_id, pnr, ticket_status)
                    VALUES (?, ?, ?, ?, 'ISSUED')
                    ON CONFLICT (pnr) DO NOTHING
                    RETURNING ticket_id
                    """, (result, row) -> result.getObject(1, UUID.class),
                    ticketId, job.bookingId(), source.ownerId(), candidate);
            if (!inserted.isEmpty()) {
                pnr = candidate;
                break;
            }
        }
        if (pnr == null) {
            throw new TicketException(HttpStatus.SERVICE_UNAVAILABLE, "PNR_GENERATION_RETRY",
                    "A unique RailConnect PNR could not be generated");
        }

        List<IssuePassenger> passengers = jdbc.query("""
                SELECT booking_passenger_id, passenger_number, display_name, coach_number,
                       berth_number, berth_type, (fare_snapshot ->> 'totalAmount')::numeric AS fare_paid
                FROM booking_passengers
                WHERE booking_id = ?
                ORDER BY passenger_number
                """, (result, row) -> new IssuePassenger(
                result.getObject("booking_passenger_id", UUID.class),
                result.getInt("passenger_number"),
                result.getString("display_name"),
                result.getString("coach_number"),
                result.getString("berth_number"),
                result.getString("berth_type"),
                result.getBigDecimal("fare_paid"),
                source.currency()), job.bookingId());
        if (passengers.size() != source.passengerCount()
                || passengers.stream().anyMatch(passenger -> passenger.farePaid() == null)) {
            throw new TicketException(HttpStatus.CONFLICT, "TICKET_PASSENGER_SNAPSHOT_INVALID",
                    "The confirmed passenger and fare details are incomplete");
        }
        for (IssuePassenger passenger : passengers) {
            jdbc.update("""
                    INSERT INTO ticket_passengers(
                        ticket_passenger_id, ticket_id, booking_passenger_id, passenger_number,
                        public_ticket_reference, passenger_name, coach_number, berth_number, berth_type, fare_paid, currency
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), ticketId, passenger.bookingPassengerId(), passenger.passengerNumber(),
                    newPassengerReference(), passenger.passengerName(), passenger.coachNumber(), passenger.berthNumber(),
                    passenger.berthType(), passenger.farePaid(), source.currency());
        }
        jdbc.update("""
                INSERT INTO ticket_verification_tokens(
                    ticket_verification_token_id, ticket_id, public_handle, expires_at
                ) VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), ticketId, publicHandle, Timestamp.from(expiry));
        completeJob(jobId, job.bookingId(), ticketId);
        appendIssueHistory(job.bookingId(), ticketId, "ISSUED", "VERIFIED_PAYMENT_AND_CONFIRMED_ALLOCATION");
        audit(ticketId, source.ownerId(), "TICKET_ISSUED", "{}");
    }

    @Transactional
    public void recordFailure(UUID jobId, String reasonCode) {
        IssueJob job = jdbc.query("""
                SELECT booking_id, attempt_count, job_status FROM ticket_issuance_jobs
                WHERE ticket_issuance_job_id = ? FOR UPDATE
                """, result -> result.next()
                ? new IssueJob(result.getObject("booking_id", UUID.class),
                        result.getString("job_status"), result.getInt("attempt_count")) : null, jobId);
        if (job == null || "COMPLETED".equals(job.status()) || "FAILED".equals(job.status())) {
            return;
        }
        int attempts = job.attemptCount() + 1;
        String nextStatus = attempts >= 10 ? "FAILED" : "RETRY";
        long backoffSeconds = Math.min(300, 5L << Math.min(attempts - 1, 6));
        jdbc.update("""
                UPDATE ticket_issuance_jobs
                SET attempt_count = ?, job_status = ?, last_failure_code = ?,
                    next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    updated_at = CURRENT_TIMESTAMP
                WHERE ticket_issuance_job_id = ?
                """, attempts, nextStatus, reasonCode, backoffSeconds, jobId);
        appendIssueHistory(job.bookingId(), null,
                "FAILED".equals(nextStatus) ? "ISSUE_FAILED" : "ISSUE_RETRY", reasonCode);
    }

    @Transactional(readOnly = true)
    public TicketPage ownTickets(UUID ownerId, int page, int size, String journeyPeriod) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        String journeyPredicate = "UPCOMING".equals(journeyPeriod) ? " AND b.journey_date >= CURRENT_DATE"
                : "PAST".equals(journeyPeriod) ? " AND b.journey_date < CURRENT_DATE" : "";
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tickets t JOIN bookings b USING (booking_id)
                WHERE t.owner_user_id = ?
                """ + journeyPredicate, Long.class, ownerId);
        List<TicketSummary> items = jdbc.query("""
                SELECT t.ticket_id, t.pnr, b.booking_reference, t.ticket_status,
                       b.train_snapshot ->> 'trainNumber' AS train_number,
                       b.train_snapshot ->> 'trainName' AS train_name,
                       b.journey_date, b.journey_snapshot ->> 'originName' AS origin_name,
                       b.journey_snapshot ->> 'destinationName' AS destination_name,
                       t.issued_at, (SELECT COUNT(*) FROM ticket_passengers tp WHERE tp.ticket_id = t.ticket_id) AS count
                FROM tickets t JOIN bookings b USING (booking_id)
                WHERE t.owner_user_id = ?
                """ + journeyPredicate + """
                ORDER BY b.journey_date DESC, t.issued_at DESC, t.ticket_id
                LIMIT ? OFFSET ?
                """, (result, row) -> summary(result), ownerId, safeSize, (long) safePage * safeSize);
        return new TicketPage(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional
    public TicketView ownTicket(UUID ticketId, UUID ownerId) {
        TicketView view = readTicket("""
                WHERE t.ticket_id = ? AND t.owner_user_id = ?
                """, ticketId, ownerId);
        if (view == null) throw notFound();
        audit(ticketId, ownerId, "PASSENGER_READ", "{}");
        return view;
    }

    @Transactional
    public TicketView ownTicketByPnr(String pnr, UUID ownerId) {
        TicketView view = readTicket("WHERE t.pnr = ? AND t.owner_user_id = ?", pnr, ownerId);
        if (view == null) throw notFound();
        audit(view.ticketId(), ownerId, "PASSENGER_READ", "{\"lookup\":\"PNR\"}");
        return view;
    }

    @Transactional
    public TicketView ownTicketByBooking(UUID bookingId, UUID ownerId) {
        TicketView view = readTicket("WHERE t.booking_id = ? AND t.owner_user_id = ?", bookingId, ownerId);
        if (view == null) throw notFound();
        audit(view.ticketId(), ownerId, "PASSENGER_READ", "{\"lookup\":\"BOOKING\"}");
        return view;
    }

    @Transactional
    public TicketPage adminSearch(String query, int page, int size, UUID actorId) {
        String search = query == null ? "" : query.trim();
        if (search.length() > 80) {
            throw new TicketException(HttpStatus.BAD_REQUEST, "TICKET_SEARCH_INVALID",
                    "The ticket search term is too long");
        }
        String predicate = search.isEmpty()
                ? "TRUE"
                : "(t.pnr = ? OR CAST(t.ticket_id AS text) = ? OR b.booking_reference = ?)";
        List<Object> parameters = new ArrayList<>();
        if (!search.isEmpty()) {
            parameters.add(search);
            parameters.add(search);
            parameters.add(search);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM tickets t JOIN bookings b USING (booking_id) WHERE "
                + predicate, Long.class, parameters.toArray());
        parameters.add(Math.max(1, Math.min(100, size)));
        parameters.add((long) Math.max(0, page) * Math.max(1, Math.min(100, size)));
        List<TicketSummary> items = jdbc.query("""
                SELECT t.ticket_id, t.pnr, b.booking_reference, t.ticket_status,
                       b.train_snapshot ->> 'trainNumber' AS train_number,
                       b.train_snapshot ->> 'trainName' AS train_name,
                       b.journey_date, b.journey_snapshot ->> 'originName' AS origin_name,
                       b.journey_snapshot ->> 'destinationName' AS destination_name,
                       t.issued_at, (SELECT COUNT(*) FROM ticket_passengers tp WHERE tp.ticket_id = t.ticket_id) AS count
                FROM tickets t JOIN bookings b USING (booking_id)
                WHERE """ + predicate + """
                ORDER BY t.issued_at DESC, t.ticket_id
                LIMIT ? OFFSET ?
                """, (result, row) -> summary(result), parameters.toArray());
        audit(null, actorId, "ADMIN_LOOKUP", "{\"queryProvided\":" + !search.isEmpty() + "}");
        return new TicketPage(items, Math.max(0, page), Math.max(1, Math.min(100, size)), total == null ? 0 : total);
    }

    @Transactional
    public TicketView adminTicket(UUID ticketId, UUID actorId) {
        TicketView view = readTicket("WHERE t.ticket_id = ?", ticketId);
        if (view == null) throw notFound();
        audit(ticketId, actorId, "ADMIN_LOOKUP", "{}");
        return view;
    }

    @Transactional
    public TicketView adminTicketByPnr(String pnr, UUID actorId) {
        TicketView view = readTicket("""
                WHERE t.pnr = ? AND EXISTS (
                    SELECT 1 FROM payment_orders po
                    JOIN payment_status_history ph ON ph.payment_order_id = po.payment_order_id
                    WHERE po.booking_id = t.booking_id AND po.payment_status = 'SUCCEEDED'
                      AND ph.new_status = 'SUCCEEDED'
                      AND ph.source IN ('STATUS_QUERY', 'VERIFIED_WEBHOOK', 'RECONCILIATION')
                )
                """, pnr);
        if (view == null) throw notFound();
        audit(view.ticketId(), actorId, "ADMIN_LOOKUP", "{\"lookup\":\"PNR\"}");
        return view;
    }

    @Transactional
    public TicketOperationsVerification verifyPnrForOperationsDetailed(String pnr, UUID actorId) {
        TicketView view = readTicket("""
                WHERE t.pnr = ? AND EXISTS (
                    SELECT 1 FROM payment_orders po
                    JOIN payment_status_history ph ON ph.payment_order_id = po.payment_order_id
                    WHERE po.booking_id = t.booking_id AND po.payment_status = 'SUCCEEDED'
                      AND ph.new_status = 'SUCCEEDED'
                      AND ph.source IN ('STATUS_QUERY', 'VERIFIED_WEBHOOK', 'RECONCILIATION')
                )
                """, pnr);
        if (view == null) throw notFound();
        audit(view.ticketId(), actorId, "OPERATIONS_VERIFY", "{\"lookup\":\"PNR\"}");
        return operationsVerification(view);
    }

    @Transactional
    public TicketVerification verifyPublicToken(String token, String requester) {
        if (requester == null || requester.isBlank()) {
            throw new TicketException(HttpStatus.BAD_REQUEST, "TICKET_VERIFICATION_INVALID",
                    "Ticket verification request is invalid");
        }
        if (!allowVerification(requester)) {
            throw new TicketException(HttpStatus.TOO_MANY_REQUESTS, "TICKET_VERIFICATION_RATE_LIMITED",
                    "Too many verification requests; try again later");
        }
        TicketView view = verifiedTokenTicket(token);
        if (view == null) return invalidVerification();
        audit(view.ticketId(), null, "PUBLIC_VERIFY", "{}");
        return verification(view);
    }

    @Transactional
    public TicketOperationsVerification verifyTokenForOperations(String token, UUID actorId) {
        TicketView view = verifiedTokenTicket(token);
        if (view == null) {
            return new TicketOperationsVerification(invalidVerification(), List.of());
        }
        audit(view.ticketId(), actorId, "OPERATIONS_VERIFY", "{\"lookup\":\"QR\"}");
        return operationsVerification(view);
    }

    @Transactional
    public TicketDiagnostics diagnostics(UUID ticketId, UUID actorId) {
        TicketView view = readTicket("WHERE t.ticket_id = ?", ticketId);
        if (view == null) throw notFound();
        TicketJobDiagnostic job = jdbc.query("""
                SELECT job_status, attempt_count, last_failure_code
                FROM ticket_issuance_jobs WHERE booking_id = ?
                """, result -> result.next()
                ? new TicketJobDiagnostic(result.getString(1), result.getInt(2), result.getString(3))
                : new TicketJobDiagnostic("UNKNOWN", 0, null), view.bookingId());
        List<TicketIssueEvent> history = jdbc.query("""
                SELECT event_type, reason_code, occurred_at
                FROM ticket_issue_history WHERE booking_id = ?
                ORDER BY occurred_at, ticket_issue_history_id
                """, (result, row) -> new TicketIssueEvent(result.getString(1), result.getString(2),
                result.getTimestamp(3).toInstant()), view.bookingId());
        audit(ticketId, actorId, "ADMIN_LOOKUP", "{\"details\":\"issuance\"}");
        return new TicketDiagnostics(ticketId, view.pnr(), job.status(), job.attemptCount(),
                job.lastFailureCode(), history);
    }

    @Transactional
    public List<TicketIssuanceFailure> issuanceFailures(UUID actorId) {
        List<TicketIssuanceFailure> failures = jdbc.query("""
                SELECT b.booking_reference, b.train_snapshot ->> 'trainNumber' AS train_number,
                       b.journey_date, j.job_status, j.attempt_count, j.last_failure_code, j.updated_at
                FROM ticket_issuance_jobs j
                JOIN bookings b USING (booking_id)
                WHERE j.job_status IN ('PENDING', 'RETRY', 'FAILED')
                ORDER BY j.updated_at DESC, j.ticket_issuance_job_id
                LIMIT 100
                """, (result, row) -> new TicketIssuanceFailure(
                result.getString("booking_reference"), result.getString("train_number"),
                result.getObject("journey_date", java.time.LocalDate.class), result.getString("job_status"),
                result.getInt("attempt_count"), result.getString("last_failure_code"),
                result.getTimestamp("updated_at").toInstant()));
        audit(null, actorId, "ADMIN_LOOKUP", "{\"details\":\"issuanceFailures\"}");
        return failures;
    }

    @Transactional
    public void retryIssuance(String bookingReference, UUID actorId) {
        IssueJobForRetry job = jdbc.query("""
                SELECT j.ticket_issuance_job_id, j.booking_id, j.job_status
                FROM ticket_issuance_jobs j
                JOIN bookings b USING (booking_id)
                WHERE b.booking_reference = ?
                FOR UPDATE OF j
                """, result -> result.next()
                ? new IssueJobForRetry(result.getObject(1, UUID.class),
                        result.getObject(2, UUID.class), result.getString(3)) : null, bookingReference);
        if (job == null) throw notFound();
        if (!"FAILED".equals(job.status())) {
            throw new TicketException(HttpStatus.CONFLICT, "TICKET_RETRY_NOT_ALLOWED",
                    "Only a failed issuance job can be manually retried");
        }
        jdbc.update("""
                UPDATE ticket_issuance_jobs
                SET job_status = 'RETRY', attempt_count = 0, last_failure_code = NULL,
                    next_attempt_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE ticket_issuance_job_id = ?
                """, job.jobId());
        appendIssueHistory(job.bookingId(), null, "ISSUE_RETRY", "ADMIN_AUTHORIZED_RETRY");
        audit(null, actorId, "ADMIN_RETRY", "{\"action\":\"RETRY_ISSUANCE\"}");
    }

    private TicketView verifiedTokenTicket(String token) {
        if (!tokenService.verify(token)) return null;
        String handle = tokenService.publicHandle(token);
        Instant expiry = tokenService.tokenExpiry(token);
        return readTicket("""
                JOIN ticket_verification_tokens v ON v.ticket_id = t.ticket_id
                WHERE v.public_handle = ? AND v.expires_at >= ?
                  AND v.expires_at < ? + INTERVAL '1 second' AND v.revoked_at IS NULL
                  AND v.expires_at > CURRENT_TIMESTAMP AND t.ticket_status = 'ISSUED'
                  AND EXISTS (
                      SELECT 1 FROM payment_orders po
                      JOIN payment_status_history ph ON ph.payment_order_id = po.payment_order_id
                      WHERE po.booking_id = t.booking_id AND po.payment_status = 'SUCCEEDED'
                        AND ph.new_status = 'SUCCEEDED'
                        AND ph.source IN ('STATUS_QUERY', 'VERIFIED_WEBHOOK', 'RECONCILIATION')
                  )
                """, handle, Timestamp.from(expiry), Timestamp.from(expiry));
    }

    @Transactional
    public void recordPdfOutcome(UUID ticketId, UUID actorId, boolean success) {
        audit(ticketId, actorId, success ? "PDF_DOWNLOAD" : "PDF_DOWNLOAD",
                success ? "{\"result\":\"GENERATED\"}" : "{\"result\":\"FAILED\"}");
        appendIssueHistory(ticketBookingId(ticketId), ticketId,
                success ? "PDF_GENERATED" : "PDF_FAILED",
                success ? "PDF_GENERATED" : "PDF_GENERATION_FAILED");
    }

    @Transactional(readOnly = true)
    public TicketView readForPdf(UUID ticketId, UUID ownerId) {
        TicketView view = readTicket("WHERE t.ticket_id = ? AND t.owner_user_id = ?", ticketId, ownerId);
        if (view == null) throw notFound();
        return view;
    }

    @Transactional
    public String verificationToken(UUID ticketId) {
        String handle = jdbc.query("""
                SELECT public_handle FROM ticket_verification_tokens
                WHERE ticket_id = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                ORDER BY created_at DESC LIMIT 1
                """, result -> result.next() ? result.getString(1) : null, ticketId);
        if (handle == null) {
            handle = newPublicHandle();
            Instant expiry = tokenService.expiry();
            String signedToken = tokenService.sign(handle, expiry);
            jdbc.update("""
                    INSERT INTO ticket_verification_tokens(
                        ticket_verification_token_id, ticket_id, public_handle, expires_at
                    ) VALUES (?, ?, ?, ?)
                    """, UUID.randomUUID(), ticketId, handle, Timestamp.from(expiry));
            return signedToken;
        }
        Instant expiry = jdbc.queryForObject("""
                SELECT expires_at FROM ticket_verification_tokens
                WHERE ticket_id = ? AND public_handle = ?
                """, (result, row) -> result.getTimestamp(1).toInstant(), ticketId, handle);
        return tokenService.sign(handle, expiry);
    }

    private TicketView readTicket(String predicate, Object... parameters) {
        TicketHeader header = jdbc.query("""
                SELECT t.ticket_id, t.pnr, t.booking_id, t.ticket_status, t.issued_at,
                       b.booking_reference, b.created_at AS booking_created_at, b.train_run_id,
                       b.journey_date, b.coach_class_code, b.total_payable, b.currency,
                       b.train_snapshot ->> 'trainNumber' AS train_number,
                       b.train_snapshot ->> 'trainName' AS train_name,
                       b.journey_snapshot ->> 'originName' AS origin_name,
                       b.journey_snapshot ->> 'destinationName' AS destination_name,
                       (SELECT po.transaction_reference FROM payment_orders po
                        WHERE po.booking_id = b.booking_id AND po.payment_status = 'SUCCEEDED'
                        ORDER BY po.completed_at DESC NULLS LAST LIMIT 1) AS payment_reference,
                       (SELECT po.payment_status FROM payment_orders po
                        WHERE po.booking_id = b.booking_id
                        ORDER BY po.completed_at DESC NULLS LAST LIMIT 1) AS payment_status,
                       (SELECT origin.departure_time FROM inventory_run_stops origin
                        WHERE origin.train_run_id = b.train_run_id AND origin.station_id = b.from_station_id)
                           AS boarding_time,
                       (SELECT destination.arrival_time FROM inventory_run_stops destination
                        WHERE destination.train_run_id = b.train_run_id AND destination.station_id = b.to_station_id)
                           AS arrival_time
                FROM tickets t JOIN bookings b USING (booking_id)
                """ + predicate,
                result -> result.next() ? header(result) : null, parameters);
        if (header == null) return null;
        List<TicketPassenger> passengers = jdbc.query("""
                SELECT public_ticket_reference, passenger_number, passenger_name, coach_number,
                       berth_number, berth_type, fare_paid, currency
                FROM ticket_passengers WHERE ticket_id = ?
                ORDER BY passenger_number
                """, (result, row) -> new TicketPassenger(
                result.getString("public_ticket_reference"),
                result.getInt("passenger_number"), result.getString("passenger_name"),
                result.getString("coach_number"), result.getString("berth_number"),
                result.getString("berth_type"), result.getBigDecimal("fare_paid"),
                result.getString("currency")), header.ticketId());
        return new TicketView(header.ticketId(), header.pnr(), header.bookingId(),
                header.bookingReference(), header.ticketStatus(), header.bookingCreatedAt(), header.issuedAt(),
                header.trainNumber(), header.trainName(), header.coachClassCode(), header.journeyDate(),
                header.originName(), header.destinationName(), header.boardingTime(), header.arrivalTime(),
                header.totalPaid(), header.currency(), header.paymentReference(), header.paymentStatus(), passengers);
    }

    private boolean allowVerification(String requester) {
        String requesterHash = sha256(requester);
        Integer count = jdbc.queryForObject("""
                INSERT INTO ticket_verification_rate_limits AS current(
                    requester_hash, window_started_at, request_count
                ) VALUES (?, CURRENT_TIMESTAMP, 1)
                ON CONFLICT (requester_hash) DO UPDATE
                SET window_started_at = CASE
                        WHEN CURRENT_TIMESTAMP >= current.window_started_at + INTERVAL '1 minute'
                        THEN CURRENT_TIMESTAMP ELSE current.window_started_at END,
                    request_count = CASE
                        WHEN CURRENT_TIMESTAMP >= current.window_started_at + INTERVAL '1 minute'
                        THEN 1 ELSE current.request_count + 1 END
                RETURNING request_count
                """, Integer.class, requesterHash);
        return count != null && count <= verificationRequestsPerMinute;
    }

    private void completeJob(UUID jobId, UUID bookingId, UUID ticketId) {
        jdbc.update("""
                UPDATE ticket_issuance_jobs
                SET job_status = 'COMPLETED', last_failure_code = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE ticket_issuance_job_id = ?
                """, jobId);
    }

    private void appendIssueHistory(UUID bookingId, UUID ticketId, String event, String reason) {
        jdbc.update("""
                INSERT INTO ticket_issue_history(
                    ticket_issue_history_id, booking_id, ticket_id, event_type, reason_code, correlation_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), bookingId, ticketId, event, reason, RequestIds.current());
    }

    private void audit(UUID ticketId, UUID actorId, String action, String details) {
        jdbc.update("""
                INSERT INTO ticket_audit_logs(
                    ticket_audit_log_id, ticket_id, actor_user_id, action, details, correlation_id
                ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
                """, UUID.randomUUID(), ticketId, actorId, action, details, RequestIds.current());
    }

    private UUID ticketBookingId(UUID ticketId) {
        UUID bookingId = jdbc.query("""
                SELECT booking_id FROM tickets WHERE ticket_id = ?
                """, result -> result.next() ? result.getObject(1, UUID.class) : null, ticketId);
        if (bookingId == null) throw notFound();
        return bookingId;
    }

    private TicketIdentity findTicketIdentity(UUID bookingId) {
        return jdbc.query("SELECT ticket_id, pnr FROM tickets WHERE booking_id = ?",
                result -> result.next()
                        ? new TicketIdentity(result.getObject(1, UUID.class), result.getString(2)) : null, bookingId);
    }

    private String newPublicHandle() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String newPassengerReference() {
        byte[] bytes = new byte[12];
        secureRandom.nextBytes(bytes);
        return "RC-PAX-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static TicketSummary summary(ResultSet result) throws SQLException {
        return new TicketSummary(result.getObject("ticket_id", UUID.class),
                result.getString("pnr"), result.getString("booking_reference"),
                result.getString("ticket_status"), result.getString("train_number"),
                result.getString("train_name"), result.getObject("journey_date", java.time.LocalDate.class),
                result.getString("origin_name"), result.getString("destination_name"),
                result.getInt("count"), result.getTimestamp("issued_at").toInstant());
    }

    private static TicketHeader header(ResultSet result) throws SQLException {
        return new TicketHeader(result.getObject("ticket_id", UUID.class), result.getString("pnr"),
                result.getObject("booking_id", UUID.class), result.getString("booking_reference"),
                result.getString("ticket_status"), result.getTimestamp("booking_created_at").toInstant(),
                result.getTimestamp("issued_at").toInstant(), result.getString("train_number"),
                result.getString("train_name"), result.getString("coach_class_code"),
                result.getObject("journey_date", java.time.LocalDate.class), result.getString("origin_name"),
                result.getString("destination_name"), result.getObject("boarding_time", java.time.LocalTime.class),
                result.getObject("arrival_time", java.time.LocalTime.class), result.getBigDecimal("total_payable"),
                result.getString("currency"), result.getString("payment_reference"),
                result.getString("payment_status"));
    }

    private static IssueSource source(ResultSet result) throws SQLException {
        return new IssueSource(result.getObject("owner_user_id", UUID.class), result.getString("status"),
                result.getString("booking_reference"), result.getObject("train_run_id", UUID.class),
                result.getObject("journey_date", java.time.LocalDate.class),
                result.getObject("from_station_id", UUID.class), result.getObject("to_station_id", UUID.class),
                result.getString("coach_class_code"), result.getBigDecimal("total_payable"),
                result.getString("currency"), result.getTimestamp("created_at").toInstant(),
                result.getObject("reservation_request_id", UUID.class), result.getString("train_number"),
                result.getString("train_name"), result.getString("origin_name"), result.getString("destination_name"),
                result.getInt("passenger_count"), result.getInt("confirmed_allocation_count"),
                result.getInt("verified_payment_count"));
    }

    private static TicketVerification verification(TicketView ticket) {
        return new TicketVerification(true, ticket.pnr(), ticket.ticketStatus(), ticket.trainNumber(),
                ticket.trainName(), ticket.journeyDate(), ticket.boardingStation(), ticket.destinationStation(),
                ticket.passengers().size(), Instant.now(), NOTICE);
    }

    private static TicketOperationsVerification operationsVerification(TicketView ticket) {
        List<TicketAllocation> allocations = ticket.passengers().stream()
                .map(passenger -> new TicketAllocation(passenger.passengerNumber(), passenger.coachNumber(),
                        passenger.berthNumber(), passenger.berthType()))
                .toList();
        return new TicketOperationsVerification(verification(ticket), allocations);
    }

    private static TicketVerification invalidVerification() {
        return new TicketVerification(false, null, "INVALID", null, null, null, null, null, 0,
                Instant.now(), NOTICE);
    }

    private static String failureCode(RuntimeException exception) {
        if (exception instanceof TicketException ticketException) return ticketException.code();
        if (exception instanceof DuplicateKeyException) return "TICKET_UNIQUE_CONFLICT";
        return "TICKET_ISSUANCE_TEMPORARY_FAILURE";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static TicketException notFound() {
        return new TicketException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket was not found");
    }

    private record IssueJob(UUID bookingId, String status, int attemptCount) {
        private IssueJob(UUID bookingId, String status) {
            this(bookingId, status, 0);
        }
    }

    private record TicketIdentity(UUID ticketId, String pnr) {
    }

    private record TicketJobDiagnostic(String status, int attemptCount, String lastFailureCode) {
    }

    private record IssueJobForRetry(UUID jobId, UUID bookingId, String status) {
    }

    private record IssuePassenger(
            UUID bookingPassengerId,
            int passengerNumber,
            String passengerName,
            String coachNumber,
            String berthNumber,
            String berthType,
            java.math.BigDecimal farePaid,
            String currency
    ) {
    }

    private record IssueSource(
            UUID ownerId,
            String status,
            String bookingReference,
            UUID trainRunId,
            java.time.LocalDate journeyDate,
            UUID fromStationId,
            UUID toStationId,
            String coachClassCode,
            java.math.BigDecimal totalPayable,
            String currency,
            Instant createdAt,
            UUID reservationRequestId,
            String trainNumber,
            String trainName,
            String originName,
            String destinationName,
            int passengerCount,
            int confirmedAllocationCount,
            int verifiedPaymentCount
    ) {
    }

    private record TicketHeader(
            UUID ticketId,
            String pnr,
            UUID bookingId,
            String bookingReference,
            String ticketStatus,
            Instant bookingCreatedAt,
            Instant issuedAt,
            String trainNumber,
            String trainName,
            String coachClassCode,
            java.time.LocalDate journeyDate,
            String originName,
            String destinationName,
            java.time.LocalTime boardingTime,
            java.time.LocalTime arrivalTime,
            java.math.BigDecimal totalPaid,
            String currency,
            String paymentReference,
            String paymentStatus
    ) {
    }
}
