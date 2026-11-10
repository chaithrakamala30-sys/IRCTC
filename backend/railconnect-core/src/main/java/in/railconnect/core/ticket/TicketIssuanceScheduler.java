package in.railconnect.core.ticket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TicketIssuanceScheduler {
    private final JdbcTemplate jdbc;
    private final TicketIssuanceService tickets;

    public TicketIssuanceScheduler(JdbcTemplate jdbc, TicketIssuanceService tickets) {
        this.jdbc = jdbc;
        this.tickets = tickets;
    }

    @Scheduled(fixedDelayString = "${railconnect.tickets.issuance-sweep-ms:10000}")
    public void processPendingJobs() {
        List<UUID> confirmedBookingsWithoutJobs = jdbc.query("""
                SELECT b.booking_id
                FROM bookings b
                WHERE b.status = 'CONFIRMED'
                  AND EXISTS (
                      SELECT 1 FROM payment_orders po
                      JOIN payment_status_history ph ON ph.payment_order_id = po.payment_order_id
                      WHERE po.booking_id = b.booking_id AND po.payment_status = 'SUCCEEDED'
                        AND ph.new_status = 'SUCCEEDED'
                        AND ph.source IN ('STATUS_QUERY', 'VERIFIED_WEBHOOK', 'RECONCILIATION')
                  )
                  AND (SELECT COUNT(*) FROM booking_passengers bp WHERE bp.booking_id = b.booking_id) > 0
                  AND (SELECT COUNT(*) FROM booking_passengers bp
                       JOIN reservation_allocations ra
                         ON ra.reservation_allocation_id = bp.reservation_allocation_id
                       WHERE bp.booking_id = b.booking_id AND ra.status = 'CONFIRMED')
                      = (SELECT COUNT(*) FROM booking_passengers bp WHERE bp.booking_id = b.booking_id)
                  AND NOT EXISTS (
                      SELECT 1 FROM ticket_issuance_jobs j WHERE j.booking_id = b.booking_id
                  )
                  AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.booking_id = b.booking_id)
                ORDER BY b.created_at, b.booking_id
                LIMIT 20
                """, (result, row) -> result.getObject(1, UUID.class));
        confirmedBookingsWithoutJobs.forEach(tickets::enqueueConfirmedBooking);

        List<UUID> jobs = jdbc.query("""
                SELECT ticket_issuance_job_id
                FROM ticket_issuance_jobs
                WHERE job_status IN ('PENDING', 'RETRY')
                  AND next_attempt_at <= CURRENT_TIMESTAMP
                  AND attempt_count < 10
                ORDER BY next_attempt_at, created_at
                LIMIT 20
                """, (result, row) -> result.getObject(1, UUID.class));
        for (UUID jobId : jobs) {
            try {
                tickets.issueJob(jobId);
            } catch (RuntimeException exception) {
                tickets.recordFailure(jobId, failureCode(exception));
            }
        }
    }

    private static String failureCode(RuntimeException exception) {
        if (exception instanceof TicketException ticketException) return ticketException.code();
        return "TICKET_ISSUANCE_TEMPORARY_FAILURE";
    }
}
