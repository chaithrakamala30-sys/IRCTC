package in.railconnect.core.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.railconnect.core.booking.BookingModels.BookingSummary;
import in.railconnect.core.fare.FareService;
import in.railconnect.core.reservation.ReservationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingServiceQueryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BookingService service = new BookingService(
            jdbc,
            new ObjectMapper(),
            mock(FareService.class),
            mock(ReservationService.class),
            mock(BookingRateLimiter.class),
            new SimpleMeterRegistry(),
            Duration.ofMinutes(10));

    @Test
    void historySeparatesOwnerParameterFromOrderByClause() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("booking_reference")) {
                assertThat(sql).contains("owner_user_id = ? ORDER BY");
            }
            return List.of();
        }).when(jdbc).query(
                anyString(), ArgumentMatchers.<RowMapper<BookingSummary>>any(), any(Object[].class));

        service.history(UUID.randomUUID(), null, null, 0, 10);
    }
}
