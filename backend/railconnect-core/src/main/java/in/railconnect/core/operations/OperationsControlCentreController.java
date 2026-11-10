package in.railconnect.core.operations;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.operations.OperationsModels.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1")
public class OperationsControlCentreController {

    private final TrainOperationsControlService operationsService;

    public OperationsControlCentreController(TrainOperationsControlService operationsService) {
        this.operationsService = operationsService;
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }

    // =========================================================================
    // Public / Passenger Tracking APIs (Pure staff reports, no 3rd-party APIs)
    // =========================================================================

    @GetMapping("/tracking/trains/{trainNumber}")
    public ApiEnvelope<PublicTrainTrackingView> getPublicTrainTracking(
            @PathVariable String trainNumber,
            @RequestParam(required = false) String serviceDate
    ) {
        return success(operationsService.getPublicTrainTracking(trainNumber, serviceDate));
    }

    // =========================================================================
    // Control Centre Dashboard & Telemetry
    // =========================================================================

    @GetMapping("/operations/dashboard")
    @PreAuthorize("hasAuthority('operations:control')")
    public ApiEnvelope<OperationsDashboardSummary> getDashboardSummary() {
        return success(operationsService.getDashboardSummary());
    }

    @GetMapping("/operations/train-runs")
    @PreAuthorize("hasAuthority('operations:control')")
    public ApiEnvelope<List<TrainMonitoringView>> listActiveTrainRuns(
            @RequestParam(defaultValue = "25") int limit
    ) {
        return success(operationsService.listActiveRuns(limit));
    }

    // =========================================================================
    // Station Operational Updates (Arrival / Departure reporting)
    // =========================================================================

    @PostMapping("/operations/station-updates")
    @PreAuthorize("hasAuthority('operations:station:update')")
    public ApiEnvelope<StationOperationalUpdateView> recordStationUpdate(
            @Valid @RequestBody StationOperationalUpdateInput input,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID staffId = principal != null ? principal.userId() : null;
        return success(operationsService.recordStationUpdate(input, staffId));
    }

    // =========================================================================
    // Platform Management
    // =========================================================================

    @GetMapping("/operations/stations/{stationId}/platforms")
    @PreAuthorize("hasAuthority('operations:platform:manage')")
    public ApiEnvelope<List<PlatformView>> listPlatforms(@PathVariable UUID stationId) {
        return success(operationsService.listPlatforms(stationId));
    }

    @PostMapping("/operations/platform-assignments")
    @PreAuthorize("hasAuthority('operations:platform:manage')")
    public ApiEnvelope<PlatformAssignmentView> assignPlatform(
            @Valid @RequestBody PlatformAssignmentInput input,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID staffId = principal != null ? principal.userId() : null;
        return success(operationsService.assignPlatform(input, staffId));
    }

    // =========================================================================
    // Incident Management
    // =========================================================================

    @GetMapping("/operations/incidents")
    @PreAuthorize("hasAuthority('operations:incident:manage')")
    public ApiEnvelope<List<IncidentView>> listIncidents(
            @RequestParam(required = false) String status
    ) {
        return success(operationsService.listIncidents(status));
    }

    @PostMapping("/operations/incidents")
    @PreAuthorize("hasAuthority('operations:incident:manage')")
    public ApiEnvelope<IncidentView> createIncident(
            @Valid @RequestBody IncidentInput input,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID staffId = principal != null ? principal.userId() : null;
        return success(operationsService.createIncident(input, staffId));
    }

    // =========================================================================
    // Disruptions & Passenger Impact Assessment
    // =========================================================================

    @PostMapping("/operations/disruptions")
    @PreAuthorize("hasAuthority('operations:disruption:manage')")
    public ApiEnvelope<DisruptionView> reportDisruption(
            @Valid @RequestBody DisruptionInput input,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID staffId = principal != null ? principal.userId() : null;
        return success(operationsService.reportDisruption(input, staffId));
    }

    // =========================================================================
    // Operational Announcements
    // =========================================================================

    @GetMapping("/operations/announcements")
    @PreAuthorize("hasAuthority('operations:announcement:publish')")
    public ApiEnvelope<List<OperationalAnnouncementView>> listAnnouncements(
            @RequestParam(defaultValue = "PUBLISHED") String status,
            @RequestParam(defaultValue = "15") int limit
    ) {
        return success(operationsService.listAnnouncements(status, limit));
    }

    @PostMapping("/operations/announcements")
    @PreAuthorize("hasAuthority('operations:announcement:publish')")
    public ApiEnvelope<OperationalAnnouncementView> publishAnnouncement(
            @Valid @RequestBody OperationalAnnouncementInput input,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID staffId = principal != null ? principal.userId() : null;
        return success(operationsService.publishAnnouncement(input, staffId));
    }
}
