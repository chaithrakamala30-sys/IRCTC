package in.railconnect.core.operations;

import in.railconnect.core.operations.OperationsModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrainOperationsControlServiceTest {

    private JdbcTemplate jdbcTemplate;
    private TrainOperationsControlService operationsService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        operationsService = new TrainOperationsControlService(jdbcTemplate);
    }

    @Test
    @DisplayName("Delay calculation compares scheduled and actual times")
    void testDelayCalculation() {
        Instant scheduled = Instant.parse("2026-11-10T10:00:00Z");
        Instant actual = Instant.parse("2026-11-10T10:35:00Z"); // 35 minutes delay

        UUID runId = UUID.randomUUID();
        UUID stationId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID updateId = UUID.randomUUID();

        StationOperationalUpdateInput input = new StationOperationalUpdateInput(
                runId, stationId, 2, "ARRIVAL", scheduled, actual, "1", 5, "Signal hold"
        );

        when(jdbcTemplate.queryForObject(contains("INSERT INTO station_operational_updates"), eq(UUID.class), anyVararg()))
                .thenReturn(updateId);
        when(jdbcTemplate.queryForObject(contains("SELECT jsonb_array_length"), eq(Integer.class), eq(runId)))
                .thenReturn(10); // 10 total stops -> stopIndex 2 is 30% progress

        StationOperationalUpdateView mockView = new StationOperationalUpdateView(
                updateId, runId, stationId, "NDLS", "New Delhi", 2, "ARRIVAL",
                scheduled, actual, 35, "1", 5, "Signal hold", "Station Controller", Instant.now()
        );
        when(jdbcTemplate.queryForObject(contains("FROM station_operational_updates"), any(RowMapper.class), eq(updateId)))
                .thenReturn(mockView);

        StationOperationalUpdateView result = operationsService.recordStationUpdate(input, staffId);

        assertNotNull(result);
        assertEquals(35, result.delayMinutes());
        assertEquals("ARRIVAL", result.eventType());
    }

    @Test
    @DisplayName("Platform conflict check detects overlapping slot")
    void testPlatformConflictDetected() {
        UUID stationId = UUID.randomUUID();
        UUID platformId = UUID.randomUUID();
        UUID runId1 = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID conflictAssignmentId = UUID.randomUUID();

        Instant arrival = Instant.parse("2026-11-10T14:00:00Z");
        Instant departure = Instant.parse("2026-11-10T14:30:00Z");

        PlatformAssignmentInput input = new PlatformAssignmentInput(stationId, platformId, runId1, arrival, departure);

        // Conflicting assignment exists
        when(jdbcTemplate.queryForList(contains("SELECT assignment_id FROM platform_assignments"), eq(UUID.class), anyVararg()))
                .thenReturn(List.of(conflictAssignmentId));

        PlatformAssignmentView mockView = new PlatformAssignmentView(
                UUID.randomUUID(), stationId, platformId, "1", runId1, "12301", "Rajdhani Express",
                arrival, departure, null, null, "CONFLICT", true
        );
        when(jdbcTemplate.queryForObject(contains("FROM platform_assignments"), any(RowMapper.class), any(UUID.class)))
                .thenReturn(mockView);

        PlatformAssignmentView result = operationsService.assignPlatform(input, staffId);
        assertNotNull(result);
        assertTrue(result.conflictFlag());
        assertEquals("CONFLICT", result.assignmentStatus());
    }
}

