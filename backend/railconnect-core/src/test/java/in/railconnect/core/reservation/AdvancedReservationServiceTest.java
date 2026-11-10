package in.railconnect.core.reservation;

import in.railconnect.core.reservation.AdvancedReservationModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdvancedReservationServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AdvancedReservationService reservationService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        reservationService = new AdvancedReservationService(jdbcTemplate);
    }

    @Test
    @DisplayName("listQuotas returns quota definitions")
    void testListQuotas() {
        QuotaView q1 = new QuotaView(UUID.randomUUID(), "GENERAL", "General Quota", "Standard quota", "{}", true);
        QuotaView q2 = new QuotaView(UUID.randomUUID(), "LADIES", "Ladies Quota", "Ladies only", "{}", true);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(q1, q2));

        List<QuotaView> quotas = reservationService.listQuotas();
        assertEquals(2, quotas.size());
        assertEquals("GENERAL", quotas.get(0).quotaCode());
    }

    @Test
    @DisplayName("configureQuotaAllocation enforces physical capacity limit")
    void testCapacityLimitExceeded() {
        UUID runId = UUID.randomUUID();
        // 50 physical berths
        when(jdbcTemplate.queryForObject(contains("COUNT(b.inventory_berth_id)"), eq(Integer.class), eq(runId), eq("3A")))
                .thenReturn(50);
        // 40 already allocated to other quotas
        when(jdbcTemplate.queryForObject(contains("COALESCE(SUM(allocated_berths)"), eq(Integer.class), eq(runId), eq("3A"), eq("LADIES")))
                .thenReturn(40);

        // Attempting to allocate 15 more berths (40 + 15 = 55 > 50)
        QuotaAllocationInput input = new QuotaAllocationInput(runId, "3A", "LADIES", 15, 5, 10);

        ReservationException ex = assertThrows(ReservationException.class, () ->
                reservationService.configureQuotaAllocation(input)
        );
        assertEquals("CAPACITY_EXCEEDED", ex.errorCode());
    }

    @Test
    @DisplayName("finalizeChart updates status and returns finalized view")
    void testFinalizeChart() {
        UUID chartId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();

        when(jdbcTemplate.update(contains("UPDATE reservation_charts"), eq(operatorId), eq(chartId))).thenReturn(1);

        ChartView mockChart = new ChartView(
                chartId, UUID.randomUUID(), "12301", "Howrah Rajdhani", "2026-11-15",
                "FINALIZED", 100, 90, 5, 5, 1, null, null, List.of()
        );
        when(jdbcTemplate.query(contains("SELECT rc.chart_id"), any(RowMapper.class), eq(chartId)))
                .thenReturn(List.of(mockChart));
        when(jdbcTemplate.query(contains("SELECT chart_passenger_id"), any(RowMapper.class), eq(chartId)))
                .thenReturn(List.of());

        ChartView result = reservationService.finalizeChart(chartId, operatorId);
        assertNotNull(result);
        assertEquals("FINALIZED", result.chartStatus());
    }
}

