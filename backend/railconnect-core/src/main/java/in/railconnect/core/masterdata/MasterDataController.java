package in.railconnect.core.masterdata;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.identity.IdentityService;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.masterdata.MasterDataModels.AuditEntry;
import in.railconnect.core.masterdata.MasterDataModels.CoachClassRequest;
import in.railconnect.core.masterdata.MasterDataModels.CoachConfiguration;
import in.railconnect.core.masterdata.MasterDataModels.CoachConfigurationRequest;
import in.railconnect.core.masterdata.MasterDataModels.CoachLayoutRequest;
import in.railconnect.core.masterdata.MasterDataModels.CoachRequest;
import in.railconnect.core.masterdata.MasterDataModels.CoachSummary;
import in.railconnect.core.masterdata.MasterDataModels.CoachTypeRequest;
import in.railconnect.core.masterdata.MasterDataModels.Composition;
import in.railconnect.core.masterdata.MasterDataModels.CompositionRequest;
import in.railconnect.core.masterdata.MasterDataModels.DashboardStats;
import in.railconnect.core.masterdata.MasterDataModels.Lookup;
import in.railconnect.core.masterdata.MasterDataModels.LookupRequest;
import in.railconnect.core.masterdata.MasterDataModels.PageResult;
import in.railconnect.core.masterdata.MasterDataModels.RouteVersion;
import in.railconnect.core.masterdata.MasterDataModels.RouteVersionRequest;
import in.railconnect.core.masterdata.MasterDataModels.Schedule;
import in.railconnect.core.masterdata.MasterDataModels.ScheduleRequest;
import in.railconnect.core.masterdata.MasterDataModels.Station;
import in.railconnect.core.masterdata.MasterDataModels.StationRequest;
import in.railconnect.core.masterdata.MasterDataModels.StationStatusRequest;
import in.railconnect.core.masterdata.MasterDataModels.StationVerificationRequest;
import in.railconnect.core.masterdata.MasterDataModels.StationVersion;
import in.railconnect.core.masterdata.MasterDataModels.Train;
import in.railconnect.core.masterdata.MasterDataModels.TrainRequest;
import in.railconnect.core.masterdata.MasterDataModels.TrainStatusRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/master-data")
public class MasterDataController {
    private final MasterDataService masterDataService;
    private final IdentityService identityService;

    public MasterDataController(MasterDataService masterDataService, IdentityService identityService) {
        this.masterDataService = masterDataService;
        this.identityService = identityService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<DashboardStats> dashboard() {
        return success(masterDataService.dashboard());
    }

    @GetMapping("/stations")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<PageResult<Station>> stations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(required = false) @Size(max = 120) String city,
            @RequestParam(required = false) @Size(max = 120) String state,
            @RequestParam(required = false) @Size(max = 16) String status
    ) {
        return success(masterDataService.stations(page, size, search, city, state, status));
    }

    @GetMapping("/stations/{stationId}")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<Station> station(@PathVariable UUID stationId) {
        return success(masterDataService.station(stationId));
    }

    @GetMapping("/stations/{stationId}/versions")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<List<StationVersion>> stationVersions(@PathVariable UUID stationId) {
        return success(masterDataService.stationVersions(stationId));
    }

