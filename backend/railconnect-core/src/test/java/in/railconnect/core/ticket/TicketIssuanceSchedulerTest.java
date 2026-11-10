package in.railconnect.core.ticket;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketIssuanceSchedulerTest {
    @Test
    void enqueuesEligibleLegacyBookingsAndProcessesDueJobs() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TicketIssuanceService tickets = mock(TicketIssuanceService.class);
        UUID bookingId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<UUID>>any())).thenAnswer(invocation ->
                invocation.<String>getArgument(0).contains("FROM bookings b")
                        ? List.of(bookingId) : List.of(jobId));

        new TicketIssuanceScheduler(jdbc, tickets).processPendingJobs();

        verify(tickets).enqueueConfirmedBooking(bookingId);
        verify(tickets).issueJob(jobId);
    }

    @Test
    void recordsSanitizedFailureAndLeavesJobForRetry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TicketIssuanceService tickets = mock(TicketIssuanceService.class);
        UUID jobId = UUID.randomUUID();
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<UUID>>any())).thenAnswer(invocation ->
                invocation.<String>getArgument(0).contains("FROM bookings b")
                        ? List.of() : List.of(jobId));
        doThrow(new IllegalStateException("database credentials must not be logged"))
                .when(tickets).issueJob(jobId);

        new TicketIssuanceScheduler(jdbc, tickets).processPendingJobs();

        verify(tickets).recordFailure(jobId, "TICKET_ISSUANCE_TEMPORARY_FAILURE");
    }

}
