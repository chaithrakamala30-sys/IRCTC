package in.railconnect.core.operations;

import in.railconnect.core.operations.OperationsModels.*;
import in.railconnect.core.reservation.ReservationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class TrainOperationsControlService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrainOperationsControlService.class);

    private final JdbcTemplate jdbcTemplate;

    public TrainOperationsControlService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // =========================================================================
    // 1. Operational Dashboard
    // =========================================================================

    public OperationsDashboardSummary getDashboardSummary() {
        int totalScheduled = queryCount("SELECT COUNT(*) FROM inventory_train_runs WHERE service_date >= CURRENT_DATE");
        int running = queryCount("SELECT COUNT(*) FROM train_run_operations WHERE operational_status = 'RUNNING'");
        int delayed = queryCount("SELECT COUNT(*) FROM train_run_operations WHERE current_delay_minutes > 15 AND operational_status NOT IN ('CANCELLED', 'COMPLETED')");
        int halted = queryCount("SELECT COUNT(*) FROM train_run_operations WHERE operational_status = 'HALTED'");
        int completed = queryCount("SELECT COUNT(*) FROM train_run_operations WHERE operational_status = 'COMPLETED'");
        int cancelled = queryCount("SELECT COUNT(*) FROM train_run_operations WHERE operational_status = 'CANCELLED'");
        int activeIncidents = queryCount("SELECT COUNT(*) FROM operational_incidents WHERE status IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'ESCALATED')");
        int platformConflicts = queryCount("SELECT COUNT(*) FROM platform_assignments WHERE conflict_flag = TRUE");

        List<OperationalAnnouncementView> announcements = listAnnouncements("PUBLISHED", 5);
        List<TrainMonitoringView> activeRuns = listActiveRuns(20);

        return new OperationsDashboardSummary(
                totalScheduled, running, delayed, halted, completed, cancelled,
                activeIncidents, platformConflicts, announcements, activeRuns
        );
    }

    public List<TrainMonitoringView> listActiveRuns(int limit) {
        String sql = """
            SELECT ir.train_run_id, tr.train_number, tr.train_name, ir.service_date,
                   COALESCE(tro.operational_status, 'SCHEDULED') AS operational_status,
                   cs.station_code AS current_station_code, cs.station_name AS current_station_name,
                   ns.station_code AS next_station_code, ns.station_name AS next_station_name,
                   tro.last_reported_event, tro.last_reported_at,
                   COALESCE(tro.current_delay_minutes, 0) AS delay_minutes,
                   COALESCE(tro.route_progress_percent, 0.0) AS route_progress_percent,
                   jsonb_array_length(ir.route_snapshot) AS total_stops
            FROM inventory_train_runs ir
            JOIN trains tr ON ir.train_id = tr.train_id
            LEFT JOIN train_run_operations tro ON ir.train_run_id = tro.train_run_id
            LEFT JOIN stations cs ON tro.current_station_id = cs.station_id
            LEFT JOIN stations ns ON tro.next_station_id = ns.station_id
            WHERE ir.service_date >= CURRENT_DATE - INTERVAL '1 day'
            ORDER BY
                CASE WHEN tro.operational_status = 'RUNNING' THEN 0
                     WHEN tro.operational_status = 'DELAYED' THEN 1
                     WHEN tro.operational_status = 'HALTED' THEN 2
                     ELSE 3 END,
                ir.service_date ASC
            LIMIT ?
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            int totalStops = rs.getInt("total_stops");
            double progress = rs.getDouble("route_progress_percent");
            int completedStops = (int) Math.round((progress / 100.0) * totalStops);

            return new TrainMonitoringView(
                    (UUID) rs.getObject("train_run_id"),
                    rs.getString("train_number"),
                    rs.getString("train_name"),
                    rs.getDate("service_date").toString(),
                    rs.getString("operational_status"),
                    rs.getString("current_station_code"),
                    rs.getString("current_station_name"),
                    rs.getString("next_station_code"),
                    rs.getString("next_station_name"),
                    rs.getString("last_reported_event"),
                    toInstant(rs.getTimestamp("last_reported_at")),
                    rs.getInt("delay_minutes"),
                    progress,
                    totalStops,
                    completedStops
            );
        }, limit);
    }

    // =========================================================================
    // 2. Station Operational Updates & Delay Calculation
    // =========================================================================

    @Transactional
    public StationOperationalUpdateView recordStationUpdate(StationOperationalUpdateInput input, UUID staffUserId) {
        // 1. Calculate delay in minutes: reportedActualTime - scheduledTime
        long diffMillis = input.reportedActualTime().toEpochMilli() - input.scheduledTime().toEpochMilli();
        int delayMinutes = (int) Math.max(0, diffMillis / 60000);

        UUID updateId = UUID.randomUUID();
        String insertSql = """
            INSERT INTO station_operational_updates
                (update_id, train_run_id, station_id, stop_index, event_type,
                 scheduled_time, reported_actual_time, delay_minutes, platform_number,
                 halt_duration_minutes, remarks, reported_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (train_run_id, station_id, event_type)
            DO UPDATE SET
                reported_actual_time = EXCLUDED.reported_actual_time,
                delay_minutes = EXCLUDED.delay_minutes,
                platform_number = EXCLUDED.platform_number,
                halt_duration_minutes = EXCLUDED.halt_duration_minutes,
                remarks = EXCLUDED.remarks,
                reported_by = EXCLUDED.reported_by,
                reported_at = CURRENT_TIMESTAMP
            RETURNING update_id
            """;

        UUID savedId = jdbcTemplate.queryForObject(insertSql, UUID.class,
                updateId, input.trainRunId(), input.stationId(), input.stopIndex(), input.eventType(),
                Timestamp.from(input.scheduledTime()), Timestamp.from(input.reportedActualTime()),
                delayMinutes, input.platformNumber(), input.haltDurationMinutes(), input.remarks(), staffUserId);

        // 2. Compute route progress & propagate downstream
        Integer totalStops = jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(route_snapshot) FROM inventory_train_runs WHERE train_run_id = ?",
                Integer.class, input.trainRunId()
        );
        int stopsCount = totalStops == null || totalStops == 0 ? 1 : totalStops;
        double progressPercent = Math.min(100.0, ((double) (input.stopIndex() + 1) / stopsCount) * 100.0);

        String newStatus = (progressPercent >= 100.0 && "DEPARTURE".equals(input.eventType()))
                ? "COMPLETED"
                : (delayMinutes > 15 ? "DELAYED" : "RUNNING");

        // 3. Update or initialize train_run_operations
        String upsertOp = """
            INSERT INTO train_run_operations
                (operation_id, train_run_id, operational_status, current_station_id,
                 last_reported_station_id, last_reported_event, last_reported_at,
                 current_delay_minutes, route_progress_percent, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (train_run_id)
            DO UPDATE SET
                operational_status = EXCLUDED.operational_status,
                current_station_id = EXCLUDED.current_station_id,
                last_reported_station_id = EXCLUDED.last_reported_station_id,
                last_reported_event = EXCLUDED.last_reported_event,
                last_reported_at = EXCLUDED.last_reported_at,
                current_delay_minutes = EXCLUDED.current_delay_minutes,
                route_progress_percent = EXCLUDED.route_progress_percent,
                updated_by = EXCLUDED.updated_by,
                updated_at = CURRENT_TIMESTAMP
            """;
        jdbcTemplate.update(upsertOp,
                UUID.randomUUID(), input.trainRunId(), newStatus, input.stationId(),
                input.stationId(), input.eventType(), Timestamp.from(input.reportedActualTime()),
                delayMinutes, progressPercent, staffUserId);

        // Log history
        jdbcTemplate.update("""
            INSERT INTO train_run_status_history
                (history_id, operation_id, new_status, reported_station_id, delay_minutes, remarks, actor_user_id)
            SELECT gen_random_uuid(), operation_id, ?, ?, ?, ?, ?
            FROM train_run_operations WHERE train_run_id = ?
            """, newStatus, input.stationId(), delayMinutes, input.remarks(), staffUserId, input.trainRunId());

        return getStationUpdate(savedId);
    }

    public StationOperationalUpdateView getStationUpdate(UUID updateId) {
        String sql = """
            SELECT sou.*, s.station_code, s.station_name, u.full_name AS staff_name
            FROM station_operational_updates sou
            JOIN stations s ON sou.station_id = s.station_id
            JOIN users u ON sou.reported_by = u.user_id
            WHERE sou.update_id = ?
            """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new StationOperationalUpdateView(
                (UUID) rs.getObject("update_id"),
                (UUID) rs.getObject("train_run_id"),
                (UUID) rs.getObject("station_id"),
                rs.getString("station_code"),
                rs.getString("station_name"),
                rs.getInt("stop_index"),
                rs.getString("event_type"),
                toInstant(rs.getTimestamp("scheduled_time")),
                toInstant(rs.getTimestamp("reported_actual_time")),
                rs.getInt("delay_minutes"),
                rs.getString("platform_number"),
                rs.getInt("halt_duration_minutes"),
                rs.getString("remarks"),
                rs.getString("staff_name"),
                toInstant(rs.getTimestamp("reported_at"))
        ), updateId);
    }

    // =========================================================================
    // 3. Platform Management & Conflict Detection
    // =========================================================================

    public List<PlatformView> listPlatforms(UUID stationId) {
        String sql = """
            SELECT p.*, s.station_code
            FROM station_platforms p
            JOIN stations s ON p.station_id = s.station_id
            WHERE p.station_id = ?
            ORDER BY p.platform_number
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new PlatformView(
                (UUID) rs.getObject("platform_id"),
                (UUID) rs.getObject("station_id"),
                rs.getString("station_code"),
                rs.getString("platform_number"),
                rs.getString("track_number"),
                rs.getInt("length_meters"),
                rs.getBoolean("electrified"),
                rs.getString("status")
        ), stationId);
    }

    @Transactional
    public PlatformAssignmentView assignPlatform(PlatformAssignmentInput input, UUID staffUserId) {
        // Conflict Check: Is there an overlapping assignment on the same platform?
        List<UUID> conflicts = jdbcTemplate.queryForList("""
            SELECT assignment_id
            FROM platform_assignments
            WHERE station_id = ? AND platform_id = ? AND assignment_status IN ('PLANNED', 'OCCUPIED')
              AND train_run_id <> ?
              AND (
                  (scheduled_arrival <= ? AND scheduled_departure >= ?)
                  OR (scheduled_arrival >= ? AND scheduled_arrival <= ?)
              )
            FOR UPDATE
            """, UUID.class, input.stationId(), input.platformId(), input.trainRunId(),
                Timestamp.from(input.scheduledArrival()), Timestamp.from(input.scheduledArrival()),
                Timestamp.from(input.scheduledArrival()), Timestamp.from(input.scheduledDeparture()));

        boolean hasConflict = !conflicts.isEmpty();
        if (hasConflict) {
            LOGGER.warn("Platform conflict detected for station {} platform {}", input.stationId(), input.platformId());
        }

        UUID assignmentId = UUID.randomUUID();
        String sql = """
            INSERT INTO platform_assignments
                (assignment_id, station_id, platform_id, train_run_id, scheduled_arrival,
                 scheduled_departure, assignment_status, conflict_flag, assigned_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                assignmentId, input.stationId(), input.platformId(), input.trainRunId(),
                Timestamp.from(input.scheduledArrival()), Timestamp.from(input.scheduledDeparture()),
                hasConflict ? "CONFLICT" : "PLANNED", hasConflict, staffUserId);

        return getPlatformAssignment(assignmentId);
    }

    public PlatformAssignmentView getPlatformAssignment(UUID assignmentId) {
        String sql = """
            SELECT pa.*, sp.platform_number, tr.train_number, tr.train_name
            FROM platform_assignments pa
            JOIN station_platforms sp ON pa.platform_id = sp.platform_id
            JOIN inventory_train_runs ir ON pa.train_run_id = ir.train_run_id
            JOIN trains tr ON ir.train_id = tr.train_id
            WHERE pa.assignment_id = ?
            """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new PlatformAssignmentView(
                (UUID) rs.getObject("assignment_id"),
                (UUID) rs.getObject("station_id"),
                (UUID) rs.getObject("platform_id"),
                rs.getString("platform_number"),
                (UUID) rs.getObject("train_run_id"),
                rs.getString("train_number"),
                rs.getString("train_name"),
                toInstant(rs.getTimestamp("scheduled_arrival")),
                toInstant(rs.getTimestamp("scheduled_departure")),
                toInstant(rs.getTimestamp("actual_arrival")),
                toInstant(rs.getTimestamp("actual_departure")),
                rs.getString("assignment_status"),
                rs.getBoolean("conflict_flag")
        ), assignmentId);
    }

    // =========================================================================
    // 4. Incident Management
    // =========================================================================

    public List<IncidentView> listIncidents(String statusFilter) {
        String sql = """
            SELECT oi.*, tr.train_number, s.station_name, u.full_name AS reported_by_name
            FROM operational_incidents oi
            LEFT JOIN inventory_train_runs ir ON oi.train_run_id = ir.train_run_id
            LEFT JOIN trains tr ON ir.train_id = tr.train_id
            LEFT JOIN stations s ON oi.station_id = s.station_id
            JOIN users u ON oi.reported_by = u.user_id
            WHERE (? IS NULL OR oi.status = ?)
            ORDER BY
                CASE WHEN oi.priority = 'CRITICAL' THEN 0
                     WHEN oi.priority = 'HIGH' THEN 1
                     ELSE 2 END,
                oi.created_at DESC
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new IncidentView(
                (UUID) rs.getObject("incident_id"),
                rs.getString("incident_number"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("category"),
                rs.getString("priority"),
                rs.getString("status"),
                (UUID) rs.getObject("train_run_id"),
                rs.getString("train_number"),
                (UUID) rs.getObject("station_id"),
                rs.getString("station_name"),
                rs.getString("reported_by_name"),
                rs.getString("resolution_notes"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("resolved_at"))
        ), statusFilter, statusFilter);
    }

    @Transactional
    public IncidentView createIncident(IncidentInput input, UUID staffUserId) {
        UUID incidentId = UUID.randomUUID();
        String incNum = "INC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        jdbcTemplate.update("""
            INSERT INTO operational_incidents
                (incident_id, incident_number, category, priority, status, train_run_id,
                 station_id, title, description, reported_by)
            VALUES (?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?)
            """, incidentId, incNum, input.category(), input.priority(),
                input.trainRunId(), input.stationId(), input.title(), input.description(), staffUserId);

        jdbcTemplate.update("""
            INSERT INTO operational_incident_history (history_id, incident_id, new_status, remarks, updated_by)
            VALUES (gen_random_uuid(), ?, 'OPEN', 'Incident logged in control centre', ?)
            """, incidentId, staffUserId);

        return getIncident(incidentId);
    }

    public IncidentView getIncident(UUID incidentId) {
        String sql = """
            SELECT oi.*, tr.train_number, s.station_name, u.full_name AS reported_by_name
            FROM operational_incidents oi
            LEFT JOIN inventory_train_runs ir ON oi.train_run_id = ir.train_run_id
            LEFT JOIN trains tr ON ir.train_id = tr.train_id
            LEFT JOIN stations s ON oi.station_id = s.station_id
            JOIN users u ON oi.reported_by = u.user_id
            WHERE oi.incident_id = ?
            """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new IncidentView(
                (UUID) rs.getObject("incident_id"),
                rs.getString("incident_number"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("category"),
                rs.getString("priority"),
                rs.getString("status"),
                (UUID) rs.getObject("train_run_id"),
                rs.getString("train_number"),
                (UUID) rs.getObject("station_id"),
                rs.getString("station_name"),
                rs.getString("reported_by_name"),
                rs.getString("resolution_notes"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("resolved_at"))
        ), incidentId);
    }

    // =========================================================================
    // 5. Disruptions & Passenger Impact Assessment
    // =========================================================================

    @Transactional
    public DisruptionView reportDisruption(DisruptionInput input, UUID staffUserId) {
        UUID disruptionId = UUID.randomUUID();

        jdbcTemplate.update("""
            INSERT INTO operational_disruptions
                (disruption_id, train_run_id, disruption_type, affected_station_id,
                 expected_duration_minutes, reason, operational_impact, reported_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, disruptionId, input.trainRunId(), input.disruptionType(), input.affectedStationId(),
                input.expectedDurationMinutes(), input.reason(), input.operationalImpact(), staffUserId);

        // Perform Passenger Impact Assessment
        Map<String, Object> paxCounts = jdbcTemplate.queryForMap("""
            SELECT
                COUNT(bp.booking_passenger_id) AS total_pax,
                COUNT(CASE WHEN ra.status = 'CONFIRMED' THEN 1 END) AS confirmed_pax,
                COUNT(CASE WHEN re.status = 'RAC' THEN 1 END) AS rac_pax,
                COUNT(CASE WHEN we.status = 'WAITLISTED' THEN 1 END) AS wl_pax
            FROM booking_passengers bp
            JOIN bookings b ON bp.booking_id = b.booking_id
            JOIN reservation_requests rr ON b.reservation_request_id = rr.reservation_request_id
            LEFT JOIN reservation_allocations ra ON bp.booking_passenger_id = ra.booking_passenger_id
            LEFT JOIN rac_entries re ON bp.booking_passenger_id = re.booking_passenger_id
            LEFT JOIN waitlist_entries we ON bp.booking_passenger_id = we.booking_passenger_id
            WHERE rr.train_run_id = ? AND b.status IN ('CONFIRMED', 'AWAITING_PAYMENT')
            """, input.trainRunId());

        int total = ((Number) paxCounts.get("total_pax")).intValue();
        int confirmed = ((Number) paxCounts.get("confirmed_pax")).intValue();
        int rac = ((Number) paxCounts.get("rac_pax")).intValue();
        int wl = ((Number) paxCounts.get("wl_pax")).intValue();

        UUID assessId = UUID.randomUUID();
        String summary = String.format("Disruption %s impacts %d passengers on this train run.", input.disruptionType(), total);
        jdbcTemplate.update("""
            INSERT INTO passenger_impact_assessments
                (assessment_id, disruption_id, train_run_id, affected_passengers_count,
                 affected_confirmed_count, affected_rac_count, affected_waitlist_count,
                 summary, action_taken, assessed_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Alerts queued for passenger notification', ?)
            """, assessId, disruptionId, input.trainRunId(), total, confirmed, rac, wl, summary, staffUserId);

        return getDisruption(disruptionId);
    }

    public DisruptionView getDisruption(UUID disruptionId) {
        String sql = """
            SELECT od.*, tr.train_number, s.station_name, pia.assessment_id,
                   pia.affected_passengers_count, pia.affected_confirmed_count,
                   pia.affected_rac_count, pia.affected_waitlist_count,
                   pia.summary AS impact_summary, pia.action_taken, pia.assessed_at
            FROM operational_disruptions od
            JOIN inventory_train_runs ir ON od.train_run_id = ir.train_run_id
            JOIN trains tr ON ir.train_id = tr.train_id
            LEFT JOIN stations s ON od.affected_station_id = s.station_id
            LEFT JOIN passenger_impact_assessments pia ON od.disruption_id = pia.disruption_id
            WHERE od.disruption_id = ?
            """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            PassengerImpactAssessmentView impact = rs.getObject("assessment_id") == null ? null :
                    new PassengerImpactAssessmentView(
                            (UUID) rs.getObject("assessment_id"),
                            rs.getInt("affected_passengers_count"),
                            rs.getInt("affected_confirmed_count"),
                            rs.getInt("affected_rac_count"),
                            rs.getInt("affected_waitlist_count"),
                            rs.getString("impact_summary"),
                            rs.getString("action_taken"),
                            toInstant(rs.getTimestamp("assessed_at"))
                    );

            return new DisruptionView(
                    (UUID) rs.getObject("disruption_id"),
                    (UUID) rs.getObject("train_run_id"),
                    rs.getString("train_number"),
                    rs.getString("disruption_type"),
                    rs.getString("station_name"),
                    rs.getInt("expected_duration_minutes"),
                    rs.getString("reason"),
                    rs.getString("operational_impact"),
                    rs.getString("status"),
                    toInstant(rs.getTimestamp("start_time")),
                    toInstant(rs.getTimestamp("resolved_at")),
                    impact
            );
        }, disruptionId);
    }

    // =========================================================================
    // 6. Operational Announcements
    // =========================================================================

    public List<OperationalAnnouncementView> listAnnouncements(String status, int limit) {
        String sql = """
            SELECT * FROM operational_announcements
            WHERE (? IS NULL OR status = ?)
            ORDER BY
                CASE WHEN priority = 'CRITICAL' THEN 0
                     WHEN priority = 'URGENT' THEN 1
                     ELSE 2 END,
                created_at DESC
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new OperationalAnnouncementView(
                (UUID) rs.getObject("announcement_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("announcement_type"),
                rs.getString("priority"),
                rs.getString("target_scope"),
                (UUID) rs.getObject("target_train_run_id"),
                (UUID) rs.getObject("target_station_id"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("starts_at")),
                toInstant(rs.getTimestamp("expires_at")),
                toInstant(rs.getTimestamp("created_at"))
        ), status, status, limit);
    }

    @Transactional
    public OperationalAnnouncementView publishAnnouncement(OperationalAnnouncementInput input, UUID staffUserId) {
        UUID id = UUID.randomUUID();
        String sql = """
            INSERT INTO operational_announcements
                (announcement_id, title, content, announcement_type, priority,
                 target_scope, target_train_run_id, target_station_id, status, expires_at, published_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?)
            """;
        jdbcTemplate.update(sql,
                id, input.title(), input.content(), input.announcementType(), input.priority(),
                input.targetScope(), input.targetTrainRunId(), input.targetStationId(),
                input.expiresAt() == null ? null : Timestamp.from(input.expiresAt()), staffUserId);

        return getAnnouncement(id);
    }

    public OperationalAnnouncementView getAnnouncement(UUID announcementId) {
        String sql = "SELECT * FROM operational_announcements WHERE announcement_id = ?";
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new OperationalAnnouncementView(
                (UUID) rs.getObject("announcement_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("announcement_type"),
                rs.getString("priority"),
                rs.getString("target_scope"),
                (UUID) rs.getObject("target_train_run_id"),
                (UUID) rs.getObject("target_station_id"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("starts_at")),
                toInstant(rs.getTimestamp("expires_at")),
                toInstant(rs.getTimestamp("created_at"))
        ), announcementId);
    }

    // =========================================================================
    // 7. Public Train Tracking (No external APIs, pure internal station reports)
    // =========================================================================

    public PublicTrainTrackingView getPublicTrainTracking(String trainNumber, String serviceDate) {
        String runSql = """
            SELECT ir.train_run_id, tr.train_number, tr.train_name, ir.service_date,
                   COALESCE(tro.operational_status, 'SCHEDULED') AS operational_status,
                   COALESCE(tro.current_delay_minutes, 0) AS current_delay_minutes,
                   COALESCE(tro.route_progress_percent, 0.0) AS route_progress_percent,
                   cs.station_code AS current_code, cs.station_name AS current_name,
                   ns.station_code AS next_code, ns.station_name AS next_name,
                   tro.last_reported_at
            FROM inventory_train_runs ir
            JOIN trains tr ON ir.train_id = tr.train_id
            LEFT JOIN train_run_operations tro ON ir.train_run_id = tro.train_run_id
            LEFT JOIN stations cs ON tro.current_station_id = cs.station_id
            LEFT JOIN stations ns ON tro.next_station_id = ns.station_id
            WHERE tr.train_number = ? AND (ir.service_date = ?::date OR ? IS NULL)
            ORDER BY ir.service_date DESC
            LIMIT 1
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(runSql, trainNumber, serviceDate, serviceDate);
        if (rows.isEmpty()) {
            throw new ReservationException(HttpStatus.NOT_FOUND, "TRAIN_RUN_NOT_FOUND", "No active service found for train " + trainNumber);
        }
        Map<String, Object> r = rows.get(0);
        UUID runId = (UUID) r.get("train_run_id");

        // Load route stops and reported updates
        String stopsSql = """
            SELECT s.station_code, s.station_name, irs.stop_index,
                   irs.arrival_time, irs.departure_time,
                   sou.event_type, sou.reported_actual_time, sou.delay_minutes, sou.platform_number
            FROM inventory_run_stops irs
            JOIN stations s ON irs.station_id = s.station_id
            LEFT JOIN station_operational_updates sou ON irs.train_run_id = sou.train_run_id AND irs.station_id = sou.station_id
            WHERE irs.train_run_id = ?
            ORDER BY irs.stop_index ASC
            """;

        List<TrackingStopItem> stops = jdbcTemplate.query(stopsSql, (rs, rowNum) -> {
            boolean hasReported = rs.getTimestamp("reported_actual_time") != null;
            int delay = rs.getInt("delay_minutes");
            String platform = rs.getString("platform_number");

            return new TrackingStopItem(
                    rs.getInt("stop_index"),
                    rs.getString("station_code"),
                    rs.getString("station_name"),
                    rs.getString("arrival_time") != null ? rs.getString("arrival_time") : "--:--",
                    rs.getString("departure_time") != null ? rs.getString("departure_time") : "--:--",
                    hasReported ? rs.getTimestamp("reported_actual_time").toInstant().toString().substring(11, 16) : "--:--",
                    hasReported ? rs.getTimestamp("reported_actual_time").toInstant().toString().substring(11, 16) : "--:--",
                    delay,
                    platform != null ? platform : "-",
                    hasReported ? "COMPLETED" : "UPCOMING"
            );
        }, runId);

        // Data reliability flag
        Timestamp lastReportedTs = (Timestamp) r.get("last_reported_at");
        String reliability = lastReportedTs == null ? "SCHEDULED_ONLY" :
                (Duration.between(lastReportedTs.toInstant(), Instant.now()).toMinutes() > 120 ? "AWAITING_STATION_UPDATE" : "STAFF_VERIFIED");

        return new PublicTrainTrackingView(
                (String) r.get("train_number"),
                (String) r.get("train_name"),
                r.get("service_date").toString(),
                (String) r.get("operational_status"),
                (Integer) r.get("current_delay_minutes"),
                ((Number) r.get("route_progress_percent")).doubleValue(),
                (String) r.get("current_code"),
                (String) r.get("current_name"),
                (String) r.get("next_code"),
                (String) r.get("next_name"),
                toInstant(lastReportedTs),
                reliability,
                stops,
                listAnnouncements("PUBLISHED", 3)
        );
    }

    private int queryCount(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}

