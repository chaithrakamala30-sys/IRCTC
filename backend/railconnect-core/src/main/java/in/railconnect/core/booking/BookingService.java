package in.railconnect.core.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.booking.BookingModels.BookingDetails;
import in.railconnect.core.booking.BookingModels.BookingPassenger;
import in.railconnect.core.booking.BookingModels.BookingStateChange;
import in.railconnect.core.booking.BookingModels.BookingSummary;
import in.railconnect.core.booking.BookingModels.CreateBookingRequest;
import in.railconnect.core.booking.BookingModels.Page;
import in.railconnect.core.fare.FareModels.FareQuoteResponse;
import in.railconnect.core.fare.FareModels.PassengerFare;
import in.railconnect.core.fare.FareService;
import in.railconnect.core.reservation.ReservationModels.PassengerAllocation;
import in.railconnect.core.reservation.ReservationModels.ReservationDetails;
import in.railconnect.core.reservation.ReservationService;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final String AWAITING_PAYMENT = "AWAITING_PAYMENT";
    private static final String EXPIRED = "EXPIRED";
    private static final String ALLOCATION_PENDING = "ALLOCATED_PENDING_BOOKING";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final FareService fareService;
    private final ReservationService reservationService;
    private final BookingRateLimiter bookingRateLimiter;
    private final MeterRegistry meterRegistry;
    private final Duration paymentWindow;

    public BookingService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            FareService fareService,
            ReservationService reservationService,
            BookingRateLimiter bookingRateLimiter,
            MeterRegistry meterRegistry,
            @Value("${railconnect.bookings.payment-window:PT10M}") Duration paymentWindow
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.fareService = fareService;
        this.reservationService = reservationService;
        this.bookingRateLimiter = bookingRateLimiter;
        this.meterRegistry = meterRegistry;
        this.paymentWindow = paymentWindow;
        if (paymentWindow.isZero() || paymentWindow.isNegative()
                || paymentWindow.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException(
                    "railconnect.bookings.payment-window must be greater than zero and at most 24 hours");
        }
    }

    @Transactional
    public BookingDetails create(
            CreateBookingRequest request,
            String idempotencyKey,
            UUID ownerId
    ) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        String fingerprint = fingerprint(request);
        jdbc.update("""
                INSERT INTO booking_idempotency_records(
                    booking_idempotency_record_id, owner_user_id, idempotency_key, request_fingerprint
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT (owner_user_id, idempotency_key) DO NOTHING
                """, UUID.randomUUID(), ownerId, key, fingerprint);
        IdempotencyRecord idempotency = jdbc.query("""
                SELECT request_fingerprint, booking_id
                FROM booking_idempotency_records
                WHERE owner_user_id = ? AND idempotency_key = ?
                FOR UPDATE
                """, result -> result.next()
                ? new IdempotencyRecord(
                        result.getString("request_fingerprint"),
                        result.getObject("booking_id", UUID.class))
                : null, ownerId, key);
        if (idempotency == null) {
            throw new BookingException(HttpStatus.INTERNAL_SERVER_ERROR, "BOOKING_IDEMPOTENCY_UNAVAILABLE",
                    "The booking request could not be recorded safely");
        }
        if (!fingerprint.equals(idempotency.fingerprint())) {
            throw new BookingException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "This idempotency key was already used for a different booking request");
        }
        if (idempotency.bookingId() != null) {
            meterRegistry.counter("railconnect.bookings.create", "result", "replayed").increment();
            return readOwn(idempotency.bookingId(), ownerId);
        }
        if (!bookingRateLimiter.allow(ownerId)) {
            meterRegistry.counter("railconnect.bookings.create", "result", "rate_limited").increment();
            throw new BookingException(HttpStatus.TOO_MANY_REQUESTS, "BOOKING_RATE_LIMITED",
                    "Too many booking attempts were made; wait before trying again");
        }

        ReservationDetails allocation = reservationService.readOwn(request.reservationRequestId(), ownerId);
        if (!ALLOCATION_PENDING.equals(allocation.status())
                || allocation.expiresAt() == null
                || !allocation.expiresAt().isAfter(databaseNow())) {
            throw new BookingException(HttpStatus.CONFLICT, "ALLOCATION_NOT_BOOKABLE",
                    "A live provisional allocation is required to create a booking");
        }

        FareQuoteResponse quote = fareService.quoteForBooking(request.fareQuoteId(), ownerId);
        QuoteIdentity quoteIdentity = loadQuoteIdentity(request.fareQuoteId(), ownerId);
        validateQuoteAndAllocation(quote, quoteIdentity, allocation);
        UUID trainId = loadTrainId(allocation.trainRunId(), allocation.journeyDate(), quote.trainNumber());
        if (!trainId.equals(quoteIdentity.trainId())) {
            throw new BookingException(HttpStatus.UNPROCESSABLE_ENTITY, "FARE_ALLOCATION_MISMATCH",
                    "The fare quote was calculated for a different train");
        }
        Instant createdAt = databaseNow();
        Instant paymentDeadline = min(createdAt.plus(paymentWindow), allocation.expiresAt());
        if (!paymentDeadline.isAfter(createdAt)) {
            throw new BookingException(HttpStatus.CONFLICT, "ALLOCATION_EXPIRED",
                    "The provisional allocation expired before booking creation");
        }

        UUID bookingId = UUID.randomUUID();
        String bookingReference = BookingReferenceGenerator.next();
        String trainSnapshot = json(Map.of(
                "trainNumber", quote.trainNumber(),
                "trainName", quote.trainName(),
                "trainId", trainId,
                "trainRunId", allocation.trainRunId()));
        String journeySnapshot = json(Map.of(
                "journeyDate", allocation.journeyDate(),
                "fromStationId", allocation.fromStationId(),
                "originName", allocation.fromStationName(),
                "toStationId", allocation.toStationId(),
                "destinationName", allocation.toStationName(),
                "coachClassCode", allocation.coachClassCode(),
                "distanceKm", quote.distanceKm(),
                "groupSeatingRequested", allocation.groupSeatingRequested(),
                "groupSeatingResult", allocation.groupSeatingResult()));
        String fareSnapshot = json(quote);
        try {
            jdbc.update("""
                    INSERT INTO bookings(
                        booking_id, booking_reference, owner_user_id, train_run_id, journey_date,
                        from_station_id, to_station_id, coach_class_code, fare_quote_id,
                        reservation_request_id, status, total_payable, currency, payment_deadline,
                        train_snapshot, journey_snapshot, fare_snapshot, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'AWAITING_PAYMENT', ?, ?, ?,
                              CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)
                    """,
                    bookingId, bookingReference, ownerId, allocation.trainRunId(), allocation.journeyDate(),
                    allocation.fromStationId(), allocation.toStationId(), allocation.coachClassCode(),
                    request.fareQuoteId(), request.reservationRequestId(), quote.totalAmount(),
                    quote.currency(), Timestamp.from(paymentDeadline), trainSnapshot, journeySnapshot,
                    fareSnapshot, Timestamp.from(createdAt), Timestamp.from(createdAt));
        } catch (DataIntegrityViolationException exception) {
            throw new BookingException(HttpStatus.CONFLICT, "BOOKING_ALREADY_CREATED",
                    "This fare quote or provisional allocation is already attached to a booking");
        }

        for (int index = 0; index < allocation.passengers().size(); index++) {
            PassengerAllocation allocatedPassenger = allocation.passengers().get(index);
            PassengerFare passengerFare = quote.passengers().get(index);
            if (allocatedPassenger.reservationPassengerId() == null
                    || allocatedPassenger.reservationAllocationId() == null
                    || allocatedPassenger.coachNumber() == null
                    || allocatedPassenger.berthNumber() == null) {
                throw new BookingException(HttpStatus.CONFLICT, "ALLOCATION_INCOMPLETE",
                        "Every passenger must have a complete provisional berth allocation");
            }
            jdbc.update("""
                    INSERT INTO booking_passengers(
                        booking_passenger_id, booking_id, passenger_number, reservation_passenger_id,
                        reservation_allocation_id, reservation_request_id, display_name, coach_number, berth_number,
                        berth_type, fare_snapshot
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                    """,
                    UUID.randomUUID(), bookingId, index + 1,
                    allocatedPassenger.reservationPassengerId(),
                    allocatedPassenger.reservationAllocationId(),
                    request.reservationRequestId(), allocatedPassenger.displayName(), allocatedPassenger.coachNumber(),
                    allocatedPassenger.berthNumber(),
                    berthType(allocation.reservationRequestId(), allocatedPassenger.reservationAllocationId()),
                    json(passengerFare));
        }

        reservationService.attachToBooking(request.reservationRequestId(), ownerId, bookingId);
        int linked = jdbc.update("""
                UPDATE booking_idempotency_records
                SET booking_id = ?
                WHERE owner_user_id = ? AND idempotency_key = ? AND booking_id IS NULL
                """, bookingId, ownerId, key);
        if (linked != 1) {
            throw new BookingException(HttpStatus.CONFLICT, "BOOKING_IDEMPOTENCY_CONFLICT",
                    "The booking request could not be completed safely");
        }
        appendHistory(bookingId, null, AWAITING_PAYMENT, "BOOKING_CREATED");
        appendAudit(bookingId, ownerId, "BOOKING_CREATED",
                Map.of("passengerCount", allocation.passengers().size(), "currency", quote.currency()));
        meterRegistry.counter("railconnect.bookings.create", "result", "success").increment();
        return readOwnWithoutExpiry(bookingId, ownerId);
    }

    @Transactional
    public Page<BookingSummary> history(UUID ownerId, String status, String reference, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<UUID> dueBookings = jdbc.query("""
                SELECT booking_id
                FROM bookings
                WHERE owner_user_id = ? AND status = 'AWAITING_PAYMENT'
                  AND payment_deadline <= CURRENT_TIMESTAMP
                ORDER BY payment_deadline, booking_id
                LIMIT 100
                FOR UPDATE SKIP LOCKED
                """, (result, row) -> result.getObject(1, UUID.class), ownerId);
        dueBookings.forEach(bookingId -> expireLocked(bookingId, ownerId));
        String normalizedStatus = normalizeStatus(status);
        String normalizedReference = reference == null || reference.isBlank()
                ? null : reference.trim().toUpperCase();
        if (normalizedReference != null && normalizedReference.length() > 24) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "BOOKING_REFERENCE_INVALID",
                    "The booking reference is invalid");
        }
        String predicate = " WHERE owner_user_id = ?";
        List<Object> parameters = new ArrayList<>();
        parameters.add(ownerId);
        if (normalizedStatus != null) {
            predicate += " AND status = ?";
            parameters.add(normalizedStatus);
        }
        if (normalizedReference != null) {
            predicate += " AND booking_reference LIKE ?";
            parameters.add("%" + normalizedReference + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM bookings" + predicate, Long.class,
                parameters.toArray());
        parameters.add(safeSize);
        parameters.add((long) safePage * safeSize);
        List<BookingSummary> items = jdbc.query("""
                SELECT booking_id, booking_reference, status, train_snapshot, journey_snapshot,
                       journey_date, coach_class_code, currency, total_payable, payment_deadline,
                       created_at, (SELECT COUNT(*) FROM booking_passengers p
                                   WHERE p.booking_id = bookings.booking_id) AS passenger_count
                FROM bookings
                """ + predicate + """
                ORDER BY created_at DESC, booking_id
                LIMIT ? OFFSET ?
                """,
                (result, row) -> summary(result), parameters.toArray());
        return new Page<>(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional
    public BookingDetails readOwn(UUID bookingId, UUID ownerId) {
        expireIfDue(bookingId, ownerId);
        return readOwnWithoutExpiry(bookingId, ownerId);
    }

    @Transactional
    public BookingDetails readOwnByReference(String reference, UUID ownerId) {
        if (reference == null || !reference.matches("RC-[23456789A-HJ-NP-Z]{12}")) {
            throw notFound();
        }
        UUID bookingId = jdbc.query("""
                SELECT booking_id FROM bookings
                WHERE booking_reference = ? AND owner_user_id = ?
                """, result -> result.next() ? result.getObject(1, UUID.class) : null,
                reference, ownerId);
        if (bookingId == null) {
            throw notFound();
        }
        return readOwn(bookingId, ownerId);
    }

    @Transactional
    public void expireDueBookings() {
        List<UUID> due = jdbc.query("""
                SELECT booking_id
                FROM bookings
                WHERE status = 'AWAITING_PAYMENT' AND payment_deadline <= CURRENT_TIMESTAMP
                ORDER BY payment_deadline, booking_id
                LIMIT 50
                FOR UPDATE SKIP LOCKED
                """, (result, row) -> result.getObject(1, UUID.class));
        for (UUID bookingId : due) {
            expireLocked(bookingId, null);
        }
    }

    private void expireIfDue(UUID bookingId, UUID ownerId) {
        UUID ownedId = jdbc.query("""
                SELECT booking_id FROM bookings
                WHERE booking_id = ? AND owner_user_id = ?
                FOR UPDATE
                """, result -> result.next() ? result.getObject(1, UUID.class) : null, bookingId, ownerId);
        if (ownedId == null) {
            throw notFound();
        }
        expireLocked(bookingId, ownerId);
    }

    private void expireLocked(UUID bookingId, UUID expectedOwnerId) {
        BookingExpiryRow booking = jdbc.query("""
                SELECT owner_user_id, reservation_request_id, status, payment_deadline
                FROM bookings WHERE booking_id = ? FOR UPDATE
                """, result -> result.next()
                ? new BookingExpiryRow(
                        result.getObject("owner_user_id", UUID.class),
                        result.getObject("reservation_request_id", UUID.class),
                        result.getString("status"),
                        result.getTimestamp("payment_deadline").toInstant())
                : null, bookingId);
        if (booking == null || (expectedOwnerId != null && !expectedOwnerId.equals(booking.ownerId()))
                || !AWAITING_PAYMENT.equals(booking.status())
                || booking.paymentDeadline().isAfter(databaseNow())) {
            return;
        }

        ReservationDetails allocation = reservationService.readForBooking(booking.reservationRequestId());
        if (ALLOCATION_PENDING.equals(allocation.status())) {
            reservationService.releaseForBookingFailure(
                    booking.reservationRequestId(), bookingId, "BOOKING_EXPIRED");
        } else if (!"RELEASED".equals(allocation.status()) && !EXPIRED.equals(allocation.status())) {
            meterRegistry.counter("railconnect.bookings.expiration", "result", "allocation_not_released").increment();
            return;
        }

        int updated = jdbc.update("""
                UPDATE bookings SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE booking_id = ? AND status = 'AWAITING_PAYMENT'
                  AND payment_deadline <= CURRENT_TIMESTAMP
                """, bookingId);
        if (updated == 1) {
            appendHistory(bookingId, AWAITING_PAYMENT, EXPIRED, "PAYMENT_DEADLINE_REACHED");
            appendAudit(bookingId, null, "BOOKING_EXPIRED",
                    Map.of("reservationRequestId", booking.reservationRequestId()));
            meterRegistry.counter("railconnect.bookings.expiration", "result", "success").increment();
        }
    }

    private BookingDetails readOwnWithoutExpiry(UUID bookingId, UUID ownerId) {
        BookingHeader header = jdbc.query("""
                SELECT booking_id, booking_reference, status, train_run_id, journey_date,
                       from_station_id, to_station_id, coach_class_code, fare_quote_id,
                       total_payable, currency, payment_deadline, created_at,
                       train_snapshot::text AS train_snapshot,
                       journey_snapshot::text AS journey_snapshot,
                       fare_snapshot::text AS fare_snapshot
                FROM bookings
                WHERE booking_id = ? AND owner_user_id = ?
                """, result -> result.next() ? header(result) : null, bookingId, ownerId);
        if (header == null) {
            throw notFound();
        }
        FareQuoteResponse quote = readQuote(header.fareSnapshot());
        JourneySnapshot journey = readJourney(header.journeySnapshot());
        List<BookingPassenger> passengers = jdbc.query("""
                SELECT passenger_number, display_name, coach_number, berth_number, berth_type,
                       fare_snapshot::text AS fare_snapshot
                FROM booking_passengers
                WHERE booking_id = ?
                ORDER BY passenger_number
                """, (result, row) -> mapPassenger(result), bookingId);
        List<BookingStateChange> history = jdbc.query("""
                SELECT previous_status, new_status, reason_code, occurred_at
                FROM booking_status_history
                WHERE booking_id = ?
                ORDER BY occurred_at, booking_status_history_id
                """, (result, row) -> new BookingStateChange(
                result.getString("previous_status"),
                result.getString("new_status"),
                result.getString("reason_code"),
                result.getTimestamp("occurred_at").toInstant()), bookingId);
        return new BookingDetails(
                header.bookingId(), header.reference(), header.status(), quote.trainNumber(),
                quote.trainName(), header.trainRunId(), header.journeyDate(), header.fromStationId(),
                journey.originName(), header.toStationId(), journey.destinationName(),
                header.coachClassCode(), quote.distanceKm(), journey.groupSeatingResult(),
                header.fareQuoteId(), quote.fareRuleVersionNumber(), header.totalPayable(),
                header.currency(), header.paymentDeadline(), header.createdAt(), passengers, history);
    }

    private void validateQuoteAndAllocation(
            FareQuoteResponse quote,
            QuoteIdentity quoteIdentity,
            ReservationDetails allocation
    ) {
        if (!quote.journeyDate().equals(allocation.journeyDate())
                || !quote.coachClassCode().equalsIgnoreCase(allocation.coachClassCode())
                || quote.passengerCount() != allocation.passengers().size()
                || quote.passengers().size() != allocation.passengers().size()
                || !quoteIdentity.journeyDate().equals(allocation.journeyDate())
                || !quoteIdentity.originStationId().equals(allocation.fromStationId())
                || !quoteIdentity.destinationStationId().equals(allocation.toStationId())
                || !quoteIdentity.coachClassCode().equalsIgnoreCase(allocation.coachClassCode())
                || !quoteIdentity.currency().equals(quote.currency())
                || quoteIdentity.totalAmount().compareTo(quote.totalAmount()) != 0
                || !quoteIdentity.expiresAt().isAfter(databaseNow())) {
            throw new BookingException(HttpStatus.UNPROCESSABLE_ENTITY, "FARE_ALLOCATION_MISMATCH",
                    "The fare quote does not match the allocated journey, class, or passenger count");
        }
        Integer journeyMatches = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_run_stops origin
                JOIN inventory_run_stops destination
                  ON destination.train_run_id = origin.train_run_id
                WHERE origin.train_run_id = ?
                  AND origin.station_id = ?
                  AND destination.station_id = ?
                  AND destination.stop_index > origin.stop_index
                """, Integer.class, allocation.trainRunId(),
                allocation.fromStationId(), allocation.toStationId());
        if (journeyMatches == null || journeyMatches != 1) {
            throw new BookingException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_JOURNEY_INVALID",
                    "The provisional allocation does not match a valid train-run journey");
        }
    }

    private QuoteIdentity loadQuoteIdentity(UUID quoteId, UUID ownerId) {
        QuoteIdentity quote = jdbc.query("""
                SELECT train_id, origin_station_id, destination_station_id, journey_date,
                       coach_class_code, currency, total_amount, expires_at
                FROM fare_quotes
                WHERE fare_quote_id = ? AND owner_user_id = ?
                """, result -> result.next()
                ? new QuoteIdentity(
                        result.getObject("train_id", UUID.class),
                        result.getObject("origin_station_id", UUID.class),
                        result.getObject("destination_station_id", UUID.class),
                        result.getObject("journey_date", LocalDate.class),
                        result.getString("coach_class_code"),
                        result.getString("currency"),
                        result.getBigDecimal("total_amount"),
                        result.getTimestamp("expires_at").toInstant())
                : null, quoteId, ownerId);
        if (quote == null) {
            throw new BookingException(HttpStatus.UNPROCESSABLE_ENTITY, "FARE_QUOTE_INVALID_OR_EXPIRED",
                    "The fare quote is unavailable or does not belong to this passenger");
        }
        return quote;
    }

    private UUID loadTrainId(UUID trainRunId, LocalDate journeyDate, String trainNumber) {
        TrainIdentity identity = jdbc.query("""
                SELECT run.train_id, train.train_number, run.service_date, run.status
                FROM inventory_train_runs run
                JOIN trains train ON train.train_id = run.train_id
                WHERE run.train_run_id = ?
                """, result -> result.next()
                ? new TrainIdentity(
                        result.getObject("train_id", UUID.class),
                        result.getString("train_number"),
                        result.getObject("service_date", LocalDate.class),
                        result.getString("status"))
                : null, trainRunId);
        if (identity == null || !"READY".equals(identity.status())
                || !journeyDate.equals(identity.serviceDate())
                || !trainNumber.equals(identity.trainNumber())) {
            throw new BookingException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_TRAIN_RUN_INVALID",
                    "The fare quote does not match an active train run for this journey date");
        }
        return identity.trainId();
    }

    private String berthType(UUID reservationRequestId, UUID allocationId) {
        String berthType = jdbc.query("""
                SELECT berth.berth_type
                FROM reservation_allocations allocation
                JOIN inventory_berths berth
                  ON berth.train_run_id = allocation.train_run_id
                 AND berth.inventory_berth_id = allocation.inventory_berth_id
                WHERE allocation.reservation_request_id = ?
                  AND allocation.reservation_allocation_id = ?
                  AND allocation.status = 'ALLOCATED_PENDING_BOOKING'
                """, result -> result.next() ? result.getString(1) : null,
                reservationRequestId, allocationId);
        if (berthType == null) {
            throw new BookingException(HttpStatus.CONFLICT, "ALLOCATION_INCOMPLETE",
                    "The allocated berth details are no longer available");
        }
        return berthType;
    }

    private BookingSummary summary(ResultSet result) throws SQLException {
        String trainSnapshot = result.getString("train_snapshot");
        String journeySnapshot = result.getString("journey_snapshot");
        TrainSnapshot train = readTrain(trainSnapshot);
        JourneySnapshot journey = readJourney(journeySnapshot);
        return new BookingSummary(
                result.getObject("booking_id", UUID.class),
                result.getString("booking_reference"),
                result.getString("status"),
                train.trainNumber(),
                train.trainName(),
                result.getObject("journey_date", LocalDate.class),
                journey.originName(),
                journey.destinationName(),
                result.getString("coach_class_code"),
                result.getInt("passenger_count"),
                result.getBigDecimal("total_payable"),
                result.getString("currency"),
                result.getTimestamp("payment_deadline").toInstant(),
                result.getTimestamp("created_at").toInstant());
    }

    private BookingPassenger mapPassenger(ResultSet result) throws SQLException {
        PassengerFare fare = readPassengerFare(result.getString("fare_snapshot"));
        return new BookingPassenger(
                result.getInt("passenger_number"),
                result.getString("display_name"),
                result.getString("coach_number"),
                result.getString("berth_number"),
                result.getString("berth_type"),
                fare.categoryName(),
                fare.items(),
                fare.totalAmount());
    }

    private BookingHeader header(ResultSet result) throws SQLException {
        return new BookingHeader(
                result.getObject("booking_id", UUID.class),
                result.getString("booking_reference"),
                result.getString("status"),
                result.getObject("train_run_id", UUID.class),
                result.getObject("journey_date", LocalDate.class),
                result.getObject("from_station_id", UUID.class),
                result.getObject("to_station_id", UUID.class),
                result.getString("coach_class_code"),
                result.getObject("fare_quote_id", UUID.class),
                result.getBigDecimal("total_payable"),
                result.getString("currency"),
                result.getTimestamp("payment_deadline").toInstant(),
                result.getTimestamp("created_at").toInstant(),
                result.getString("fare_snapshot"),
                result.getString("train_snapshot"),
                result.getString("journey_snapshot"));
    }

    private FareQuoteResponse readQuote(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, FareQuoteResponse.class);
        } catch (JsonProcessingException exception) {
            throw new BookingException(HttpStatus.INTERNAL_SERVER_ERROR, "BOOKING_SNAPSHOT_INVALID",
                    "The stored fare snapshot could not be read");
        }
    }

    private PassengerFare readPassengerFare(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, PassengerFare.class);
        } catch (JsonProcessingException exception) {
            throw new BookingException(HttpStatus.INTERNAL_SERVER_ERROR, "BOOKING_SNAPSHOT_INVALID",
                    "The stored passenger fare snapshot could not be read");
        }
    }

    private TrainSnapshot readTrain(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, TrainSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new BookingException(HttpStatus.INTERNAL_SERVER_ERROR, "BOOKING_SNAPSHOT_INVALID",
                    "The stored train snapshot could not be read");
        }
    }

    private JourneySnapshot readJourney(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, JourneySnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new BookingException(HttpStatus.INTERNAL_SERVER_ERROR, "BOOKING_SNAPSHOT_INVALID",
                    "The stored journey snapshot could not be read");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BookingException(HttpStatus.INTERNAL_SERVER_ERROR, "BOOKING_SNAPSHOT_SERIALIZATION_FAILED",
                    "Booking details could not be stored safely");
        }
    }

    private void appendHistory(UUID bookingId, String previous, String next, String reason) {
        jdbc.update("""
                INSERT INTO booking_status_history(
                    booking_status_history_id, booking_id, previous_status, new_status, reason_code, correlation_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), bookingId, previous, next, reason, correlationId());
    }

    private void appendAudit(UUID bookingId, UUID actor, String event, Map<String, ?> details) {
        jdbc.update("""
                INSERT INTO booking_audit_log(
                    booking_audit_id, booking_id, actor_user_id, event_type, details, correlation_id
                ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
                """, UUID.randomUUID(), bookingId, actor, event, json(details), correlationId());
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 100) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key header is required");
        }
        return key.trim();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase();
        if (!AWAITING_PAYMENT.equals(normalized) && !EXPIRED.equals(normalized)) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "BOOKING_STATUS_INVALID",
                    "The requested booking status filter is invalid");
        }
        return normalized;
    }

    private String fingerprint(CreateBookingRequest request) {
        return sha256(request.fareQuoteId() + "|" + request.reservationRequestId());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private Instant databaseNow() {
        Timestamp now = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        if (now == null) {
            throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_TIME_UNAVAILABLE",
                    "Booking time could not be verified");
        }
        return now.toInstant();
    }

    private Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private String correlationId() {
        String requestId = RequestIds.current();
        return requestId == null || "unavailable".equals(requestId)
                ? UUID.randomUUID().toString() : requestId;
    }

    private BookingException notFound() {
        return new BookingException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking was not found");
    }

    @Scheduled(fixedDelayString = "${railconnect.bookings.expiration-sweep-ms:30000}")
    @Transactional
    public void scheduledExpirySweep() {
        expireDueBookings();
    }

    private record IdempotencyRecord(String fingerprint, UUID bookingId) { }
    private record BookingExpiryRow(UUID ownerId, UUID reservationRequestId, String status, Instant paymentDeadline) { }
    private record TrainIdentity(UUID trainId, String trainNumber, LocalDate serviceDate, String status) { }
    private record QuoteIdentity(
            UUID trainId,
            UUID originStationId,
            UUID destinationStationId,
            LocalDate journeyDate,
            String coachClassCode,
            String currency,
            java.math.BigDecimal totalAmount,
            Instant expiresAt
    ) { }
    private record TrainSnapshot(String trainNumber, String trainName, UUID trainId, UUID trainRunId) { }
    private record JourneySnapshot(
            LocalDate journeyDate,
            UUID fromStationId,
            String originName,
            UUID toStationId,
            String destinationName,
            String coachClassCode,
            java.math.BigDecimal distanceKm,
            boolean groupSeatingRequested,
            String groupSeatingResult
    ) { }
    private record BookingHeader(
            UUID bookingId,
            String reference,
            String status,
            UUID trainRunId,
            LocalDate journeyDate,
            UUID fromStationId,
            UUID toStationId,
            String coachClassCode,
            UUID fareQuoteId,
            java.math.BigDecimal totalPayable,
            String currency,
            Instant paymentDeadline,
            Instant createdAt,
            String fareSnapshot,
            String trainSnapshot,
            String journeySnapshot
    ) { }
}
