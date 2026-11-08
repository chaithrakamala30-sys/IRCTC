package in.railconnect.core.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.railconnect.core.reservation.ReservationModels.ReservationSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationServiceQueryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ReservationService service = new ReservationService(
            jdbc, new ObjectMapper(), new SimpleMeterRegistry(), 4, 10);

    @Test
    void allocationFailuresSeparatesStatusParameterFromGroupByClause() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertThat(sql).contains("request.status = ? GROUP BY");
            return List.of();
        }).when(jdbc).query(
                anyString(), ArgumentMatchers.<RowMapper<ReservationSummary>>any(), any(Object[].class));

        service.allocationFailures(0, 20);
    }
}