    @PostMapping("/stations")
    @PreAuthorize("hasAuthority('operations:stations:manage')")
    public ApiEnvelope<Station> createStation(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @Valid @RequestBody StationRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createStation(request, actor.userId(), servletRequest));
    }

    @PutMapping("/stations/{stationId}")
    @PreAuthorize("hasAuthority('operations:stations:manage')")
    public ApiEnvelope<Station> updateStation(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID stationId,
            @Valid @RequestBody StationRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.updateStation(stationId, request, actor.userId(), servletRequest));
    }

    @PatchMapping("/stations/{stationId}/status")
    @PreAuthorize("hasAuthority('operations:stations:manage')")
    public ApiEnvelope<Station> stationStatus(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID stationId,
            @Valid @RequestBody StationStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.changeStationStatus(stationId, request, actor.userId(), servletRequest));
    }

    @PatchMapping("/stations/{stationId}/verification")
    @PreAuthorize("hasAuthority('operations:stations:manage')")
    public ApiEnvelope<Station> stationVerification(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID stationId,
            @Valid @RequestBody StationVerificationRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.verifyStationCode(stationId, request, actor.userId(), servletRequest));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<List<Lookup>> categories() {
        return success(masterDataService.categories());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('operations:trains:manage')")
    public ApiEnvelope<Lookup> createCategory(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @Valid @RequestBody LookupRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createCategory(request, actor.userId(), servletRequest));
    }

    @GetMapping("/train-types")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<List<Lookup>> trainTypes() {
        return success(masterDataService.trainTypes());
    }

    @PostMapping("/train-types")
    @PreAuthorize("hasAuthority('operations:trains:manage')")
    public ApiEnvelope<Lookup> createTrainType(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @Valid @RequestBody LookupRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createTrainType(request, actor.userId(), servletRequest));
    }

    @GetMapping("/trains")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<PageResult<Train>> trains(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @Size(max = 16) String status
    ) {
        return success(masterDataService.trains(page, size, search, categoryId, status));
    }

    @GetMapping("/trains/{trainId}")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<Train> train(@PathVariable UUID trainId) {
        return success(masterDataService.train(trainId));
    }

    @PostMapping("/trains")
    @PreAuthorize("hasAuthority('operations:trains:manage')")
    public ApiEnvelope<Train> createTrain(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @Valid @RequestBody TrainRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createTrain(request, actor.userId(), servletRequest));
    }

    @PutMapping("/trains/{trainId}")
    @PreAuthorize("hasAuthority('operations:trains:manage')")
    public ApiEnvelope<Train> updateTrain(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID trainId,
            @Valid @RequestBody TrainRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.updateTrain(trainId, request, actor.userId(), servletRequest));
    }

    @PatchMapping("/trains/{trainId}/status")
    @PreAuthorize("hasAuthority('operations:trains:manage')")
    public ApiEnvelope<Train> trainStatus(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID trainId,
            @Valid @RequestBody TrainStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.changeTrainStatus(trainId, request, actor.userId(), servletRequest));
    }

    @GetMapping("/routes")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<PageResult<RouteVersion>> routes(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 160) String search,
            @RequestParam(required = false) @Size(max = 16) String status
    ) {
        return success(masterDataService.routes(page, size, search, status));
    }

    @GetMapping("/route-versions/{routeVersionId}")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<RouteVersion> routeVersion(@PathVariable UUID routeVersionId) {
        return success(masterDataService.routeVersion(routeVersionId));
    }

    @PostMapping("/routes")
    @PreAuthorize("hasAuthority('operations:routes:manage')")
    public ApiEnvelope<RouteVersion> createRoute(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @Valid @RequestBody RouteVersionRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createRoute(request, actor.userId(), servletRequest));
    }

    @PostMapping("/routes/{routeId}/versions")
    @PreAuthorize("hasAuthority('operations:routes:manage')")
    public ApiEnvelope<RouteVersion> createRouteVersion(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID routeId,
            @Valid @RequestBody RouteVersionRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createRouteVersion(routeId, request, actor.userId(), servletRequest));
    }

    @PostMapping("/route-versions/{routeVersionId}/publish")
    @PreAuthorize("hasAuthority('operations:master-data:publish')")
    public ApiEnvelope<RouteVersion> publishRoute(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID routeVersionId,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.publishRouteVersion(routeVersionId, actor.userId(), servletRequest));
    }

    @GetMapping("/schedules")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<PageResult<Schedule>> schedules(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) UUID trainId,
            @RequestParam(required = false) @Size(max = 16) String status
    ) {
        return success(masterDataService.schedules(page, size, trainId, status));
    }

    @GetMapping("/schedules/{scheduleId}")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<Schedule> schedule(@PathVariable UUID scheduleId) {
        return success(masterDataService.schedule(scheduleId));
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasAuthority('operations:schedules:manage')")
    public ApiEnvelope<Schedule> createSchedule(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @Valid @RequestBody ScheduleRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createSchedule(request, actor.userId(), servletRequest));
    }

    @PostMapping("/schedules/{scheduleId}/publish")
    @PreAuthorize("hasAuthority('operations:master-data:publish')")
    public ApiEnvelope<Schedule> publishSchedule(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID scheduleId,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.publishSchedule(scheduleId, actor.userId(), servletRequest));
    }

    @GetMapping("/coach-classes")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<List<Lookup>> coachClasses() {
        return success(masterDataService.coachClasses());
    }

    @PostMapping("/coach-classes")
    @PreAuthorize("hasAuthority('operations:composition:manage')")
    public ApiEnvelope<Lookup> createCoachClass(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @Valid @RequestBody CoachClassRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createCoachClass(request, actor.userId(), servletRequest));
    }

    @GetMapping("/coach-types")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<List<Lookup>> coachTypes() {
        return success(masterDataService.coachTypes());
    }

    @PostMapping("/coach-types")
    @PreAuthorize("hasAuthority('operations:composition:manage')")
    public ApiEnvelope<Lookup> createCoachType(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @Valid @RequestBody CoachTypeRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createCoachType(request, actor.userId(), servletRequest));
    }

    @GetMapping("/coach-configurations")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<List<CoachConfiguration>> coachConfigurations() {
        return success(masterDataService.coachConfigurations());
    }

    @GetMapping("/coach-configurations/{configurationId}")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<CoachConfiguration> coachConfiguration(@PathVariable UUID configurationId) {
        return success(masterDataService.coachConfiguration(configurationId));
    }

    @PostMapping("/coach-configurations")
    @PreAuthorize("hasAuthority('operations:composition:manage')")
    public ApiEnvelope<CoachConfiguration> createCoachConfiguration(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @Valid @RequestBody CoachConfigurationRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createCoachConfiguration(request, actor.userId(), servletRequest));
    }

    @PostMapping("/coach-configurations/{configurationId}/layouts")
    @PreAuthorize("hasAuthority('operations:composition:manage')")
    public ApiEnvelope<CoachConfiguration> createCoachLayoutVersion(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID configurationId,
            @Valid @RequestBody CoachLayoutRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createCoachLayoutVersion(
                configurationId, request, actor.userId(), servletRequest));
    }

    @PostMapping("/coach-configurations/{configurationId}/layouts/publish")
    @PreAuthorize("hasAuthority('operations:master-data:publish')")
    public ApiEnvelope<CoachConfiguration> publishCoachLayout(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID configurationId,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.publishCoachLayout(configurationId, actor.userId(), servletRequest));
    }

    @GetMapping("/coaches")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<List<CoachSummary>> coaches() {
        return success(masterDataService.coaches());
    }

    @PostMapping("/coaches")
    @PreAuthorize("hasAuthority('operations:composition:manage')")
    public ApiEnvelope<CoachSummary> createCoach(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @Valid @RequestBody CoachRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.createCoach(request, actor.userId(), servletRequest));
    }

    @GetMapping("/trains/{trainId}/composition")
    @PreAuthorize("hasAuthority('operations:master-data:read')")
    public ApiEnvelope<Composition> composition(@PathVariable UUID trainId) {
        return success(masterDataService.composition(trainId));
    }

    @PutMapping("/trains/{trainId}/composition")
    @PreAuthorize("hasAuthority('operations:composition:manage')")
    public ApiEnvelope<Composition> saveComposition(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID trainId,
            @Valid @RequestBody CompositionRequest request,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.saveComposition(trainId, request, actor.userId(), servletRequest));
    }

    @PostMapping("/trains/{trainId}/composition/versions/{versionId}/publish")
    @PreAuthorize("hasAuthority('operations:master-data:publish')")
    public ApiEnvelope<Composition> publishComposition(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID trainId,
            @PathVariable UUID versionId,
            HttpServletRequest servletRequest
    ) {
        requireRecentAuthentication(servletRequest);
        return success(masterDataService.publishComposition(
                trainId, versionId, actor.userId(), servletRequest));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('operations:master-data:audit:read')")
    public ApiEnvelope<PageResult<AuditEntry>> audit(
            @RequestParam(required = false) @Size(max = 40) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return success(masterDataService.audit(entityType, entityId, page, size));
    }

    private void requireRecentAuthentication(HttpServletRequest servletRequest) {
        identityService.assertRecentAuthentication(servletRequest);
    }

    private <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }
}
