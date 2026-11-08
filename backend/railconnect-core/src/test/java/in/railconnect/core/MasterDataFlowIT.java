package in.railconnect.core;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import in.railconnect.core.masterdata.MasterDataModels.ScheduleExceptionInput;
import in.railconnect.core.masterdata.MasterDataModels.ScheduleRequest;
import in.railconnect.core.masterdata.MasterDataModels.Station;
import in.railconnect.core.masterdata.MasterDataModels.StationRequest;
import in.railconnect.core.masterdata.MasterDataModels.StationStatusRequest;
import in.railconnect.core.masterdata.MasterDataModels.StationVerificationRequest;
import in.railconnect.core.masterdata.MasterDataModels.TrainRequest;
import in.railconnect.core.masterdata.MasterDataService;
import in.railconnect.core.fare.FareException;
import in.railconnect.core.fare.FareModels.FareQuoteRequest;
import in.railconnect.core.fare.FareModels.FareRuleVersionRequest;
import in.railconnect.core.fare.FareModels.PassengerRequest;
import in.railconnect.core.fare.FareService;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.inventory.InventoryException;
import in.railconnect.core.inventory.InventoryModels.BlockRequest;
import in.railconnect.core.inventory.InventoryModels.HoldRequest;
import in.railconnect.core.inventory.InventoryModels.InitializeRunRequest;
import in.railconnect.core.inventory.InventoryModels.JourneyRequest;
import in.railconnect.core.inventory.InventoryService;
import in.railconnect.core.search.PublicTrainSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RAILCONNECT_DB_URL", matches = "jdbc:postgresql:.+")
class MasterDataFlowIT {
    @Autowired
    private MasterDataService masterDataService;

    @Autowired
    private PublicTrainSearchService publicTrainSearchService;

