package in.railconnect.core.reservation;

import in.railconnect.core.reservation.AdvancedReservationModels.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class AdvancedReservationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedReservationService.class);

    private final JdbcTemplate jdbcTemplate;

    public AdvancedReservationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // =========================================================================
    // 1. Quotas Management
    // =========================================================================

    public List<QuotaView> listQuotas() {
        String sql = """
            SELECT quota_id, quota_code, display_name, description, eligibility_criteria_json, active
            FROM reservation_quotas
            ORDER BY CASE WHEN quota_code = 'GENERAL' THEN 0 ELSE 1 END, display_name
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new QuotaView(
                (UUID) rs.getObject("quota_id"),
                rs.getString("quota_code"),
                rs.getString("display_name"),
                rs.getString("description"),
                rs.getString("eligibility_criteria_json"),
                rs.getBoolean("active")
        ));
    }

    public List<QuotaAllocationView> listQuotaAllocations(UUID trainRunId) {
        String sql = """
            SELECT q.allocation_id, q.train_run_id, q.coach_class_code, q.quota_code,
                   q.allocated_berths, q.rac_capacity, q.waitlist_capacity,
                   COALESCE((
                       SELECT COUNT(*) FROM reservation_allocations ra
                       JOIN reservation_requests rr ON ra.reservation_request_id = rr.reservation_request_id
                       WHERE rr.train_run_id = q.train_run_id AND ra.coach_class_code = q.coach_class_code
                         AND ra.status = 'CONFIRMED'
                   ), 0) AS confirmed_count,
                   COALESCE((
                       SELECT COUNT(*) FROM rac_entries re
                       WHERE re.train_run_id = q.train_run_id AND re.coach_class_code = q.coach_class_code
                         AND re.quota_code = q.quota_code AND re.status = 'RAC'
                   ), 0) AS rac_count,
                   COALESCE((
                       SELECT COUNT(*) FROM waitlist_entries we
                       WHERE we.train_run_id = q.train_run_id AND we.coach_class_code = q.coach_class_code
                         AND we.quota_code = q.quota_code AND we.status = 'WAITLISTED'
                   ), 0) AS waitlist_count
            FROM quota_inventory_allocations q
            WHERE q.train_run_id = ?
            ORDER BY q.coach_class_code, q.quota_code
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new QuotaAllocationView(
                (UUID) rs.getObject("allocation_id"),
                (UUID) rs.getObject("train_run_id"),
                rs.getString("coach_class_code"),
                rs.getString("quota_code"),
                rs.getInt("allocated_berths"),
                rs.getInt("rac_capacity"),
                rs.getInt("waitlist_capacity"),
                rs.getInt("confirmed_count"),
                rs.getInt("rac_count"),
                rs.getInt("waitlist_count")
        ), trainRunId);
    }

    @Transactional
    public QuotaAllocationView configureQuotaAllocation(QuotaAllocationInput input) {
        // Enforce physical capacity bounds: allocated berths for the class cannot exceed physical berths
        Integer physicalBerths = jdbcTemplate.queryForObject("""
            SELECT COUNT(b.inventory_berth_id)
            FROM inventory_berths b
            JOIN inventory_coaches c ON b.inventory_coach_id = c.inventory_coach_id
            WHERE c.train_run_id = ? AND c.coach_class_code = ?
            """, Integer.class, input.trainRunId(), input.coachClassCode());

        int totalPhysical = physicalBerths == null ? 0 : physicalBerths;

        // Sum existing allocations for other quotas
        Integer otherAllocated = jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(allocated_berths), 0)
            FROM quota_inventory_allocations
            WHERE train_run_id = ? AND coach_class_code = ? AND quota_code <> ?
            """, Integer.class, input.trainRunId(), input.coachClassCode(), input.quotaCode());

        int usedByOthers = otherAllocated == null ? 0 : otherAllocated;
        if (usedByOthers + input.allocatedBerths() > totalPhysical && totalPhysical > 0) {
            throw new ReservationException(HttpStatus.BAD_REQUEST, "CAPACITY_EXCEEDED",
                    "Total quota allocations (" + (usedByOthers + input.allocatedBerths()) +
                    ") exceed physical coach capacity (" + totalPhysical + ")");
        }

        UUID allocationId = UUID.randomUUID();
        String upsert = """
            INSERT INTO quota_inventory_allocations
                (allocation_id, train_run_id, coach_class_code, quota_code, allocated_berths, rac_capacity, waitlist_capacity, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (train_run_id, coach_class_code, quota_code)
            DO UPDATE SET
                allocated_berths = EXCLUDED.allocated_berths,
                rac_capacity = EXCLUDED.rac_capacity,
                waitlist_capacity = EXCLUDED.waitlist_capacity,
                updated_at = CURRENT_TIMESTAMP
            RETURNING allocation_id
            """;
        UUID savedId = jdbcTemplate.queryForObject(upsert, UUID.class,
                allocationId, input.trainRunId(), input.coachClassCode(), input.quotaCode(),
                input.allocatedBerths(), input.racCapacity(), input.waitlistCapacity());

        return new QuotaAllocationView(
                savedId, input.trainRunId(), input.coachClassCode(), input.quotaCode(),
                input.allocatedBerths(), input.racCapacity(), input.waitlistCapacity(),
                0, 0, 0
        );
    }

    // =========================================================================
    // 2. Queue & Waitlist/RAC Management
    // =========================================================================

    public List<ReservationQueueView> listQueues(UUID trainRunId) {
        String sql = """
            SELECT q.queue_id, q.train_run_id, q.coach_class_code, q.quota_code, q.queue_type,
                   q.current_highest_sequence,
                   CASE WHEN q.queue_type = 'RAC' THEN
                       (SELECT COUNT(*) FROM rac_entries r WHERE r.train_run_id = q.train_run_id AND r.coach_class_code = q.coach_class_code AND r.quota_code = q.quota_code AND r.status = 'RAC')
                   ELSE
                       (SELECT COUNT(*) FROM waitlist_entries w WHERE w.train_run_id = q.train_run_id AND w.coach_class_code = q.coach_class_code AND w.quota_code = q.quota_code AND w.status = 'WAITLISTED')
                   END AS waiting_count
            FROM reservation_queues q
            WHERE q.train_run_id = ?
            ORDER BY q.coach_class_code, q.queue_type, q.quota_code
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReservationQueueView(
                (UUID) rs.getObject("queue_id"),
                (UUID) rs.getObject("train_run_id"),
                rs.getString("coach_class_code"),
                rs.getString("quota_code"),
                rs.getString("queue_type"),
                rs.getInt("current_highest_sequence"),
                rs.getInt("waiting_count")
        ), trainRunId);
    }

    // =========================================================================
    // 3. Automatic Promotion Engine (Triggered on cancellation / berth release)
    // =========================================================================

    @Transactional
    public List<PromotionResult> processPromotionsForTrainRun(UUID trainRunId, UUID freedCancellationId) {
        List<PromotionResult> promotions = new ArrayList<>();

        // 1. Identify distinct classes that have available berths or unallocated claims
        List<String> classes = jdbcTemplate.queryForList("""
            SELECT DISTINCT c.coach_class_code
            FROM inventory_coaches c
            WHERE c.train_run_id = ?
            """, String.class, trainRunId);

        for (String classCode : classes) {
            // Find eligible RAC passengers in FIFO sequence
            List<Map<String, Object>> racPax = jdbcTemplate.queryForList("""
                SELECT re.rac_id, re.booking_passenger_id, re.current_position, bp.passenger_name
                FROM rac_entries re
                JOIN booking_passengers bp ON re.booking_passenger_id = bp.booking_passenger_id
                WHERE re.train_run_id = ? AND re.coach_class_code = ? AND re.status = 'RAC'
                ORDER BY re.queue_sequence ASC
                LIMIT 5
                FOR UPDATE SKIP LOCKED
                """, trainRunId, classCode);

            // Promote RAC to CONFIRMED if berths available
            for (Map<String, Object> pax : racPax) {
                UUID bpId = (UUID) pax.get("booking_passenger_id");
                UUID racId = (UUID) pax.get("rac_id");
                int currentPos = (Integer) pax.get("current_position");
                String paxName = (String) pax.get("passenger_name");

                // Assign a confirmed berth
                List<UUID> freeBerths = jdbcTemplate.queryForList("""
                    SELECT b.inventory_berth_id
                    FROM inventory_berths b
                    JOIN inventory_coaches c ON b.inventory_coach_id = c.inventory_coach_id
                    WHERE c.train_run_id = ? AND c.coach_class_code = ?
                      AND b.inventory_berth_id NOT IN (
                          SELECT inventory_berth_id FROM reservation_allocations WHERE status = 'CONFIRMED'
                      )
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """, UUID.class, trainRunId, classCode);

                if (!freeBerths.isEmpty()) {
                    UUID berthId = freeBerths.get(0);
                    // Update RAC entry
                    jdbcTemplate.update("""
                        UPDATE rac_entries
                        SET status = 'PROMOTED_CONFIRMED', current_position = 0, updated_at = CURRENT_TIMESTAMP
                        WHERE rac_id = ?
                        """, racId);

                    // Insert state change history
                    recordStateChange(bpId, "RAC", "CONFIRMED", "CANCELLATION_PROMOTION", "Promoted to confirmed berth on cancellation");

                    UUID promoId = UUID.randomUUID();
                    jdbcTemplate.update("""
                        INSERT INTO reservation_promotions
                            (promotion_id, train_run_id, booking_passenger_id, from_status, to_status,
                             promoted_from_position, promoted_to_position, freed_by_cancellation_id, allocated_berth_id)
                        VALUES (?, ?, ?, 'RAC', 'CONFIRMED', ?, 0, ?, ?)
                        """, promoId, trainRunId, bpId, currentPos, freedCancellationId, berthId);

                    promotions.add(new PromotionResult(
                            promoId, bpId, paxName, "RAC", "CONFIRMED", currentPos, 0, "AUTO", "AUTO", Instant.now()
                    ));
                }
            }

            // Promote Waitlist to RAC
            List<Map<String, Object>> wlPax = jdbcTemplate.queryForList("""
                SELECT we.waitlist_id, we.booking_passenger_id, we.current_position, bp.passenger_name
                FROM waitlist_entries we
                JOIN booking_passengers bp ON we.booking_passenger_id = bp.booking_passenger_id
                WHERE we.train_run_id = ? AND we.coach_class_code = ? AND we.status = 'WAITLISTED'
                ORDER BY we.queue_sequence ASC
                LIMIT 5
                FOR UPDATE SKIP LOCKED
                """, trainRunId, classCode);

            for (Map<String, Object> pax : wlPax) {
                UUID bpId = (UUID) pax.get("booking_passenger_id");
                UUID wlId = (UUID) pax.get("waitlist_id");
                int currentPos = (Integer) pax.get("current_position");
                String paxName = (String) pax.get("passenger_name");

                jdbcTemplate.update("""
                    UPDATE waitlist_entries
                    SET status = 'PROMOTED_RAC', current_position = 0, updated_at = CURRENT_TIMESTAMP
                    WHERE waitlist_id = ?
                    """, wlId);

                recordStateChange(bpId, "WAITLISTED", "RAC", "WAITLIST_PROMOTION", "Promoted from waitlist to RAC");

                UUID promoId = UUID.randomUUID();
                jdbcTemplate.update("""
                    INSERT INTO reservation_promotions
                        (promotion_id, train_run_id, booking_passenger_id, from_status, to_status,
                         promoted_from_position, promoted_to_position, freed_by_cancellation_id)
                    VALUES (?, ?, ?, 'WAITLISTED', 'RAC', ?, 0, ?)
                    """, promoId, trainRunId, bpId, currentPos, freedCancellationId);

                promotions.add(new PromotionResult(
                        promoId, bpId, paxName, "WAITLISTED", "RAC", currentPos, 0, "-", "-", Instant.now()
                ));
            }
        }

        return promotions;
    }

    private void recordStateChange(UUID bookingPassengerId, String fromState, String toState, String reason, String details) {
        jdbcTemplate.update("""
            INSERT INTO passenger_reservation_state_history (history_id, booking_passenger_id, previous_state, new_state, reason, details)
            VALUES (?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), bookingPassengerId, fromState, toState, reason, details);
    }

    // =========================================================================
    // 4. Passenger Reservation Status Tracking
    // =========================================================================

    public PassengerReservationStatusView getBookingReservationStatus(UUID bookingId) {
        String bookingSql = """
            SELECT b.booking_id, b.booking_reference, t.pnr, tr.train_number, tr.train_name,
                   ir.service_date, rr.coach_class_code, b.status AS booking_status
            FROM bookings b
            LEFT JOIN tickets t ON b.booking_id = t.booking_id
            JOIN reservation_requests rr ON b.reservation_request_id = rr.reservation_request_id
            JOIN inventory_train_runs ir ON rr.train_run_id = ir.train_run_id
            JOIN trains tr ON ir.train_id = tr.train_id
            WHERE b.booking_id = ?
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(bookingSql, bookingId);
        if (rows.isEmpty()) {
            throw new ReservationException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found");
        }
        Map<String, Object> b = rows.get(0);

        List<PassengerStatusItem> passengers = jdbcTemplate.query("""
            SELECT bp.booking_passenger_id, bp.passenger_number, bp.passenger_name,
                   ra.coach_number, ra.berth_number, ra.berth_type,
                   COALESCE(we.status, re.status, ra.status, 'CONFIRMED') AS pax_status,
                   COALESCE(we.current_position, re.current_position, 0) AS position_num
            FROM booking_passengers bp
            LEFT JOIN reservation_allocations ra ON bp.booking_passenger_id = ra.booking_passenger_id
            LEFT JOIN waitlist_entries we ON bp.booking_passenger_id = we.booking_passenger_id
            LEFT JOIN rac_entries re ON bp.booking_passenger_id = re.booking_passenger_id
            WHERE bp.booking_id = ?
            ORDER BY bp.passenger_number
            """, (rs, rowNum) -> new PassengerStatusItem(
                (UUID) rs.getObject("booking_passenger_id"),
                rs.getInt("passenger_number"),
                rs.getString("passenger_name"),
                rs.getString("pax_status"),
                rs.getInt("position_num"),
                rs.getString("coach_number"),
                rs.getString("berth_number"),
                rs.getString("berth_type")
        ), bookingId);

        List<StatusHistoryItem> history = jdbcTemplate.query("""
            SELECT previous_state, new_state, reason, details, occurred_at
            FROM passenger_reservation_state_history rsh
            JOIN booking_passengers bp ON rsh.booking_passenger_id = bp.booking_passenger_id
            WHERE bp.booking_id = ?
            ORDER BY occurred_at DESC
            """, (rs, rowNum) -> new StatusHistoryItem(
                rs.getString("previous_state"),
                rs.getString("new_state"),
                rs.getString("reason"),
                rs.getString("details"),
                rs.getTimestamp("occurred_at").toInstant()
        ), bookingId);

        return new PassengerReservationStatusView(
                (UUID) b.get("booking_id"),
                (String) b.get("booking_reference"),
                (String) b.get("pnr"),
                (String) b.get("train_number"),
                (String) b.get("train_name"),
                b.get("service_date").toString(),
                (String) b.get("coach_class_code"),
                "GENERAL",
                (String) b.get("booking_status"),
                passengers,
                history
        );
    }

    // =========================================================================
    // 5. Chart Preparation & Operational Manifests
    // =========================================================================

    public List<ChartView> listCharts() {
        String sql = """
            SELECT rc.chart_id, rc.train_run_id, tr.train_number, tr.train_name, ir.service_date,
                   rc.chart_status, rc.total_passengers, rc.confirmed_passengers,
                   rc.rac_passengers, rc.waitlisted_passengers, rc.current_version,
                   rc.prepared_at, rc.finalized_at
            FROM reservation_charts rc
            JOIN inventory_train_runs ir ON rc.train_run_id = ir.train_run_id
            JOIN trains tr ON ir.train_id = tr.train_id
            ORDER BY ir.service_date DESC, tr.train_number
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ChartView(
                (UUID) rs.getObject("chart_id"),
                (UUID) rs.getObject("train_run_id"),
                rs.getString("train_number"),
                rs.getString("train_name"),
                rs.getDate("service_date").toString(),
                rs.getString("chart_status"),
                rs.getInt("total_passengers"),
                rs.getInt("confirmed_passengers"),
                rs.getInt("rac_passengers"),
                rs.getInt("waitlisted_passengers"),
                rs.getInt("current_version"),
                toInstant(rs.getTimestamp("prepared_at")),
                toInstant(rs.getTimestamp("finalized_at")),
                List.of()
        ));
    }

    @Transactional
    public ChartView prepareChart(UUID trainRunId, UUID operatorUserId) {
        // Run promotion engine first to clear RAC / WL where possible
        processPromotionsForTrainRun(trainRunId, null);

        // Fetch train details
        Map<String, Object> run = jdbcTemplate.queryForMap("""
            SELECT tr.train_number, tr.train_name, ir.service_date
            FROM inventory_train_runs ir
            JOIN trains tr ON ir.train_id = tr.train_id
            WHERE ir.train_run_id = ?
            """, trainRunId);

        UUID chartId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO reservation_charts (chart_id, train_run_id, chart_status, prepared_by)
            VALUES (?, ?, 'PREPARED', ?)
            ON CONFLICT (train_run_id) DO UPDATE SET
                chart_status = 'PREPARED',
                current_version = reservation_charts.current_version + 1,
                prepared_at = CURRENT_TIMESTAMP
            """, chartId, trainRunId, operatorUserId);

        // Retrieve the authoritative chartId
        UUID resolvedChartId = jdbcTemplate.queryForObject(
                "SELECT chart_id FROM reservation_charts WHERE train_run_id = ?", UUID.class, trainRunId
        );

        // Populate passengers snapshot
        jdbcTemplate.update("DELETE FROM reservation_chart_passengers WHERE chart_id = ?", resolvedChartId);

        String insertPax = """
            INSERT INTO reservation_chart_passengers
                (chart_passenger_id, chart_id, booking_id, booking_passenger_id, pnr, passenger_name,
                 coach_number, berth_number, berth_type, coach_class_code, reservation_status,
                 position_number, from_station_code, to_station_code)
            SELECT gen_random_uuid(), ?, b.booking_id, bp.booking_passenger_id,
                   COALESCE(t.pnr, 'RC-TK-PENDING'), bp.passenger_name,
                   ra.coach_number, ra.berth_number, ra.berth_type, rr.coach_class_code,
                   COALESCE(we.status, re.status, ra.status, 'CONFIRMED'),
                   COALESCE(we.current_position, re.current_position, 0),
                   fs.station_code, ts.station_code
            FROM booking_passengers bp
            JOIN bookings b ON bp.booking_id = b.booking_id
            LEFT JOIN tickets t ON b.booking_id = t.booking_id
            JOIN reservation_requests rr ON b.reservation_request_id = rr.reservation_request_id
            JOIN stations fs ON rr.from_station_id = fs.station_id
            JOIN stations ts ON rr.to_station_id = ts.station_id
            LEFT JOIN reservation_allocations ra ON bp.booking_passenger_id = ra.booking_passenger_id
            LEFT JOIN waitlist_entries we ON bp.booking_passenger_id = we.booking_passenger_id
            LEFT JOIN rac_entries re ON bp.booking_passenger_id = re.booking_passenger_id
            WHERE rr.train_run_id = ? AND b.status IN ('CONFIRMED', 'PENDING')
            """;
        jdbcTemplate.update(insertPax, resolvedChartId, trainRunId);

        // Update counts
        jdbcTemplate.update("""
            UPDATE reservation_charts
            SET total_passengers = (SELECT COUNT(*) FROM reservation_chart_passengers WHERE chart_id = ?),
                confirmed_passengers = (SELECT COUNT(*) FROM reservation_chart_passengers WHERE chart_id = ? AND reservation_status = 'CONFIRMED'),
                rac_passengers = (SELECT COUNT(*) FROM reservation_chart_passengers WHERE chart_id = ? AND reservation_status = 'RAC'),
                waitlisted_passengers = (SELECT COUNT(*) FROM reservation_chart_passengers WHERE chart_id = ? AND reservation_status = 'WAITLISTED')
            WHERE chart_id = ?
            """, resolvedChartId, resolvedChartId, resolvedChartId, resolvedChartId, resolvedChartId);

        return getChart(resolvedChartId);
    }

    public ChartView getChart(UUID chartId) {
        String sql = """
            SELECT rc.chart_id, rc.train_run_id, tr.train_number, tr.train_name, ir.service_date,
                   rc.chart_status, rc.total_passengers, rc.confirmed_passengers,
                   rc.rac_passengers, rc.waitlisted_passengers, rc.current_version,
                   rc.prepared_at, rc.finalized_at
            FROM reservation_charts rc
            JOIN inventory_train_runs ir ON rc.train_run_id = ir.train_run_id
            JOIN trains tr ON ir.train_id = tr.train_id
            WHERE rc.chart_id = ?
            """;
        List<ChartView> charts = jdbcTemplate.query(sql, (rs, rowNum) -> new ChartView(
                (UUID) rs.getObject("chart_id"),
                (UUID) rs.getObject("train_run_id"),
                rs.getString("train_number"),
                rs.getString("train_name"),
                rs.getDate("service_date").toString(),
                rs.getString("chart_status"),
                rs.getInt("total_passengers"),
                rs.getInt("confirmed_passengers"),
                rs.getInt("rac_passengers"),
                rs.getInt("waitlisted_passengers"),
                rs.getInt("current_version"),
                toInstant(rs.getTimestamp("prepared_at")),
                toInstant(rs.getTimestamp("finalized_at")),
                List.of()
        ), chartId);

        if (charts.isEmpty()) {
            throw new ReservationException(HttpStatus.NOT_FOUND, "CHART_NOT_FOUND", "Reservation chart not found");
        }
        ChartView summary = charts.get(0);

        List<ChartPassengerView> passengers = jdbcTemplate.query("""
            SELECT chart_passenger_id, pnr, passenger_name, coach_number, berth_number,
                   berth_type, coach_class_code, reservation_status, position_number,
                   from_station_code, to_station_code
            FROM reservation_chart_passengers
            WHERE chart_id = ?
            ORDER BY coach_number, berth_number, position_number
            """, (rs, rowNum) -> new ChartPassengerView(
                (UUID) rs.getObject("chart_passenger_id"),
                rs.getString("pnr"),
                rs.getString("passenger_name"),
                rs.getString("coach_number"),
                rs.getString("berth_number"),
                rs.getString("berth_type"),
                rs.getString("coach_class_code"),
                rs.getString("reservation_status"),
                rs.getInt("position_number"),
                rs.getString("from_station_code"),
                rs.getString("to_station_code")
        ), chartId);

        return new ChartView(
                summary.chartId(), summary.trainRunId(), summary.trainNumber(), summary.trainName(),
                summary.serviceDate(), summary.chartStatus(), summary.totalPassengers(),
                summary.confirmedPassengers(), summary.racPassengers(), summary.waitlistedPassengers(),
                summary.currentVersion(), summary.preparedAt(), summary.finalizedAt(), passengers
        );
    }

    @Transactional
    public ChartView finalizeChart(UUID chartId, UUID operatorUserId) {
        int updated = jdbcTemplate.update("""
            UPDATE reservation_charts
            SET chart_status = 'FINALIZED', finalized_at = CURRENT_TIMESTAMP, finalized_by = ?
            WHERE chart_id = ?
            """, operatorUserId, chartId);
        if (updated == 0) {
            throw new ReservationException(HttpStatus.NOT_FOUND, "CHART_NOT_FOUND", "Reservation chart not found");
        }
        return getChart(chartId);
    }

    @Transactional
    public ChartView authorizeCorrection(UUID chartId, ChartCorrectionRequest req, UUID operatorUserId) {
        // Fetch current passenger berth
        Map<String, Object> current = jdbcTemplate.queryForMap("""
            SELECT coach_number, berth_number
            FROM reservation_chart_passengers
            WHERE chart_passenger_id = ? AND chart_id = ?
            """, req.chartPassengerId(), chartId);

        String prevCoach = (String) current.get("coach_number");
        String prevBerth = (String) current.get("berth_number");

        // Record immutable correction history
        jdbcTemplate.update("""
            INSERT INTO chart_correction_history
                (correction_id, chart_id, chart_passenger_id, previous_coach, new_coach,
                 previous_berth, new_berth, reason, authorized_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), chartId, req.chartPassengerId(), prevCoach, req.newCoach(),
                prevBerth, req.newBerth(), req.reason(), operatorUserId);

        // Update passenger assignment on chart
        jdbcTemplate.update("""
            UPDATE reservation_chart_passengers
            SET coach_number = ?, berth_number = ?
            WHERE chart_passenger_id = ? AND chart_id = ?
            """, req.newCoach(), req.newBerth(), req.chartPassengerId(), chartId);

        // Bump chart version
        jdbcTemplate.update("""
            UPDATE reservation_charts
            SET current_version = current_version + 1
            WHERE chart_id = ?
            """, chartId);

        return getChart(chartId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}

