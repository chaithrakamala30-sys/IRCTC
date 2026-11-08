package in.railconnect.core.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.inventory.InventoryModels.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryService {
    private static final int HOLD_MINUTES = 10;
    private static final int MAX_HOLD_REQUESTS_PER_MINUTE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;

    public InventoryService(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RunSummary initializeRun(InitializeRunRequest request, UUID actor) {
        advisoryLock("inventory-run:" + request.trainId() + ":" + request.serviceDate());
        List<RunSummary> existing = jdbc.query(
                runSummarySelect() + " WHERE r.train_id = ? AND r.service_date = ?",
                runSummaryMapper(), request.trainId(), request.serviceDate());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        LocalDate serviceDate = request.serviceDate();
        RunSource source = jdbc.query(
                """
                        SELECT t.train_id, t.train_number, t.train_name, s.schedule_id, s.route_version_id,
                               composition.current_version_id AS composition_version_id
                        FROM trains t
                        JOIN train_schedules s ON s.train_id = t.train_id
                        JOIN train_route_versions route ON route.route_version_id = s.route_version_id
                        JOIN train_compositions composition ON composition.train_id = t.train_id
                        JOIN train_composition_versions cv
                          ON cv.composition_version_id = composition.current_version_id
                        LEFT JOIN train_schedule_exceptions exception
                          ON exception.schedule_id = s.schedule_id AND exception.service_date = ?
                        WHERE t.train_id = ?
                          AND t.status = 'ACTIVE'
                          AND s.status = 'PUBLISHED'
                          AND s.effective_from <= ?
                          AND (s.effective_to IS NULL OR s.effective_to >= ?)
                          AND route.status = 'PUBLISHED'
                          AND route.effective_from <= ?
                          AND (route.effective_to IS NULL OR route.effective_to >= ?)
                          AND cv.status = 'PUBLISHED'
                          AND (
                            exception.service_status = 'RUN'
                            OR (exception.service_status IS NULL AND EXISTS (
                                SELECT 1 FROM train_schedule_days operating_day
                                WHERE operating_day.schedule_id = s.schedule_id
                                  AND operating_day.day_of_week = ?
                            ))
                          )
                        ORDER BY s.version_number DESC
                        LIMIT 1
                        """,
                (result, row) -> new RunSource(
                        result.getObject("train_id", UUID.class),
                        result.getString("train_number"),
                        result.getString("train_name"),
                        result.getObject("schedule_id", UUID.class),
                        result.getObject("route_version_id", UUID.class),
                        result.getObject("composition_version_id", UUID.class)
                ),
                serviceDate, request.trainId(), serviceDate, serviceDate, serviceDate, serviceDate,
                serviceDate.getDayOfWeek().getValue()
        ).stream().findFirst().orElseThrow(() -> failure(HttpStatus.CONFLICT,
                "TRAIN_NOT_OPERATING", "No published operating schedule and composition apply on this date"));

        List<Stop> stops = jdbc.query(
                """
                        SELECT rs.route_stop_id, rs.station_id, station.station_code, station.station_name,
                               rs.stop_sequence, rs.arrival_time, rs.departure_time,
                               rs.arrival_day_offset, rs.departure_day_offset, rs.distance_from_source_km
                        FROM train_route_stops rs
                        JOIN stations station ON station.station_id = rs.station_id
                        WHERE rs.route_version_id = ?
                        ORDER BY rs.stop_sequence
                        """,
                (result, row) -> new Stop(
                        row, result.getObject("station_id", UUID.class), result.getString("station_code"),
                        result.getString("station_name"), result.getInt("stop_sequence"),
                        result.getString("arrival_time"), result.getString("departure_time"),
                        result.getInt("arrival_day_offset"), result.getInt("departure_day_offset"),
                        result.getDouble("distance_from_source_km")
                ),
                source.routeVersionId()
        );
        if (stops.size() < 2) {
            throw failure(HttpStatus.CONFLICT, "ROUTE_INCOMPLETE", "Published route requires at least two stops");
        }

        UUID runId = UUID.randomUUID();
        List<CoachSource> coaches = loadCoachSources(source.compositionVersionId());
        if (coaches.isEmpty()) {
            throw failure(HttpStatus.CONFLICT, "COMPOSITION_EMPTY", "Published composition contains no coaches");
        }
        String routeSnapshot = json(stops);
        String compositionSnapshot = json(coaches.stream().map(CoachSource::snapshot).toList());
        jdbc.update(
                """
                        INSERT INTO inventory_train_runs(
                            train_run_id, train_id, service_date, schedule_id, route_version_id,
                            composition_version_id, route_snapshot, composition_snapshot, created_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                        """,
                runId, source.trainId(), serviceDate, source.scheduleId(), source.routeVersionId(),
                source.compositionVersionId(), routeSnapshot, compositionSnapshot, actor
        );

        for (int i = 0; i < stops.size(); i++) {
            Stop stop = stops.get(i);
            RouteStopSource routeStop = jdbc.queryForObject(
                    "SELECT route_stop_id FROM train_route_stops WHERE route_version_id = ? AND stop_sequence = ?",
                    (result, row) -> new RouteStopSource(result.getObject(1, UUID.class)),
                    source.routeVersionId(), stop.stopSequence());
            jdbc.update(
                    """
                            INSERT INTO inventory_run_stops(
                                train_run_id, stop_index, route_stop_id, station_id, station_code, station_name,
                                stop_sequence, arrival_time, departure_time, arrival_day_offset,
                                departure_day_offset, distance_from_source_km
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::time, ?::time, ?, ?, ?)
                            """,
                    runId, i, routeStop.routeStopId(), stop.stationId(), stop.stationCode(), stop.stationName(),
                    stop.stopSequence(), stop.arrivalTime(), stop.departureTime(), stop.arrivalDayOffset(),
                    stop.departureDayOffset(), stop.distanceFromSourceKm()
            );
            if (i < stops.size() - 1) {
                Stop next = stops.get(i + 1);
                double distance = next.distanceFromSourceKm() - stop.distanceFromSourceKm();
                if (distance <= 0) {
                    throw failure(HttpStatus.CONFLICT, "ROUTE_DISTANCE_INVALID", "Route stop distances must increase");
                }
                jdbc.update(
                        """
                                INSERT INTO inventory_run_segments(
                                    train_run_id, segment_index, from_stop_index, to_stop_index,
                                    from_station_id, to_station_id, distance_km
                                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                                """,
                        runId, i, i, i + 1, stop.stationId(), next.stationId(), distance
                );
            }
        }

        for (CoachSource coach : coaches) {
            UUID inventoryCoachId = UUID.randomUUID();
            jdbc.update(
                    """
                            INSERT INTO inventory_coaches(
                                inventory_coach_id, train_run_id, source_coach_id, coach_number, position,
                                configuration_id, coach_class_id, coach_class_code, coach_class_name,
                                coach_type_name, configuration_snapshot
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                            """,
                    inventoryCoachId, runId, coach.coachId(), coach.coachNumber(), coach.position(),
                    coach.configurationId(), coach.coachClassId(), coach.classCode(), coach.className(),
                    coach.typeName(), json(coach.snapshot())
            );
            for (AccommodationSource berth : coach.berths()) {
                jdbc.update(
                        """
                                INSERT INTO inventory_berths(
                                    inventory_berth_id, train_run_id, inventory_coach_id, source_accommodation_id,
                                    berth_number, berth_type, display_order, attributes
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                                """,
                        UUID.randomUUID(), runId, inventoryCoachId, berth.accommodationId(), berth.number(),
                        berth.type(), berth.displayOrder(), berth.attributesJson()
                );
            }
        }

        int readyRuns = jdbc.update(
                "UPDATE inventory_train_runs SET status = 'READY' WHERE train_run_id = ? AND status = 'INITIALIZING'",
                runId);
        if (readyRuns != 1) {
            throw new IllegalStateException("Inventory run could not transition to READY after snapshot creation");
        }
        audit(runId, null, actor, "RUN_INITIALIZED",
                Map.of("trainId", request.trainId(), "serviceDate", serviceDate));
        return runSummary(runId);
    }

    @Transactional(readOnly = true)
    public Page<RunSummary> runs(LocalDate serviceDate, UUID trainId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = boundedPageSize(size);
        Long total = jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM inventory_train_runs
                        WHERE (CAST(? AS date) IS NULL OR service_date = ?)
                          AND (CAST(? AS uuid) IS NULL OR train_id = ?)
                        """,
                Long.class, serviceDate, serviceDate, trainId, trainId);
        List<RunSummary> items = jdbc.query(
                runSummarySelect() + """
                        WHERE (CAST(? AS date) IS NULL OR r.service_date = ?)
                          AND (CAST(? AS uuid) IS NULL OR r.train_id = ?)
                        """ + " "
                        + "ORDER BY r.service_date DESC, r.train_id LIMIT ? OFFSET ?",
                runSummaryMapper(), serviceDate, serviceDate, trainId, trainId,
                safeSize, (long) safePage * safeSize);
        return new Page<>(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public List<TrainOption> initializableTrains() {
        return jdbc.query(
                """
                        SELECT train.train_id, train.train_number, train.train_name
                        FROM trains train
                        WHERE train.status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1 FROM train_schedules schedule
                              JOIN train_route_versions route ON route.route_version_id = schedule.route_version_id
                              WHERE schedule.train_id = train.train_id
                                AND schedule.status = 'PUBLISHED'
                                AND route.status = 'PUBLISHED'
                          )
                          AND EXISTS (
                              SELECT 1 FROM train_compositions composition
                              JOIN train_composition_versions version
                                ON version.composition_version_id = composition.current_version_id
                              WHERE composition.train_id = train.train_id AND version.status = 'PUBLISHED'
                          )
                        ORDER BY train.train_number
                        LIMIT 500
                        """,
                (result, row) -> new TrainOption(
                        result.getObject("train_id", UUID.class),
                        result.getString("train_number"),
                        result.getString("train_name")
                ));
    }

    @Transactional(readOnly = true)
    public RunDetails runDetails(UUID runId) {
        RunSummary summary = runSummary(runId);
        List<Stop> stops = jdbc.query(
                """
                        SELECT stop_index, station_id, station_code, station_name, stop_sequence,
                               arrival_time, departure_time, arrival_day_offset, departure_day_offset,
                               distance_from_source_km
                        FROM inventory_run_stops WHERE train_run_id = ? ORDER BY stop_index
                        """,
                (result, row) -> new Stop(
                        result.getInt("stop_index"), result.getObject("station_id", UUID.class),
                        result.getString("station_code"), result.getString("station_name"),
                        result.getInt("stop_sequence"), result.getString("arrival_time"),
                        result.getString("departure_time"), result.getInt("arrival_day_offset"),
                        result.getInt("departure_day_offset"), result.getDouble("distance_from_source_km")
                ), runId);
        return new RunDetails(summary, stops);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(JourneyRequest request) {
        Journey journey = journey(request.trainRunId(), request.fromStationId(), request.toStationId());
        List<ClassAvailability> classes = namedJdbc.query(
                """
                        WITH berth_state AS (
                            SELECT berth.inventory_berth_id, coach.coach_class_code, coach.coach_class_name,
                                   berth.base_status,
                                   EXISTS (
                                       SELECT 1 FROM inventory_segment_claims claim
                                       JOIN inventory_blocks block ON block.inventory_block_id = claim.inventory_block_id
                                       WHERE claim.inventory_berth_id = berth.inventory_berth_id
                                         AND claim.train_run_id = :runId
                                         AND claim.segment_index >= :fromIndex AND claim.segment_index < :toIndex
                                         AND block.status = 'ACTIVE'
                                   ) AS blocked,
                                   EXISTS (
                                       SELECT 1 FROM inventory_segment_claims claim
                                       JOIN inventory_holds hold ON hold.inventory_hold_id = claim.inventory_hold_id
                                       WHERE claim.inventory_berth_id = berth.inventory_berth_id
                                         AND claim.train_run_id = :runId
                                         AND claim.segment_index >= :fromIndex AND claim.segment_index < :toIndex
                                         AND hold.status = 'ACTIVE' AND hold.expires_at > CURRENT_TIMESTAMP
                                   ) AS held
                            FROM inventory_berths berth
                            JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                            WHERE berth.train_run_id = :runId
                              AND berth.base_status = 'AVAILABLE'
                              AND (:classCode IS NULL OR coach.coach_class_code = :classCode)
                        )
                        SELECT coach_class_code, coach_class_name, COUNT(*) AS total,
                               COUNT(*) FILTER (WHERE base_status = 'AVAILABLE' AND NOT blocked AND NOT held) AS available,
                               COUNT(*) FILTER (WHERE base_status = 'AVAILABLE' AND NOT blocked AND held) AS held,
                               COUNT(*) FILTER (WHERE base_status = 'AVAILABLE' AND blocked) AS blocked,
                               COUNT(*) FILTER (WHERE base_status = 'UNAVAILABLE') AS unavailable
                        FROM berth_state
                        GROUP BY coach_class_code, coach_class_name
                        ORDER BY coach_class_code
                        """,
                new MapSqlParameterSource()
                        .addValue("runId", request.trainRunId())
                        .addValue("fromIndex", journey.fromIndex())
                        .addValue("toIndex", journey.toIndex())
                        .addValue("classCode", blankToNull(request.coachClassCode())),
                (result, row) -> {
                    int available = result.getInt("available");
                    int total = result.getInt("total");
                    return new ClassAvailability(
                            result.getString("coach_class_code"), result.getString("coach_class_name"),
                            total, available, result.getInt("held"), result.getInt("blocked"),
                            result.getInt("unavailable"),
                            available > 0 ? (available == total ? "AVAILABLE" : "LIMITED") : "SOLD_OUT"
                    );
                });
        return new AvailabilityResponse(
                journey.runId(), journey.serviceDate(), journey.fromStationId(), journey.fromName(),
                journey.toStationId(), journey.toName(), journey.fromIndex(), journey.toIndex(),
                journey.distanceKm(), classes);
    }

    @Transactional(readOnly = true)
    public BerthPage berths(JourneyRequest request, int page, int size) {
        Journey journey = journey(request.trainRunId(), request.fromStationId(), request.toStationId());
        int safePage = Math.max(0, page);
        int safeSize = boundedPageSize(size);
        MapSqlParameterSource params = journeyParams(journey)
                .addValue("classCode", blankToNull(request.coachClassCode()))
                .addValue("limit", safeSize)
                .addValue("offset", (long) safePage * safeSize);
        String from = """
                FROM inventory_berths berth
                JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                WHERE berth.train_run_id = :runId
                  AND (:classCode IS NULL OR coach.coach_class_code = :classCode)
                  AND NOT EXISTS (
                      SELECT 1 FROM inventory_segment_claims claim
                      JOIN inventory_blocks block ON block.inventory_block_id = claim.inventory_block_id
                      WHERE claim.inventory_berth_id = berth.inventory_berth_id
                        AND claim.segment_index >= :fromIndex AND claim.segment_index < :toIndex
                        AND block.status = 'ACTIVE'
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM inventory_segment_claims claim
                      JOIN inventory_holds hold ON hold.inventory_hold_id = claim.inventory_hold_id
                      WHERE claim.inventory_berth_id = berth.inventory_berth_id
                        AND claim.segment_index >= :fromIndex AND claim.segment_index < :toIndex
                        AND hold.status = 'ACTIVE' AND hold.expires_at > CURRENT_TIMESTAMP
                  )
                """;
        Long total = namedJdbc.queryForObject("SELECT COUNT(*) " + from, params, Long.class);
        List<Berth> items = namedJdbc.query(
                """
                        SELECT berth.inventory_berth_id, coach.coach_number, coach.position,
                               coach.coach_class_code, coach.coach_class_name, berth.berth_number,
                               berth.berth_type, berth.display_order, berth.base_status
                        """ + from + " ORDER BY coach.position, berth.display_order LIMIT :limit OFFSET :offset",
                params,
                (result, row) -> new Berth(
                        result.getObject("inventory_berth_id", UUID.class), result.getString("coach_number"),
                        result.getInt("position"), result.getString("coach_class_code"),
                        result.getString("coach_class_name"), result.getString("berth_number"),
                        result.getString("berth_type"), result.getInt("display_order"),
                        result.getString("base_status")
                ));
        return new BerthPage(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional
    public HoldResponse createHold(HoldRequest request, String idempotencyKey, UUID owner) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw failure(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key header is required");
        }
        advisoryLock("inventory-hold:" + owner);
        advisoryLock("inventory-run-claims:" + request.trainRunId());
        expireStaleHoldsForRun(request.trainRunId());
        Journey journey = journey(request.trainRunId(), request.fromStationId(), request.toStationId());
        String fingerprint = fingerprint(request);
        List<HoldResponse> previous = jdbc.query(
                """
                        SELECT hold.inventory_hold_id, hold.train_run_id, hold.inventory_berth_id,
                               coach.coach_number, coach.coach_class_code, berth.berth_number,
                               from_stop.station_id AS from_station_id, from_stop.station_name AS from_station_name,
                               to_stop.station_id AS to_station_id, to_stop.station_name AS to_station_name,
                               hold.status, hold.created_at, hold.expires_at, hold.request_fingerprint
                        FROM inventory_holds hold
                        JOIN inventory_berths berth ON berth.inventory_berth_id = hold.inventory_berth_id
                        JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                        JOIN inventory_run_stops from_stop
                          ON from_stop.train_run_id = hold.train_run_id AND from_stop.stop_index = hold.from_stop_index
                        JOIN inventory_run_stops to_stop
                          ON to_stop.train_run_id = hold.train_run_id AND to_stop.stop_index = hold.to_stop_index
                        WHERE hold.owner_user_id = ? AND hold.idempotency_key = ?
                        """,
                holdMapperWithFingerprint(), owner, idempotencyKey);
        if (!previous.isEmpty()) {
            HoldResponse prior = previous.get(0);
            String priorFingerprint = jdbc.queryForObject(
                    "SELECT request_fingerprint FROM inventory_holds WHERE inventory_hold_id = ?",
                    String.class, prior.inventoryHoldId());
            if (!fingerprint.equals(priorFingerprint)) {
                throw failure(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "This idempotency key was already used for a different request");
            }
            return prior;
        }
        enforceHoldRateLimit(owner);
        Integer maximum = jdbc.queryForObject(
                "SELECT configuration_value FROM inventory_configuration WHERE configuration_key = ?",
                Integer.class, "MAX_ACTIVE_HOLDS_PER_PASSENGER");
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_holds WHERE owner_user_id = ? AND status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP",
                Integer.class, owner);
        if (active != null && maximum != null && active >= maximum) {
            throw failure(HttpStatus.TOO_MANY_REQUESTS, "ACTIVE_HOLD_LIMIT",
                    "Release or wait for an existing hold before requesting another");
        }

        String classCode = request.coachClassCode().trim().toUpperCase();
        String preferredType = request.preferredBerthType() == null
                ? null : request.preferredBerthType().trim().toUpperCase();
        List<UUID> candidates = jdbc.query(
                """
                        SELECT berth.inventory_berth_id
                        FROM inventory_berths berth
                        JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                        WHERE berth.train_run_id = ?
                          AND coach.coach_class_code = ?
                          AND berth.base_status = 'AVAILABLE'
                          AND (CAST(? AS varchar) IS NULL OR berth.berth_type = ?)
                          AND NOT EXISTS (
                              SELECT 1 FROM inventory_segment_claims claim
                              WHERE claim.inventory_berth_id = berth.inventory_berth_id
                                AND claim.segment_index >= ? AND claim.segment_index < ?
                          )
                        ORDER BY coach.position, berth.display_order
                        LIMIT 1
                        """,
                (result, row) -> result.getObject(1, UUID.class),
                request.trainRunId(), classCode, preferredType, preferredType,
                journey.fromIndex(), journey.toIndex());
        if (candidates.isEmpty()) {
            throw failure(HttpStatus.CONFLICT, "NO_BERTH_AVAILABLE",
                    "No berth is available for the selected class and journey");
        }

        UUID holdId = UUID.randomUUID();
        UUID berthId = candidates.get(0);
        try {
            jdbc.update(
                    """
                            INSERT INTO inventory_holds(
                                inventory_hold_id, train_run_id, inventory_berth_id, owner_user_id,
                                from_stop_index, to_stop_index, idempotency_key, request_fingerprint, expires_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP + (? * INTERVAL '1 minute'))
                            """,
                    holdId, request.trainRunId(), berthId, owner, journey.fromIndex(), journey.toIndex(),
                    idempotencyKey, fingerprint, HOLD_MINUTES);
            insertHoldClaims(holdId, request.trainRunId(), berthId, journey.fromIndex(), journey.toIndex());
        } catch (DataIntegrityViolationException exception) {
            if (!isSegmentClaimConflict(exception)) {
                throw exception;
            }
            throw failure(HttpStatus.CONFLICT, "BERTH_NO_LONGER_AVAILABLE",
                    "The berth was claimed by another request; please refresh availability");
        }
        audit(request.trainRunId(), berthId, owner, "HOLD_CREATED",
                Map.of("holdId", holdId, "fromStopIndex", journey.fromIndex(), "toStopIndex", journey.toIndex()));
        return hold(holdId, owner);
    }

    @Transactional
    public List<HoldResponse> holds(UUID owner) {
        expireStaleHolds(500);
        return jdbc.query(
                """
                        SELECT hold.inventory_hold_id, hold.train_run_id, hold.inventory_berth_id,
                               coach.coach_number, coach.coach_class_code, berth.berth_number,
                               from_stop.station_id AS from_station_id, from_stop.station_name AS from_station_name,
                               to_stop.station_id AS to_station_id, to_stop.station_name AS to_station_name,
                               hold.status, hold.created_at, hold.expires_at
                        FROM inventory_holds hold
                        JOIN inventory_berths berth ON berth.inventory_berth_id = hold.inventory_berth_id
                        JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                        JOIN inventory_run_stops from_stop
                          ON from_stop.train_run_id = hold.train_run_id AND from_stop.stop_index = hold.from_stop_index
                        JOIN inventory_run_stops to_stop
                          ON to_stop.train_run_id = hold.train_run_id AND to_stop.stop_index = hold.to_stop_index
                        WHERE hold.owner_user_id = ?
                        ORDER BY hold.created_at DESC
                        LIMIT 100
                        """,
                holdMapper(), owner);
    }

    @Transactional
    public HoldResponse releaseHold(UUID holdId, UUID owner) {
        List<HoldResponse> owned = jdbc.query(
                """
                        SELECT hold.inventory_hold_id, hold.train_run_id, hold.inventory_berth_id,
                               coach.coach_number, coach.coach_class_code, berth.berth_number,
                               from_stop.station_id AS from_station_id, from_stop.station_name AS from_station_name,
                               to_stop.station_id AS to_station_id, to_stop.station_name AS to_station_name,
                               hold.status, hold.created_at, hold.expires_at
                        FROM inventory_holds hold
                        JOIN inventory_berths berth ON berth.inventory_berth_id = hold.inventory_berth_id
                        JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                        JOIN inventory_run_stops from_stop
                          ON from_stop.train_run_id = hold.train_run_id AND from_stop.stop_index = hold.from_stop_index
                        JOIN inventory_run_stops to_stop
                          ON to_stop.train_run_id = hold.train_run_id AND to_stop.stop_index = hold.to_stop_index
                        WHERE hold.inventory_hold_id = ? AND hold.owner_user_id = ?
                        FOR UPDATE OF hold
                        """,
                holdMapper(), holdId, owner);
        if (owned.isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "Hold not found");
        }
        HoldResponse before = owned.get(0);
        if ("ACTIVE".equals(before.status())) {
            jdbc.update("DELETE FROM inventory_segment_claims WHERE inventory_hold_id = ?", holdId);
            jdbc.update("""
                    UPDATE inventory_holds SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP,
                                                updated_at = CURRENT_TIMESTAMP
                    WHERE inventory_hold_id = ? AND status = 'ACTIVE'
                    """, holdId);
            audit(before.trainRunId(), before.inventoryBerthId(), owner, "HOLD_RELEASED",
                    Map.of("holdId", holdId));
        }
        return hold(holdId, owner);
    }

    @Transactional
    public BlockResponse createBlock(UUID runId, BlockRequest request, UUID actor) {
        advisoryLock("inventory-run-claims:" + runId);
        expireStaleHoldsForRun(runId);
        Journey journey = journeyForBerth(runId, request.fromStationId(), request.toStationId(),
                request.inventoryBerthId());
        UUID blockId = UUID.randomUUID();
        String blockType = request.blockType().trim().toUpperCase();
        try {
            jdbc.update(
                    """
                            INSERT INTO inventory_blocks(
                                inventory_block_id, train_run_id, inventory_berth_id, from_stop_index,
                                to_stop_index, block_type, reason, created_by
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    blockId, runId, request.inventoryBerthId(), journey.fromIndex(), journey.toIndex(),
                    blockType, request.reason().trim(), actor);
            insertBlockClaims(blockId, runId, request.inventoryBerthId(),
                    journey.fromIndex(), journey.toIndex());
        } catch (DataIntegrityViolationException exception) {
            if (!isSegmentClaimConflict(exception)) {
                throw exception;
            }
            throw failure(HttpStatus.CONFLICT, "BERTH_IN_USE",
                    "The berth has an active hold or block for one or more requested segments");
        }
        audit(runId, request.inventoryBerthId(), actor, "BERTH_BLOCKED",
                Map.of("blockId", blockId, "blockType", blockType, "reason", request.reason().trim()));
        return block(blockId);
    }

    @Transactional
    public BlockResponse releaseBlock(UUID blockId, UUID actor) {
        List<BlockResponse> blocks = jdbc.query(
                """
                        SELECT block.inventory_block_id, block.train_run_id, block.inventory_berth_id,
                               coach.coach_number, berth.berth_number, block.block_type, block.reason,
                               block.status, block.created_at
                        FROM inventory_blocks block
                        JOIN inventory_berths berth ON berth.inventory_berth_id = block.inventory_berth_id
                        JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                        WHERE block.inventory_block_id = ?
                        FOR UPDATE OF block
                        """,
                blockMapper(), blockId);
        if (blocks.isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "BLOCK_NOT_FOUND", "Inventory block not found");
        }
        BlockResponse current = blocks.get(0);
        if ("ACTIVE".equals(current.status())) {
            jdbc.update("DELETE FROM inventory_segment_claims WHERE inventory_block_id = ?", blockId);
            jdbc.update("""
                    UPDATE inventory_blocks SET status = 'RELEASED', released_by = ?, released_at = CURRENT_TIMESTAMP
                    WHERE inventory_block_id = ? AND status = 'ACTIVE'
                    """, actor, blockId);
            audit(current.trainRunId(), current.inventoryBerthId(), actor, "BERTH_UNBLOCKED",
                    Map.of("blockId", blockId));
        }
        return block(blockId);
    }

    @Transactional(readOnly = true)
    public Page<BlockResponse> blocks(UUID runId, int page, int size) {
        runSummary(runId);
        int safePage = Math.max(0, page);
        int safeSize = boundedPageSize(size);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_blocks WHERE train_run_id = ?", Long.class, runId);
        List<BlockResponse> items = jdbc.query(
                """
                        SELECT block.inventory_block_id, block.train_run_id, block.inventory_berth_id,
                               coach.coach_number, berth.berth_number, block.block_type, block.reason,
                               block.status, block.created_at
                        FROM inventory_blocks block
                        JOIN inventory_berths berth ON berth.inventory_berth_id = block.inventory_berth_id
                        JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                        WHERE block.train_run_id = ?
                        ORDER BY block.created_at DESC LIMIT ? OFFSET ?
                        """,
                blockMapper(), runId, safeSize, (long) safePage * safeSize);
        return new Page<>(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> audit(UUID runId, int limit) {
        runSummary(runId);
        return jdbc.query(
                """
                        SELECT inventory_audit_id, train_run_id, inventory_berth_id, actor.full_name AS actor_name,
                               event_type, details::text, occurred_at, correlation_id
                        FROM inventory_audit_log audit
                        LEFT JOIN users actor ON actor.user_id = audit.actor_user_id
                        WHERE train_run_id = ?
                        ORDER BY occurred_at DESC LIMIT ?
                        """,
                (result, row) -> new AuditEvent(
                        result.getObject("inventory_audit_id", UUID.class),
                        result.getObject("train_run_id", UUID.class),
                        result.getObject("inventory_berth_id", UUID.class),
                        result.getString("actor_name"),
                        result.getString("event_type"), result.getString("details"),
                        result.getTimestamp("occurred_at").toInstant(), result.getString("correlation_id")
                ), runId, Math.max(1, Math.min(limit, 200)));
    }

    @Scheduled(fixedDelayString = "${railconnect.inventory.expiry-sweep-ms:30000}")
    @Transactional
    public void expireHoldsScheduled() {
        expireStaleHolds(500);
    }

    private int expireStaleHolds(int limit) {
        List<ExpiredHold> expired = jdbc.query(
                """
                        SELECT inventory_hold_id, train_run_id, inventory_berth_id, owner_user_id
                        FROM inventory_holds
                        WHERE status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP
                        ORDER BY expires_at
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """,
                (result, row) -> new ExpiredHold(
                        result.getObject("inventory_hold_id", UUID.class),
                        result.getObject("train_run_id", UUID.class),
                        result.getObject("inventory_berth_id", UUID.class),
                        result.getObject("owner_user_id", UUID.class)
                ), limit);
        for (ExpiredHold hold : expired) {
            jdbc.update("DELETE FROM inventory_segment_claims WHERE inventory_hold_id = ?", hold.holdId());
            jdbc.update("""
                    UPDATE inventory_holds SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                    WHERE inventory_hold_id = ? AND status = 'ACTIVE'
                    """, hold.holdId());
            audit(hold.runId(), hold.berthId(), null, "HOLD_EXPIRED", Map.of("holdId", hold.holdId()));
        }
        return expired.size();
    }

    private int expireStaleHoldsForRun(UUID runId) {
        List<ExpiredHold> expired = jdbc.query(
                """
                        SELECT inventory_hold_id, train_run_id, inventory_berth_id, owner_user_id
                        FROM inventory_holds
                        WHERE train_run_id = ? AND status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP
                        ORDER BY expires_at
                        FOR UPDATE
                        """,
                (result, row) -> new ExpiredHold(
                        result.getObject("inventory_hold_id", UUID.class),
                        result.getObject("train_run_id", UUID.class),
                        result.getObject("inventory_berth_id", UUID.class),
                        result.getObject("owner_user_id", UUID.class)
                ), runId);
        for (ExpiredHold hold : expired) {
            jdbc.update("DELETE FROM inventory_segment_claims WHERE inventory_hold_id = ?", hold.holdId());
            jdbc.update("""
                    UPDATE inventory_holds SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                    WHERE inventory_hold_id = ? AND status = 'ACTIVE'
                    """, hold.holdId());
            audit(hold.runId(), hold.berthId(), null, "HOLD_EXPIRED", Map.of("holdId", hold.holdId()));
        }
        return expired.size();
    }

    private void enforceHoldRateLimit(UUID owner) {
        jdbc.update(
                """
                        INSERT INTO inventory_hold_rate_limits(owner_user_id, window_started_at, request_count)
                        VALUES (?, CURRENT_TIMESTAMP, 1)
                        ON CONFLICT (owner_user_id) DO UPDATE SET
                            window_started_at = CASE
                                WHEN inventory_hold_rate_limits.window_started_at <= CURRENT_TIMESTAMP - INTERVAL '1 minute'
                                THEN CURRENT_TIMESTAMP ELSE inventory_hold_rate_limits.window_started_at END,
                            request_count = CASE
                                WHEN inventory_hold_rate_limits.window_started_at <= CURRENT_TIMESTAMP - INTERVAL '1 minute'
                                THEN 1 ELSE inventory_hold_rate_limits.request_count + 1 END
                        """, owner);
        Integer count = jdbc.queryForObject(
                "SELECT request_count FROM inventory_hold_rate_limits WHERE owner_user_id = ?",
                Integer.class, owner);
        if (count != null && count > MAX_HOLD_REQUESTS_PER_MINUTE) {
            throw failure(HttpStatus.TOO_MANY_REQUESTS, "HOLD_RATE_LIMIT",
                    "Too many hold requests; wait before trying again");
        }
    }

    private void advisoryLock(String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", result -> {
            if (result.next()) {
                result.getObject(1);
            }
            return null;
        }, key);
    }

    private void insertHoldClaims(UUID holdId, UUID runId, UUID berthId, int fromIndex, int toIndex) {
        jdbc.update(
                """
                        INSERT INTO inventory_hold_segments(inventory_hold_id, train_run_id, inventory_berth_id, segment_index)
                        SELECT ?, ?, ?, segment_index FROM inventory_run_segments
                        WHERE train_run_id = ? AND segment_index >= ? AND segment_index < ?
                        """,
                holdId, runId, berthId, runId, fromIndex, toIndex);
        jdbc.update(
                """
                        INSERT INTO inventory_segment_claims(
                            train_run_id, inventory_berth_id, segment_index, inventory_hold_id
                        )
                        SELECT train_run_id, inventory_berth_id, segment_index, inventory_hold_id
                        FROM inventory_hold_segments WHERE inventory_hold_id = ?
                        """,
                holdId);
    }

    private void insertBlockClaims(UUID blockId, UUID runId, UUID berthId, int fromIndex, int toIndex) {
        jdbc.update(
                """
                        INSERT INTO inventory_block_segments(inventory_block_id, train_run_id, inventory_berth_id, segment_index)
                        SELECT ?, ?, ?, segment_index FROM inventory_run_segments
                        WHERE train_run_id = ? AND segment_index >= ? AND segment_index < ?
                        """,
                blockId, runId, berthId, runId, fromIndex, toIndex);
        jdbc.update(
                """
                        INSERT INTO inventory_segment_claims(
                            train_run_id, inventory_berth_id, segment_index, inventory_block_id
                        )
                        SELECT train_run_id, inventory_berth_id, segment_index, inventory_block_id
                        FROM inventory_block_segments WHERE inventory_block_id = ?
                        """,
                blockId);
    }

    private Journey journey(UUID runId, UUID fromStationId, UUID toStationId) {
        RunSummary summary = runSummary(runId);
        if (!"READY".equals(summary.status())) {
            throw failure(HttpStatus.CONFLICT, "TRAIN_RUN_UNAVAILABLE", "Train run is not available");
        }
        List<JourneyStop> stops = jdbc.query(
                """
                        SELECT stop_index, station_id, station_name, distance_from_source_km
                        FROM inventory_run_stops
                        WHERE train_run_id = ? AND station_id IN (?, ?)
                        """,
                (result, row) -> new JourneyStop(
                        result.getInt("stop_index"), result.getObject("station_id", UUID.class),
                        result.getString("station_name"), result.getDouble("distance_from_source_km")
                ), runId, fromStationId, toStationId);
        JourneyStop from = stops.stream().filter(stop -> stop.stationId().equals(fromStationId)).findFirst()
                .orElseThrow(() -> failure(HttpStatus.BAD_REQUEST, "ORIGIN_NOT_ON_ROUTE",
                        "Origin station is not on this train run"));
        JourneyStop to = stops.stream().filter(stop -> stop.stationId().equals(toStationId)).findFirst()
                .orElseThrow(() -> failure(HttpStatus.BAD_REQUEST, "DESTINATION_NOT_ON_ROUTE",
                        "Destination station is not on this train run"));
        if (from.index() >= to.index()) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_JOURNEY_ORDER",
                    "Origin must precede destination on the train route");
        }
        return new Journey(runId, summary.serviceDate(), fromStationId, from.name(), toStationId, to.name(),
                from.index(), to.index(), to.distance() - from.distance());
    }

    private Journey journeyForBerth(UUID runId, UUID from, UUID to, UUID berthId) {
        Integer found = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_berths WHERE train_run_id = ? AND inventory_berth_id = ?",
                Integer.class, runId, berthId);
        if (found == null || found != 1) {
            throw failure(HttpStatus.NOT_FOUND, "BERTH_NOT_FOUND", "Berth not found on this train run");
        }
        return journey(runId, from, to);
    }

    private List<CoachSource> loadCoachSources(UUID compositionVersionId) {
        List<CoachSeed> seeds = jdbc.query(
                """
                        SELECT composition_coach.coach_id, composition_coach.coach_number,
                               composition_coach.position, composition_coach.configuration_snapshot::text,
                               (composition_coach.configuration_snapshot->>'configurationId')::uuid AS configuration_id,
                               configuration.coach_class_id, coach_class.class_code, coach_class.class_name,
                               coach_type.type_name
                        FROM train_composition_coaches composition_coach
                        JOIN coach_configurations configuration
                          ON configuration.configuration_id =
                             (composition_coach.configuration_snapshot->>'configurationId')::uuid
                        JOIN coach_classes coach_class ON coach_class.coach_class_id = configuration.coach_class_id
                        JOIN coach_types coach_type ON coach_type.coach_type_id = configuration.coach_type_id
                        WHERE composition_coach.composition_version_id = ?
                        ORDER BY composition_coach.position
                        """,
                (result, row) -> new CoachSeed(
                        result.getObject("coach_id", UUID.class), result.getString("coach_number"),
                        result.getInt("position"), result.getString("configuration_snapshot"),
                        result.getObject("configuration_id", UUID.class),
                        result.getObject("coach_class_id", UUID.class), result.getString("class_code"),
                        result.getString("class_name"), result.getString("type_name")
                ), compositionVersionId);
        List<CoachSource> coaches = new ArrayList<>();
        for (CoachSeed seed : seeds) {
            UUID layoutId = jdbc.query(
                    """
                            SELECT layout_id FROM accommodation_layouts
                            WHERE configuration_id = ? AND status = 'PUBLISHED'
                            ORDER BY version_number DESC LIMIT 1
                            """,
                    (result, row) -> result.getObject(1, UUID.class), seed.configurationId())
                    .stream().findFirst().orElseThrow(() -> failure(HttpStatus.CONFLICT,
                            "PUBLISHED_LAYOUT_MISSING", "A coach configuration has no published physical layout"));
            List<AccommodationSource> berths = jdbc.query(
                    """
                            SELECT accommodation_id, accommodation_number, accommodation_type,
                                   display_order, position::text, deck_level
                            FROM physical_accommodations
                            WHERE layout_id = ? AND active = TRUE
                            ORDER BY display_order
                            """,
                    (result, row) -> new AccommodationSource(
                            result.getObject("accommodation_id", UUID.class),
                            result.getString("accommodation_number"),
                            result.getString("accommodation_type"),
                            result.getInt("display_order"),
                            attributes(result.getString("position"), (Integer) result.getObject("deck_level"))
                    ), layoutId);
            if (berths.isEmpty()) {
                throw failure(HttpStatus.CONFLICT, "PUBLISHED_LAYOUT_EMPTY",
                        "A published coach layout contains no active berths");
            }
            Map<String, Object> snapshot = Map.of(
                    "configurationId", seed.configurationId(),
                    "configuration", parseJson(seed.configurationSnapshot()),
                    "layoutId", layoutId,
                    "accommodationCount", berths.size(),
                    "coachClassCode", seed.classCode(),
                    "coachClassName", seed.className(),
                    "coachTypeName", seed.typeName()
            );
            coaches.add(new CoachSource(seed.coachId(), seed.coachNumber(), seed.position(),
                    seed.configurationId(), seed.coachClassId(), seed.classCode(), seed.className(),
                    seed.typeName(), snapshot, berths));
        }
        return coaches;
    }

    private Object parseJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted coach configuration snapshot is invalid JSON", exception);
        }
    }

    private String attributes(String positionJson, Integer deckLevel) {
        try {
            Map<String, Object> position = objectMapper.readValue(positionJson, objectMapper.getTypeFactory()
                    .constructMapType(Map.class, String.class, Object.class));
            if (deckLevel != null) {
                position.put("deckLevel", deckLevel);
            }
            return json(position);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted accommodation position is invalid JSON", exception);
        }
    }

    private HoldResponse hold(UUID holdId, UUID owner) {
        return jdbc.query(
                """
                        SELECT hold.inventory_hold_id, hold.train_run_id, hold.inventory_berth_id,
                               coach.coach_number, coach.coach_class_code, berth.berth_number,
                               from_stop.station_id AS from_station_id, from_stop.station_name AS from_station_name,
                               to_stop.station_id AS to_station_id, to_stop.station_name AS to_station_name,
                               hold.status, hold.created_at, hold.expires_at
                        FROM inventory_holds hold
                        JOIN inventory_berths berth ON berth.inventory_berth_id = hold.inventory_berth_id
                        JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                        JOIN inventory_run_stops from_stop
                          ON from_stop.train_run_id = hold.train_run_id AND from_stop.stop_index = hold.from_stop_index
                        JOIN inventory_run_stops to_stop
                          ON to_stop.train_run_id = hold.train_run_id AND to_stop.stop_index = hold.to_stop_index
                        WHERE hold.inventory_hold_id = ? AND hold.owner_user_id = ?
                        """,
                holdMapper(), holdId, owner).stream().findFirst()
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "Hold not found"));
    }

    private BlockResponse block(UUID blockId) {
        return jdbc.query(
                """
                        SELECT block.inventory_block_id, block.train_run_id, block.inventory_berth_id,
                               coach.coach_number, berth.berth_number, block.block_type, block.reason,
                               block.status, block.created_at
                        FROM inventory_blocks block
                        JOIN inventory_berths berth ON berth.inventory_berth_id = block.inventory_berth_id
                        JOIN inventory_coaches coach ON coach.inventory_coach_id = berth.inventory_coach_id
                        WHERE block.inventory_block_id = ?
                        """,
                blockMapper(), blockId).stream().findFirst()
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "BLOCK_NOT_FOUND", "Inventory block not found"));
    }

    private RunSummary runSummary(UUID runId) {
        return jdbc.query(runSummarySelect() + " WHERE r.train_run_id = ?", runSummaryMapper(), runId)
                .stream().findFirst()
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "TRAIN_RUN_NOT_FOUND", "Train run not found"));
    }

    private String runSummarySelect() {
        return """
                SELECT r.train_run_id, r.train_id, train.train_number, train.train_name,
                       r.service_date, r.status, r.initialized_at,
                       (SELECT COUNT(*) FROM inventory_run_stops stop WHERE stop.train_run_id = r.train_run_id) AS stops,
                       (SELECT COUNT(*) FROM inventory_coaches coach WHERE coach.train_run_id = r.train_run_id) AS coaches,
                       (SELECT COUNT(*) FROM inventory_berths berth WHERE berth.train_run_id = r.train_run_id) AS berths
                FROM inventory_train_runs r
                JOIN trains train ON train.train_id = r.train_id
                """;
    }

    private RowMapper<RunSummary> runSummaryMapper() {
        return (result, row) -> new RunSummary(
                result.getObject("train_run_id", UUID.class), result.getObject("train_id", UUID.class),
                result.getString("train_number"), result.getString("train_name"),
                result.getObject("service_date", LocalDate.class), result.getString("status"),
                result.getInt("stops"), result.getInt("coaches"), result.getInt("berths"),
                result.getTimestamp("initialized_at").toInstant()
        );
    }

    private RowMapper<HoldResponse> holdMapper() {
        return (result, row) -> new HoldResponse(
                result.getObject("inventory_hold_id", UUID.class), result.getObject("train_run_id", UUID.class),
                result.getObject("inventory_berth_id", UUID.class), result.getString("coach_number"),
                result.getString("coach_class_code"), result.getString("berth_number"),
                result.getObject("from_station_id", UUID.class), result.getString("from_station_name"),
                result.getObject("to_station_id", UUID.class), result.getString("to_station_name"),
                result.getString("status"), result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("expires_at").toInstant()
        );
    }

    private RowMapper<HoldResponse> holdMapperWithFingerprint() {
        return holdMapper();
    }

    private RowMapper<BlockResponse> blockMapper() {
        return (result, row) -> new BlockResponse(
                result.getObject("inventory_block_id", UUID.class), result.getObject("train_run_id", UUID.class),
                result.getObject("inventory_berth_id", UUID.class), result.getString("coach_number"),
                result.getString("berth_number"), result.getString("block_type"), result.getString("reason"),
                result.getString("status"), result.getTimestamp("created_at").toInstant()
        );
    }

    private MapSqlParameterSource journeyParams(Journey journey) {
        return new MapSqlParameterSource()
                .addValue("runId", journey.runId())
                .addValue("fromIndex", journey.fromIndex())
                .addValue("toIndex", journey.toIndex());
    }

    private void audit(UUID runId, UUID berthId, UUID actor, String event, Map<String, ?> details) {
        jdbc.update(
                """
                        INSERT INTO inventory_audit_log(
                            inventory_audit_id, train_run_id, inventory_berth_id, actor_user_id,
                            event_type, details, correlation_id
                        ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                        """,
                UUID.randomUUID(), runId, berthId, actor, event, json(details), RequestIds.current());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize inventory snapshot", exception);
        }
    }

    private String fingerprint(HoldRequest request) {
        String canonical = String.join("|", request.trainRunId().toString(),
                request.fromStationId().toString(), request.toStationId().toString(),
                request.coachClassCode().trim().toUpperCase(),
                request.preferredBerthType() == null ? "" : request.preferredBerthType().trim().toUpperCase());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static boolean isSegmentClaimConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains("inventory_segment_claims_pkey")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static int boundedPageSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static InventoryException failure(HttpStatus status, String code, String message) {
        return new InventoryException(status, code, message);
    }

    private record RunSource(UUID trainId, String trainNumber, String trainName, UUID scheduleId,
                             UUID routeVersionId, UUID compositionVersionId) {
    }

    private record RouteStopSource(UUID routeStopId) {
    }

    private record JourneyStop(int index, UUID stationId, String name, double distance) {
    }

    private record Journey(UUID runId, LocalDate serviceDate, UUID fromStationId, String fromName,
                           UUID toStationId, String toName, int fromIndex, int toIndex, double distanceKm) {
    }

    private record ExpiredHold(UUID holdId, UUID runId, UUID berthId, UUID ownerId) {
    }

    private record CoachSeed(UUID coachId, String coachNumber, int position, String configurationSnapshot,
                             UUID configurationId, UUID coachClassId, String classCode,
                             String className, String typeName) {
    }

    private record CoachSource(UUID coachId, String coachNumber, int position, UUID configurationId,
                               UUID coachClassId, String classCode, String className, String typeName,
                               Map<String, Object> snapshot, List<AccommodationSource> berths) {
    }

    private record AccommodationSource(UUID accommodationId, String number, String type,
                                       int displayOrder, String attributesJson) {
    }
}
