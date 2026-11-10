package in.railconnect.core.support;

import in.railconnect.core.notification.NotificationModels.NotificationCategory;
import in.railconnect.core.notification.NotificationModels.NotificationPriority;
import in.railconnect.core.notification.NotificationService;
import in.railconnect.core.support.SupportModels.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SupportTicketService {

    private static final Logger log = LoggerFactory.getLogger(SupportTicketService.class);

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;

    public SupportTicketService(JdbcTemplate jdbcTemplate, NotificationService notificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
    }

    // ==========================================
    // 1. CATEGORIES
    // ==========================================

    public List<SupportCategoryView> getCategories() {
        String sql = "SELECT category_id, category_code, display_name, department, default_priority, sla_response_hours, sla_resolution_hours FROM support_categories WHERE is_active = true ORDER BY display_name ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SupportCategoryView(
                (UUID) rs.getObject("category_id"),
                rs.getString("category_code"),
                rs.getString("display_name"),
                rs.getString("department"),
                rs.getString("default_priority"),
                rs.getInt("sla_response_hours"),
                rs.getInt("sla_resolution_hours")
        ));
    }

    // ==========================================
    // 2. PASSENGER TICKET ACTIONS
    // ==========================================

    @Transactional
    public UUID createTicket(UUID passengerUserId, CreateTicketRequest req) {
        UUID ticketId = UUID.randomUUID();
        String ref = "RC-SUP-" + System.currentTimeMillis() % 1000000;

        // Fetch category SLA hours
        int slaResponseHours = 4;
        int slaResolutionHours = 24;
        String priority = req.priority() != null ? req.priority().toUpperCase() : "NORMAL";

        try {
            Map<String, Object> cat = jdbcTemplate.queryForMap(
                    "SELECT sla_response_hours, sla_resolution_hours, default_priority FROM support_categories WHERE category_id = ?",
                    req.categoryId()
            );
            slaResponseHours = ((Number) cat.get("sla_response_hours")).intValue();
            slaResolutionHours = ((Number) cat.get("sla_resolution_hours")).intValue();
            if (req.priority() == null) {
                priority = (String) cat.get("default_priority");
            }
        } catch (Exception ignored) {}

        Instant now = Instant.now();
        Instant targetResponse = now.plus(slaResponseHours, ChronoUnit.HOURS);
        Instant targetResolution = now.plus(slaResolutionHours, ChronoUnit.HOURS);

        String insertSql = """
            INSERT INTO support_tickets (
                ticket_id, ticket_reference, passenger_user_id, category_id, subject, description,
                priority, status, linked_booking_id, linked_payment_id, linked_ticket_id, linked_train_run_id,
                sla_target_response_at, sla_target_resolution_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        jdbcTemplate.update(insertSql,
                ticketId, ref, passengerUserId, req.categoryId(), req.subject(), req.description(),
                priority, req.linkedBookingId(), req.linkedPaymentId(), req.linkedTicketId(), req.linkedTrainRunId(),
                java.sql.Timestamp.from(targetResponse), java.sql.Timestamp.from(targetResolution),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
        );

        // Insert initial message
        addMessage(ticketId, passengerUserId, "PASSENGER", req.description(), false);

        // Record history
        recordHistory(ticketId, null, "OPEN", passengerUserId, "Ticket created by passenger");

        // Notify Passenger (Confirmation of ticket received)
        notificationService.createAndDispatchNotification(
                passengerUserId,
                NotificationCategory.SYSTEM,
                "SUPPORT_TICKET_CREATED",
                "Support Request Received: " + ref,
                "Your request regarding '" + req.subject() + "' has been logged under reference " + ref + ". Our support team will review it shortly.",
                NotificationPriority.NORMAL,
                "/support",
                Map.of("ticketId", ticketId.toString(), "reference", ref)
        );

        return ticketId;
    }

    public List<SupportTicketSummaryView> getPassengerTickets(UUID passengerUserId) {
        String sql = """
            SELECT t.ticket_id, t.ticket_reference, t.passenger_user_id, u.full_name AS passenger_name,
                   c.display_name AS category_name, t.subject, t.priority, t.status,
                   staff.full_name AS staff_name, t.sla_target_resolution_at,
                   t.created_at, t.updated_at
            FROM support_tickets t
            JOIN users u ON t.passenger_user_id = u.user_id
            JOIN support_categories c ON t.category_id = c.category_id
            LEFT JOIN users staff ON t.assigned_staff_id = staff.user_id
            WHERE t.passenger_user_id = ?
            ORDER BY t.created_at DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapSummaryView(rs), passengerUserId);
    }

    public SupportTicketDetailView getTicketDetails(UUID ticketId, UUID requesterUserId, boolean isStaff) {
        String ticketSql = """
            SELECT t.ticket_id, t.ticket_reference, t.passenger_user_id, u.full_name AS passenger_name, u.email AS passenger_email,
                   t.category_id, c.display_name AS category_name, t.subject, t.description, t.priority, t.status,
                   t.assigned_staff_id, staff.full_name AS staff_name,
                   t.assigned_team_id, team.team_name,
                   t.linked_booking_id, b.booking_reference,
                   t.linked_payment_id,
                   t.linked_ticket_id, tk.pnr,
                   t.linked_train_run_id,
                   t.sla_target_response_at, t.sla_target_resolution_at,
                   t.resolved_at, t.closed_at, t.created_at, t.updated_at
            FROM support_tickets t
            JOIN users u ON t.passenger_user_id = u.user_id
            JOIN support_categories c ON t.category_id = c.category_id
            LEFT JOIN users staff ON t.assigned_staff_id = staff.user_id
            LEFT JOIN support_teams team ON t.assigned_team_id = team.team_id
            LEFT JOIN bookings b ON t.linked_booking_id = b.booking_id
            LEFT JOIN tickets tk ON t.linked_ticket_id = tk.ticket_id
            WHERE t.ticket_id = ?
        """;

        List<SupportTicketDetailView> list = jdbcTemplate.query(ticketSql, (rs, rowNum) -> {
            UUID passengerId = (UUID) rs.getObject("passenger_user_id");

            // Privacy authorization check
            if (!isStaff && !passengerId.equals(requesterUserId)) {
                throw new RuntimeException("Unauthorized: You can only view your own support tickets.");
            }

            Instant targetResolution = rs.getTimestamp("sla_target_resolution_at") != null ? rs.getTimestamp("sla_target_resolution_at").toInstant() : null;
            boolean isBreached = targetResolution != null && Instant.now().isAfter(targetResolution) && !List.of("RESOLVED", "CLOSED").contains(rs.getString("status"));

            // Retrieve messages (strictly filter out internal notes for passengers)
            List<SupportMessageView> messages = getMessages(ticketId, isStaff);

            return new SupportTicketDetailView(
                    (UUID) rs.getObject("ticket_id"),
                    rs.getString("ticket_reference"),
                    passengerId,
                    rs.getString("passenger_name"),
                    rs.getString("passenger_email"),
                    (UUID) rs.getObject("category_id"),
                    rs.getString("category_name"),
                    rs.getString("subject"),
                    rs.getString("description"),
                    TicketPriority.valueOf(rs.getString("priority")),
                    TicketStatus.valueOf(rs.getString("status")),
                    (UUID) rs.getObject("assigned_staff_id"),
                    rs.getString("staff_name"),
                    (UUID) rs.getObject("assigned_team_id"),
                    rs.getString("team_name"),
                    (UUID) rs.getObject("linked_booking_id"),
                    rs.getString("booking_reference"),
                    (UUID) rs.getObject("linked_payment_id"),
                    (UUID) rs.getObject("linked_ticket_id"),
                    rs.getString("pnr"),
                    (UUID) rs.getObject("linked_train_run_id"),
                    rs.getTimestamp("sla_target_response_at") != null ? rs.getTimestamp("sla_target_response_at").toInstant() : null,
                    targetResolution,
                    isBreached,
                    rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toInstant() : null,
                    rs.getTimestamp("closed_at") != null ? rs.getTimestamp("closed_at").toInstant() : null,
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    messages
            );
        }, ticketId);

        if (list.isEmpty()) {
            throw new RuntimeException("Support ticket not found.");
        }
        return list.get(0);
    }

    // ==========================================
    // 3. MESSAGING & INTERNAL NOTES
    // ==========================================

    @Transactional
    public void addMessage(UUID ticketId, UUID senderUserId, String senderType, String body, boolean isInternalNote) {
        UUID messageId = UUID.randomUUID();
        String sql = """
            INSERT INTO support_ticket_messages (message_id, ticket_id, sender_user_id, sender_type, is_internal_note, message_body, created_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;
        jdbcTemplate.update(sql, messageId, ticketId, senderUserId, senderType, isInternalNote, body);

        // Update ticket updated_at
        jdbcTemplate.update("UPDATE support_tickets SET updated_at = CURRENT_TIMESTAMP WHERE ticket_id = ?", ticketId);

        // If staff replied (not internal note), mark first response time and notify passenger
        if ("STAFF".equalsIgnoreCase(senderType) && !isInternalNote) {
            jdbcTemplate.update("""
                UPDATE support_tickets SET
                    first_responded_at = COALESCE(first_responded_at, CURRENT_TIMESTAMP),
                    status = CASE WHEN status = 'OPEN' THEN 'IN_PROGRESS' ELSE status END
                WHERE ticket_id = ?
            """, ticketId);

            // Fetch passenger user ID & reference
            try {
                Map<String, Object> t = jdbcTemplate.queryForMap(
                        "SELECT passenger_user_id, ticket_reference FROM support_tickets WHERE ticket_id = ?", ticketId
                );
                UUID pId = (UUID) t.get("passenger_user_id");
                String ref = (String) t.get("ticket_reference");

                notificationService.createAndDispatchNotification(
                        pId,
                        NotificationCategory.SYSTEM,
                        "SUPPORT_REPLY_RECEIVED",
                        "New Message on " + ref,
                        "Support staff has responded to your ticket. Tap to view the reply.",
                        NotificationPriority.HIGH,
                        "/support",
                        Map.of("ticketId", ticketId.toString(), "reference", ref)
                );
            } catch (Exception e) {
                log.error("Failed to notify passenger of staff reply", e);
            }
        }
    }

    public List<SupportMessageView> getMessages(UUID ticketId, boolean includeInternalNotes) {
        String sql = """
            SELECT m.message_id, m.ticket_id, m.sender_user_id, u.full_name AS sender_name,
                   m.sender_type, m.is_internal_note, m.message_body, m.created_at
            FROM support_ticket_messages m
            JOIN users u ON m.sender_user_id = u.user_id
            WHERE m.ticket_id = ?
        """ + (includeInternalNotes ? "" : " AND m.is_internal_note = false") + " ORDER BY m.created_at ASC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> new SupportMessageView(
                (UUID) rs.getObject("message_id"),
                (UUID) rs.getObject("ticket_id"),
                (UUID) rs.getObject("sender_user_id"),
                rs.getString("sender_name"),
                rs.getString("sender_type"),
                rs.getBoolean("is_internal_note"),
                rs.getString("message_body"),
                rs.getTimestamp("created_at").toInstant()
        ), ticketId);
    }

    // ==========================================
    // 4. STAFF ACTIONS & WORKSPACE
    // ==========================================

    public List<SupportTicketSummaryView> getStaffQueue(String filter, UUID staffUserId) {
        StringBuilder sql = new StringBuilder("""
            SELECT t.ticket_id, t.ticket_reference, t.passenger_user_id, u.full_name AS passenger_name,
                   c.display_name AS category_name, t.subject, t.priority, t.status,
                   staff.full_name AS staff_name, t.sla_target_resolution_at,
                   t.created_at, t.updated_at
            FROM support_tickets t
            JOIN users u ON t.passenger_user_id = u.user_id
            JOIN support_categories c ON t.category_id = c.category_id
            LEFT JOIN users staff ON t.assigned_staff_id = staff.user_id
            WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if ("MY_ASSIGNED".equalsIgnoreCase(filter)) {
            sql.append(" AND t.assigned_staff_id = ? AND t.status NOT IN ('RESOLVED', 'CLOSED')");
            params.add(staffUserId);
        } else if ("UNASSIGNED".equalsIgnoreCase(filter)) {
            sql.append(" AND t.assigned_staff_id IS NULL AND t.status NOT IN ('RESOLVED', 'CLOSED')");
        } else if ("ESCALATED".equalsIgnoreCase(filter)) {
            sql.append(" AND t.status = 'ESCALATED'");
        } else if ("HIGH_CRITICAL".equalsIgnoreCase(filter)) {
            sql.append(" AND t.priority IN ('HIGH', 'URGENT', 'CRITICAL') AND t.status NOT IN ('RESOLVED', 'CLOSED')");
        } else {
            // ALL active
            sql.append(" AND t.status NOT IN ('RESOLVED', 'CLOSED')");
        }

        sql.append(" ORDER BY t.priority DESC, t.created_at ASC LIMIT 50");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapSummaryView(rs), params.toArray());
    }

    @Transactional
    public void assignTicket(UUID ticketId, UUID staffUserId, UUID assignedByUserId) {
        jdbcTemplate.update("""
            UPDATE support_tickets SET
                assigned_staff_id = ?,
                status = CASE WHEN status = 'OPEN' THEN 'ASSIGNED' ELSE status END,
                updated_at = CURRENT_TIMESTAMP
            WHERE ticket_id = ?
        """, staffUserId, ticketId);

        recordHistory(ticketId, "OPEN", "ASSIGNED", assignedByUserId, "Assigned to staff " + staffUserId);
    }

    @Transactional
    public void updateTicketStatus(UUID ticketId, TicketStatus newStatus, String reason, UUID staffUserId) {
        String prev = jdbcTemplate.queryForObject("SELECT status FROM support_tickets WHERE ticket_id = ?", String.class, ticketId);

        Instant resolvedAt = newStatus == TicketStatus.RESOLVED ? Instant.now() : null;
        Instant closedAt = newStatus == TicketStatus.CLOSED ? Instant.now() : null;

        jdbcTemplate.update("""
            UPDATE support_tickets SET
                status = ?,
                resolved_at = COALESCE(?, resolved_at),
                closed_at = COALESCE(?, closed_at),
                updated_at = CURRENT_TIMESTAMP
            WHERE ticket_id = ?
        """, newStatus.name(), resolvedAt != null ? java.sql.Timestamp.from(resolvedAt) : null,
             closedAt != null ? java.sql.Timestamp.from(closedAt) : null, ticketId);

        recordHistory(ticketId, prev, newStatus.name(), staffUserId, reason);

        // Notify passenger of status resolution
        if (newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.CLOSED) {
            try {
                Map<String, Object> t = jdbcTemplate.queryForMap(
                        "SELECT passenger_user_id, ticket_reference FROM support_tickets WHERE ticket_id = ?", ticketId
                );
                UUID pId = (UUID) t.get("passenger_user_id");
                String ref = (String) t.get("ticket_reference");

                notificationService.createAndDispatchNotification(
                        pId,
                        NotificationCategory.SYSTEM,
                        "SUPPORT_TICKET_RESOLVED",
                        "Ticket Resolved: " + ref,
                        "Your ticket " + ref + " has been marked as resolved. Please submit feedback to let us know how we did.",
                        NotificationPriority.HIGH,
                        "/support",
                        Map.of("ticketId", ticketId.toString(), "reference", ref)
                );
            } catch (Exception ignored) {}
        }
    }

    @Transactional
    public void escalateTicket(UUID ticketId, int level, String reason, UUID staffUserId) {
        jdbcTemplate.update("""
            UPDATE support_tickets SET
                status = 'ESCALATED',
                priority = 'CRITICAL',
                updated_at = CURRENT_TIMESTAMP
            WHERE ticket_id = ?
        """, ticketId);

        jdbcTemplate.update("""
            INSERT INTO support_escalations (escalation_id, ticket_id, escalation_level, escalated_by_user_id, reason, occurred_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """, UUID.randomUUID(), ticketId, level, staffUserId, reason);

        recordHistory(ticketId, "IN_PROGRESS", "ESCALATED", staffUserId, "Escalated to level " + level + ": " + reason);
    }

    @Transactional
    public void submitFeedback(UUID ticketId, UUID passengerUserId, SupportFeedbackRequest req) {
        String sql = """
            INSERT INTO support_feedback (feedback_id, ticket_id, passenger_user_id, satisfaction_rating, comments, created_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (ticket_id) DO UPDATE SET
                satisfaction_rating = EXCLUDED.satisfaction_rating,
                comments = EXCLUDED.comments
        """;
        jdbcTemplate.update(sql, UUID.randomUUID(), ticketId, passengerUserId, req.satisfactionRating(), req.comments());
    }

    // ==========================================
    // 5. DASHBOARD STATS (ADMIN & SUPERVISOR)
    // ==========================================

    public SupportDashboardStatsView getDashboardStats(UUID staffUserId) {
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_tickets", Long.class);
        long open = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_tickets WHERE status = 'OPEN'", Long.class);
        long unassigned = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_tickets WHERE assigned_staff_id IS NULL AND status NOT IN ('RESOLVED', 'CLOSED')", Long.class);
        long myAssigned = staffUserId != null ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_tickets WHERE assigned_staff_id = ? AND status NOT IN ('RESOLVED', 'CLOSED')", Long.class, staffUserId) : 0L;
        long escalated = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_tickets WHERE status = 'ESCALATED'", Long.class);
        long resolved = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_tickets WHERE status IN ('RESOLVED', 'CLOSED')", Long.class);
        long breached = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_tickets WHERE sla_target_resolution_at < CURRENT_TIMESTAMP AND status NOT IN ('RESOLVED', 'CLOSED')", Long.class);

        Double avgScore = jdbcTemplate.queryForObject("SELECT COALESCE(AVG(satisfaction_rating), 5.0) FROM support_feedback", Double.class);

        return new SupportDashboardStatsView(
                total, open, unassigned, myAssigned, escalated, resolved, breached,
                avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 5.0
        );
    }

    private void recordHistory(UUID ticketId, String prevStatus, String newStatus, UUID userId, String reason) {
        jdbcTemplate.update("""
            INSERT INTO support_ticket_status_history (history_id, ticket_id, previous_status, new_status, changed_by_user_id, reason, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """, UUID.randomUUID(), ticketId, prevStatus, newStatus, userId, reason);
    }

    private SupportTicketSummaryView mapSummaryView(ResultSet rs) throws SQLException {
        Instant targetRes = rs.getTimestamp("sla_target_resolution_at") != null ? rs.getTimestamp("sla_target_resolution_at").toInstant() : null;
        boolean breached = targetRes != null && Instant.now().isAfter(targetRes) && !List.of("RESOLVED", "CLOSED").contains(rs.getString("status"));

        return new SupportTicketSummaryView(
                (UUID) rs.getObject("ticket_id"),
                rs.getString("ticket_reference"),
                (UUID) rs.getObject("passenger_user_id"),
                rs.getString("passenger_name"),
                rs.getString("category_name"),
                rs.getString("subject"),
                TicketPriority.valueOf(rs.getString("priority")),
                TicketStatus.valueOf(rs.getString("status")),
                rs.getString("staff_name"),
                targetRes,
                breached,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