    @Autowired
    private FareService fareService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional
    void createsPublishesAndAuditsRailwayMasterData() throws Exception {
        UUID actor = null;
        Station origin = createStation("Origin");
        Station intermediate = createStation("Midway");
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
        intermediate = masterDataService.verifyStationCode(
                intermediate.stationId(), new StationVerificationRequest(true, "verified in test"), actor, null);
        intermediate = masterDataService.changeStationStatus(
                intermediate.stationId(), new StationStatusRequest("ACTIVE", "test activation"), actor, null);

        LocalDate serviceMonday = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate runExceptionDate = serviceMonday.plusDays(1);
        LocalDate cancellationDate = serviceMonday.plusWeeks(1);
        RouteVersionRequest routeRequest = new RouteVersionRequest(
                "Integration test route",
                origin.stationId(),
                destination.stationId(),
                BigDecimal.valueOf(100),
                LocalDate.now(),
                null,
                "test route",
                List.of(
                        new RouteStopRequest(
                                origin.stationId(), 1, null, LocalTime.of(23, 30), 0, 0,
                                0, BigDecimal.ZERO, "ORIGIN", null),
                        new RouteStopRequest(
                                intermediate.stationId(), 2, LocalTime.of(0, 25), LocalTime.of(0, 30), 1, 1,
                                5, BigDecimal.valueOf(40), "REGULAR", null),
                        new RouteStopRequest(
                                destination.stationId(), 3, LocalTime.of(1, 30), null, 1, 1,
                                0, BigDecimal.valueOf(100), "TERMINAL", null)
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
                        BigDecimal.valueOf(100),
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
                        serviceMonday,
                        null,
                        List.of(serviceMonday.getDayOfWeek().getValue()),
                        List.of(
                                new ScheduleExceptionInput(runExceptionDate, "RUN", "test extra service"),
                                new ScheduleExceptionInput(cancellationDate, "CANCEL", "test cancellation")
                        ),
                        "test schedule"
                ),
                actor,
                null
        );
        schedule = masterDataService.publishSchedule(schedule.scheduleId(), actor, null);
        assertThat(schedule.status()).isEqualTo("PUBLISHED");
        var intermediateJourney = publicTrainSearchService.search(
                intermediate.stationId(),
                destination.stationId(),
                serviceMonday.plusDays(1),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "DEPARTURE_EARLIEST",
                0,
                20
        );
        assertThat(intermediateJourney.totalItems()).isEqualTo(1);
        assertThat(intermediateJourney.items().get(0).journeyDate()).isEqualTo(serviceMonday.plusDays(1));
        assertThat(intermediateJourney.items().get(0).arrivalDate()).isEqualTo(serviceMonday.plusDays(1));
        assertThat(intermediateJourney.items().get(0).durationMinutes()).isEqualTo(60);
        assertThat(publicTrainSearchService.search(
                destination.stationId(),
                intermediate.stationId(),
                serviceMonday.plusDays(1),
                null, null, null, null, null, null, null, null,
                "DEPARTURE_EARLIEST", 0, 20
        ).totalItems()).isZero();
        assertThat(publicTrainSearchService.search(
                origin.stationId(),
                destination.stationId(),
                runExceptionDate,
                null, null, null, null, null, null, null, null,
                "DEPARTURE_EARLIEST", 0, 20
        ).totalItems()).isEqualTo(1);
        assertThat(publicTrainSearchService.search(
                origin.stationId(),
                destination.stationId(),
                cancellationDate,
                null, null, null, null, null, null, null, null,
                "DEPARTURE_EARLIEST", 0, 20
        ).totalItems()).isZero();
        assertThat(publicTrainSearchService.searchStations(origin.stationCode(), 10))
                .anySatisfy(station -> assertThat(station.stationId()).isEqualTo(originId));
        assertThat(publicTrainSearchService.searchStations("Origin Junction", 10))
                .anySatisfy(station -> assertThat(station.stationId()).isEqualTo(originId));
        var citySuggestions = publicTrainSearchService.searchStations("Test city", 20);
        assertThat(citySuggestions).extracting(station -> station.stationId()).doesNotHaveDuplicates();

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

        var inventoryRun = inventoryService.initializeRun(
                new InitializeRunRequest(train.trainId(), serviceMonday), null);
        assertThat(inventoryRun.stops()).isEqualTo(3);
        assertThat(inventoryRun.coaches()).isEqualTo(1);
        assertThat(inventoryRun.berths()).isEqualTo(2);
        var inventoryOwner = createInventoryPassenger();
        var firstHoldRequest = new HoldRequest(
                inventoryRun.trainRunId(), origin.stationId(), intermediate.stationId(),
                coachClass.code(), null);
        var firstHold = inventoryService.createHold(firstHoldRequest, "inventory-hold-1", inventoryOwner.userId());
        var sameRequestRetry = inventoryService.createHold(
                firstHoldRequest, "inventory-hold-1", inventoryOwner.userId());
        assertThat(sameRequestRetry.inventoryHoldId()).isEqualTo(firstHold.inventoryHoldId());
        UUID inventoryOriginId = origin.stationId();
        UUID inventoryIntermediateId = intermediate.stationId();
        UUID inventoryDestinationId = destination.stationId();
        String inventoryClassCode = coachClass.code();
        assertThatThrownBy(() -> inventoryService.createHold(
                new HoldRequest(inventoryRun.trainRunId(), inventoryOriginId, inventoryDestinationId,
                        inventoryClassCode, null),
                "inventory-hold-1", inventoryOwner.userId()))
                .isInstanceOfSatisfying(InventoryException.class, exception ->
                        assertThat(exception.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
        var otherInventoryOwner = createInventoryPassenger();
        assertThatThrownBy(() -> inventoryService.releaseHold(firstHold.inventoryHoldId(),
                otherInventoryOwner.userId()))
                .isInstanceOfSatisfying(InventoryException.class, exception ->
                        assertThat(exception.code()).isEqualTo("HOLD_NOT_FOUND"));
        assertThat(inventoryService.availability(new JourneyRequest(
                inventoryRun.trainRunId(), origin.stationId(), intermediate.stationId(), coachClass.code()))
                .classes().get(0).available()).isEqualTo(1);
        assertThat(inventoryService.availability(new JourneyRequest(
                inventoryRun.trainRunId(), intermediate.stationId(), destination.stationId(), coachClass.code()))
                .classes().get(0).available()).isEqualTo(2);
        var secondHold = inventoryService.createHold(
                new HoldRequest(inventoryRun.trainRunId(), origin.stationId(), intermediate.stationId(),
                        coachClass.code(), null),
                "inventory-hold-2", inventoryOwner.userId());
        assertThat(inventoryService.availability(new JourneyRequest(
                inventoryRun.trainRunId(), origin.stationId(), intermediate.stationId(), coachClass.code()))
                .classes().get(0).available()).isZero();
        assertThatThrownBy(() -> inventoryService.createHold(
                new HoldRequest(inventoryRun.trainRunId(), inventoryOriginId, inventoryIntermediateId,
                        inventoryClassCode, null),
                "inventory-hold-3", inventoryOwner.userId()))
                .isInstanceOfSatisfying(InventoryException.class, exception ->
                        assertThat(exception.code()).isEqualTo("NO_BERTH_AVAILABLE"));
        inventoryService.releaseHold(firstHold.inventoryHoldId(), inventoryOwner.userId());
        jdbcTemplate.update(
                """
                UPDATE inventory_holds
                SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 minutes',
                    expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE inventory_hold_id = ?
                """,
                secondHold.inventoryHoldId());
        assertThat(inventoryService.holds(inventoryOwner.userId()))
                .filteredOn(hold -> hold.inventoryHoldId().equals(secondHold.inventoryHoldId()))
                .singleElement()
                .satisfies(hold -> assertThat(hold.status()).isEqualTo("EXPIRED"));
        inventoryService.createBlock(
                inventoryRun.trainRunId(),
                new BlockRequest(firstHold.inventoryBerthId(), origin.stationId(), intermediate.stationId(),
                        "MAINTENANCE", "Integration test maintenance"),
                inventoryOwner.userId());
        assertThat(inventoryService.availability(new JourneyRequest(
                inventoryRun.trainRunId(), origin.stationId(), intermediate.stationId(), coachClass.code()))
                .classes().get(0).available()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/public/inventory/availability")
                        .param("trainRunId", inventoryRun.trainRunId().toString())
                        .param("fromStationId", origin.stationId().toString())
                        .param("toStationId", intermediate.stationId().toString())
                        .param("coachClassCode", coachClass.code()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.classes[0].blocked").value(1));

        var filteredJourney = publicTrainSearchService.search(
                origin.stationId(),
                intermediate.stationId(),
                serviceMonday,
                category.code(),
                coachClass.code(),
                LocalTime.of(23, 0),
                LocalTime.of(23, 59),
                null,
                null,
                180,
                train.trainName(),
                "DURATION_LONGEST",
                0,
                20
        );
        assertThat(filteredJourney.totalItems()).isEqualTo(1);
        assertThat(filteredJourney.items().get(0).coachClasses()).singleElement()
                .satisfies(configuredClass -> assertThat(configuredClass.code()).isEqualTo(coachClass.code()));
        var trainDetail = publicTrainSearchService.detail(
                train.trainId(), origin.stationId(), destination.stationId(), serviceMonday);
        assertThat(trainDetail.route()).hasSize(3);
        assertThat(trainDetail.route()).filteredOn(stop -> stop.selectedOrigin()).hasSize(1);
        assertThat(trainDetail.route()).filteredOn(stop -> stop.selectedDestination()).hasSize(1);
        mockMvc.perform(get("/api/v1/public/trains/search")
                        .param("fromStationId", origin.stationId().toString())
                        .param("toStationId", intermediate.stationId().toString())
                        .param("journeyDate", serviceMonday.toString())
                        .param("coachClass", coachClass.code()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].trainId").value(train.trainId().toString()))
                .andExpect(jsonPath("$.data.items[0].coachClasses[0].code").value(coachClass.code()));
        mockMvc.perform(get("/api/v1/public/stations/search")
                        .param("q", origin.stationCode())
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stationId").value(originId.toString()));

        var overlappingSchedule = masterDataService.createSchedule(
                new ScheduleRequest(
                        train.trainId(),
                        route.routeVersionId(),
                        "Asia/Kolkata",
                        serviceMonday,
                        null,
                        List.of(serviceMonday.getDayOfWeek().getValue()),
                        List.of(),
                        "overlap regression"
                ),
                actor,
                null
        );
        assertThatThrownBy(() -> masterDataService.publishSchedule(
                overlappingSchedule.scheduleId(), actor, null))
                .isInstanceOfSatisfying(MasterDataException.class, exception ->
                        assertThat(exception.code()).isEqualTo("SCHEDULE_EFFECTIVE_DATES_OVERLAP"));

        var futureSchedule = masterDataService.createSchedule(
                new ScheduleRequest(
                        train.trainId(),
                        route.routeVersionId(),
                        "Asia/Kolkata",
                        serviceMonday.plusDays(7),
                        null,
                        List.of(serviceMonday.getDayOfWeek().getValue()),
                        List.of(),
                        "future schedule version"
                ),
                actor,
                null
        );
        masterDataService.publishSchedule(futureSchedule.scheduleId(), actor, null);
        LocalDate previousScheduleEnd = jdbcTemplate.queryForObject(
                "SELECT effective_to FROM train_schedules WHERE schedule_id = ?",
                LocalDate.class,
                schedule.scheduleId()
        );
        assertThat(previousScheduleEnd).isEqualTo(serviceMonday.plusDays(6));
        Integer effectiveRangeAuditEntries = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM master_data_audit_logs
                        WHERE entity_id = ? AND action = 'EFFECTIVE_RANGE_CLOSED'
                        """,
                Integer.class,
                schedule.scheduleId()
        );
        assertThat(effectiveRangeAuditEntries).isEqualTo(1);

        FareQuoteRequest quoteRequest = new FareQuoteRequest(
                train.trainId(),
                intermediate.stationId(),
                destination.stationId(),
                serviceMonday.plusDays(1),
                coachClass.code(),
                List.of(new PassengerRequest("ADULT", null))
        );
        assertThatThrownBy(() -> fareService.quote(quoteRequest, null))
                .isInstanceOfSatisfying(FareException.class, exception ->
                        assertThat(exception.code()).isEqualTo("FARE_CONFIGURATION_UNAVAILABLE"));

        RailConnectPrincipal fareAuthor = createFareActor("author");
        RailConnectPrincipal fareChecker = createFareActor("checker");
        FareRuleVersionRequest fareRule = new FareRuleVersionRequest(
                "Integration fare " + UUID.randomUUID(),
                "Configured only for integration test",
                train.trainId(),
                category.id(),
                coachClass.id(),
                "DISTANCE_RATE",
                BigDecimal.ZERO,
                new BigDecimal("2.0000"),
                10,
                serviceMonday,
                null,
                List.of(),
                List.of(),
                List.of()
        );
        var fareVersion = fareService.createRule(fareRule, fareAuthor);
        fareVersion = fareService.submit(fareVersion.fareRuleVersionId(), fareAuthor);
        assertThat(fareVersion.status()).isEqualTo("SUBMITTED");
        UUID fareVersionId = fareVersion.fareRuleVersionId();

        assertThatThrownBy(() -> fareService.approve(fareVersionId, fareAuthor))
                .isInstanceOfSatisfying(FareException.class, exception ->
                        assertThat(exception.code()).isEqualTo("FARE_RULE_MAKER_CHECKER_REQUIRED"));
        fareVersion = fareService.approve(fareVersionId, fareChecker);
        assertThat(fareVersion.status()).isEqualTo("APPROVED");
        fareVersion = fareService.publish(fareVersionId, fareChecker);
        assertThat(fareVersion.status()).isEqualTo("PUBLISHED");

        var quote = fareService.quote(quoteRequest, fareAuthor);
        assertThat(quote.status()).isEqualTo("ACTIVE");
        assertThat(quote.distanceKm()).isEqualByComparingTo("60.00");
        assertThat(quote.totalAmount()).isEqualByComparingTo("120.00");
        assertThat(fareService.quote(quote.fareQuoteId(), fareAuthor).totalAmount())
                .isEqualByComparingTo("120.00");
        assertThatThrownBy(() -> fareService.quote(quote.fareQuoteId(), fareChecker))
                .isInstanceOfSatisfying(FareException.class, exception ->
                        assertThat(exception.code()).isEqualTo("FARE_QUOTE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/public/fares/quotes/{quoteId}", quote.fareQuoteId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FARE_QUOTE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/admin/fares/rules"))
                .andExpect(status().isUnauthorized());
        var anonymousQuoteResult = mockMvc.perform(post("/api/v1/public/fares/quotes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.totalAmount").value(120.00))
                .andReturn();
        UUID anonymousQuoteId = UUID.fromString(objectMapper.readTree(
                anonymousQuoteResult.getResponse().getContentAsString())
                .path("data").path("fareQuoteId").asText());
        mockMvc.perform(get("/api/v1/public/fares/quotes/{quoteId}", anonymousQuoteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.totalAmount").value(120.00));
        Integer fareAuditEntries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fare_rule_audit_events WHERE fare_rule_version_id = ?",
                Integer.class,
                fareVersionId
        );
        Integer persistedQuoteCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fare_quotes WHERE fare_quote_id = ? AND total_amount = 120.00",
                Integer.class,
                quote.fareQuoteId()
        );
        assertThat(fareAuditEntries).isEqualTo(4);
        assertThat(persistedQuoteCount).isEqualTo(1);

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
                        List.of(label + " Junction"),
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

    private RailConnectPrincipal createFareActor(String label) {
        UUID userId = UUID.randomUUID();
        String unique = UUID.randomUUID().toString();
        String email = "fare-" + label + "-" + unique + "@example.test";
        jdbcTemplate.update("""
                INSERT INTO users (
                    user_id, full_name, email, mobile_number, password_hash,
                    terms_version, privacy_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                "Fare " + label + " " + unique.substring(0, 8),
                email,
                "+1" + unique.replace("-", "").substring(0, 10),
                "test-password-hash",
                "test-terms",
                "test-privacy");
        Collection<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("fares:rules:read"),
                new SimpleGrantedAuthority("fares:rules:manage"),
                new SimpleGrantedAuthority("fares:rules:approve"),
                new SimpleGrantedAuthority("fares:rules:publish"));
        return new RailConnectPrincipal(
                userId, email, "Fare " + label, "ACTIVE", false, authorities);
    }

    private RailConnectPrincipal createInventoryPassenger() {
        UUID userId = UUID.randomUUID();
        String unique = UUID.randomUUID().toString();
        String email = "inventory-" + unique + "@example.test";
        jdbcTemplate.update("""
                INSERT INTO users (
                    user_id, full_name, email, mobile_number, password_hash,
                    terms_version, privacy_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                "Inventory Passenger " + unique.substring(0, 8),
                email,
                "+2" + unique.replace("-", "").substring(0, 10),
                "test-password-hash",
                "test-terms",
                "test-privacy");
        return new RailConnectPrincipal(
                userId, email, "Inventory Passenger", "ACTIVE", false,
                List.of(
                        new SimpleGrantedAuthority("ROLE_PASSENGER"),
                        new SimpleGrantedAuthority("inventory:availability:read"),
                        new SimpleGrantedAuthority("inventory:holds:manage")
                ));
    }
}
