package in.railconnect.core;

import in.railconnect.core.masterdata.MasterDataException;
import in.railconnect.core.masterdata.MasterDataModels.AccommodationInput;
import in.railconnect.core.masterdata.MasterDataModels.CoachClassRequest;
import in.railconnect.core.masterdata.MasterDataModels.CoachConfigurationRequest;
import in.railconnect.core.masterdata.MasterDataModels.CoachRequest;
import in.railconnect.core.masterdata.MasterDataModels.CoachTypeRequest;
import in.railconnect.core.masterdata.MasterDataModels.CompositionCoachInput;
import in.railconnect.core.masterdata.MasterDataModels.CompositionRequest;
import in.railconnect.core.masterdata.MasterDataModels.LookupRequest;
import in.railconnect.core.masterdata.MasterDataModels.RouteStopRequest;
import in.railconnect.core.masterdata.MasterDataModels.RouteVersionRequest;
import in.railconnect.core.masterdata.MasterDataModels.ScheduleRequest;
import in.railconnect.core.masterdata.MasterDataModels.Station;
import in.railconnect.core.masterdata.MasterDataModels.StationRequest;
import in.railconnect.core.masterdata.MasterDataModels.StationStatusRequest;
import in.railconnect.core.masterdata.MasterDataModels.StationVerificationRequest;
import in.railconnect.core.masterdata.MasterDataModels.TrainRequest;
import in.railconnect.core.masterdata.MasterDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RAILCONNECT_DB_URL", matches = "jdbc:postgresql:.+")
class MasterDataFlowIT {
    @Autowired
    private MasterDataService masterDataService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void createsPublishesAndAuditsRailwayMasterData() {
        UUID actor = null;
        Station origin = createStation("Origin");
        Station destination = createStation("Terminal");
        UUID originId = origin.stationId();

        assertThatThrownBy(() -> masterDataService.changeStationStatus(
                originId, new StationStatusRequest("ACTIVE", "test"), actor, null))
                .isInstanceOfSatisfying(MasterDataException.class, exception ->
                        assertThat(exception.code()).isEqualTo("STATION_CODE_NOT_VERIFIED"));
        assertThat(masterDataService.verifyStationCode(
                origin.stationId(), new StationVerificationRequest(true, "verified in test"), actor, null
        ).codeVerified()).isTrue();
        origin = masterDataService.changeStationStatus(
                origin.stationId(), new StationStatusRequest("ACTIVE", "test activation"), actor, null);
        assertThat(origin.status()).isEqualTo("ACTIVE");
        destination = masterDataService.verifyStationCode(
                destination.stationId(), new StationVerificationRequest(true, "verified in test"), actor, null);
        destination = masterDataService.changeStationStatus(
                destination.stationId(), new StationStatusRequest("ACTIVE", "test activation"), actor, null);

        RouteVersionRequest routeRequest = new RouteVersionRequest(
                "Integration test route",
                origin.stationId(),
                destination.stationId(),
                BigDecimal.TEN,
                LocalDate.now(),
                null,
                "test route",
                List.of(
                        new RouteStopRequest(
                                origin.stationId(), 1, null, LocalTime.of(8, 0), 0, 0,
                                0, BigDecimal.ZERO, "ORIGIN", null),
                        new RouteStopRequest(
                                destination.stationId(), 2, LocalTime.of(9, 0), null, 0, 0,
                                0, BigDecimal.TEN, "TERMINAL", null)
                )
        );
        var route = masterDataService.createRoute(routeRequest, actor, null);
        route = masterDataService.publishRouteVersion(route.routeVersionId(), actor, null);
        assertThat(route.status()).isEqualTo("PUBLISHED");

        var category = masterDataService.createCategory(
                new LookupRequest(uniqueCode("CAT"), "Test category " + UUID.randomUUID(), null), actor, null);
        var trainType = masterDataService.createTrainType(
                new LookupRequest(uniqueCode("TYPE"), "Test type " + UUID.randomUUID(), null), actor, null);
        var train = masterDataService.createTrain(
                new TrainRequest(
                        uniqueCode("T"),
                        "Test train " + UUID.randomUUID(),
                        category.id(),
                        trainType.id(),
                        origin.stationId(),
                        destination.stationId(),
                        route.routeId(),
                        BigDecimal.TEN,
                        "integration test",
                        LocalDate.now(),
                        null
                ),
                actor,
                null
        );
        train = masterDataService.changeTrainStatus(
                train.trainId(), new in.railconnect.core.masterdata.MasterDataModels.TrainStatusRequest(
                        "ACTIVE", "test activation"), actor, null);
        assertThat(train.status()).isEqualTo("ACTIVE");

        var schedule = masterDataService.createSchedule(
                new ScheduleRequest(
                        train.trainId(),
                        route.routeVersionId(),
                        "Asia/Kolkata",
                        LocalDate.now(),
                        null,
                        List.of(1, 3, 5),
                        List.of(),
                        "test schedule"
                ),
                actor,
                null
        );
        schedule = masterDataService.publishSchedule(schedule.scheduleId(), actor, null);
        assertThat(schedule.status()).isEqualTo("PUBLISHED");

        var coachClass = masterDataService.createCoachClass(
                new CoachClassRequest(
                        uniqueCode("CLASS"), "Test class " + UUID.randomUUID(), null,
                        "SEATING", "TEST", true
                ),
                actor,
                null
        );
        var coachType = masterDataService.createCoachType(
                new CoachTypeRequest(uniqueCode("COACH"), "Test coach type " + UUID.randomUUID(), null, true),
                actor,
                null
        );
        var configuration = masterDataService.createCoachConfiguration(
                new CoachConfigurationRequest(
                        uniqueCode("CFG"),
                        "Test configuration " + UUID.randomUUID(),
                        coachClass.id(),
                        coachType.id(),
                        2,
                        List.of(
                                new AccommodationInput("1", "SEAT", Map.of("row", 1, "column", 1), null, 1, true),
                                new AccommodationInput("2", "SEAT", Map.of("row", 1, "column", 2), null, 2, true)
                        ),
                        "test layout"
                ),
                actor,
                null
        );
        configuration = masterDataService.publishCoachLayout(configuration.configurationId(), actor, null);
        assertThat(configuration.layoutStatus()).isEqualTo("PUBLISHED");

        var coach = masterDataService.createCoach(
                new CoachRequest(uniqueCode("UNIT"), configuration.configurationId()), actor, null);
        var composition = masterDataService.saveComposition(
                train.trainId(),
                new CompositionRequest(
                        List.of(new CompositionCoachInput(coach.coachId(), "C1", 1)),
                        "test composition"
                ),
                actor,
                null
        );
        composition = masterDataService.publishComposition(
                train.trainId(), composition.currentVersionId(), actor, null);
        assertThat(composition.status()).isEqualTo("PUBLISHED");
        assertThat(composition.totalCapacity()).isEqualTo(2);
        String compositionSnapshotType = jdbcTemplate.queryForObject(
                "SELECT jsonb_typeof(snapshot) FROM train_composition_versions WHERE composition_version_id = ?",
                String.class,
                composition.currentVersionId()
        );
        Integer compositionSnapshotCoachCount = jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(snapshot -> 'coaches') FROM train_composition_versions "
                        + "WHERE composition_version_id = ?",
                Integer.class,
                composition.currentVersionId()
        );
        assertThat(compositionSnapshotType).isEqualTo("object");
        assertThat(compositionSnapshotCoachCount).isEqualTo(1);

        Integer stationVersions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM station_versions WHERE station_id = ?",
                Integer.class,
                origin.stationId()
        );
        Integer auditRecords = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM master_data_audit_logs",
                Integer.class
        );
        var stationAuditEntry = masterDataService.audit("STATION", origin.stationId(), 0, 10)
                .items().get(0);
        assertThat(stationVersions).isEqualTo(3);
        assertThat(auditRecords).isGreaterThanOrEqualTo(10);
        assertThat(stationAuditEntry.entityName()).contains(origin.stationName());
    }

    private Station createStation(String label) {
        String code = uniqueCode("S");
        return masterDataService.createStation(
                new StationRequest(
                        code,
                        label + " " + UUID.randomUUID(),
                        List.of(),
                        "Test city",
                        "Test state",
                        "India",
                        null,
                        null,
                        "Asia/Kolkata",
                        null,
                        null,
                        null,
                        null,
                        List.of()
                ),
                null,
                null
        );
    }

    private String uniqueCode(String prefix) {
        return (prefix + UUID.randomUUID().toString().replace("-", "")).substring(0, 20).toUpperCase();
    }
}
