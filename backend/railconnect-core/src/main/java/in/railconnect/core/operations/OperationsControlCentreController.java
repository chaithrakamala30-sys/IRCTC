package in.railconnect.core.operations;

import in.railconnect.core.api.ApiResponse;
import in.railconnect.core.identity.AuthenticatedUser;
import in.railconnect.core.operations.OperationsModels.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OperationsControlCentreController {

    private final TrainOperationsControlService operationsService;

    public OperationsControlCentreController(TrainOperationsControlService operationsService) {
        this.operationsService = operationsService;
    }

    // =========================================================================
    // Public / Passenger Tracking APIs (Pure staff reports, no 3rd-party APIs)
    // =========================================================================

    @GetMapping("/tracking/trains/{trainNumber}")
    public ApiResponse<PublicTrainTrackingView> getPublicTrainTracking(
            @PathVariable String trainNumber,
            @RequestParam(required = false) String serviceDate
    ) {
        return ApiResponse.ok(operationsService.getPublicTrainTracking(trainNumber, serviceDate));
    }

    // =========================================================================
    // Control Centre Dashboard & Telemetry
    // =========================================================================

    @GetMapping("/operations/dashboard")
    @PreAuthorize("hasAuthority('operations:control')")
    public ApiResponse<OperationsDashboardSummary> getDashboardSummary() {
        return ApiResponse.ok(operationsService.getDashboardSummary());
    }

    @GetMapping("/operations/train-runs")
    @PreAuthorize("hasAuthority('operations:control')")
    public ApiResponse<List<TrainMonitoringView>> listActiveTrainRuns(
            @RequestParam(defaultValue = "25") int limit
    ) {
        return ApiResponse.ok(operationsService.listActiveRuns(limit));
    }

    // =========================================================================
    // Station Operational Updates (Arrival / Departure reporting)
    // =========================================================================

    @PostMapping("/operations/station-updates")
    @PreAuthorize("hasAuthority('operations:station:update')")
    public ApiResponse<StationOperationalUpdateView> recordStationUpdate(
            @Valid @RequestBody StationOperationalUpdateInput input,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID staffId = user != null ? user.userId() : null;
        return ApiResponse.ok(operationsService.recordStationUpdate(input, staffId));
    }

    // =========================================================================
    // Platform Management
    // =========================================================================

    @GetMapping("/operations/stations/{stationId}/platforms")
    @PreAuthorize("hasAuthority('operations:platform:manage')")
    public ApiResponse<List<PlatformView>> listPlatforms(@PathVariable UUID stationId) {
        return ApiResponse.ok(operationsService.listPlatforms(stationId));
    }

    @PostMapping("/operations/platform-assignments")
    @PreAuthorize("hasAuthority('operations:platform:manage')")
    public ApiResponse<PlatformAssignmentView> assignPlatform(
            @Valid @RequestBody PlatformAssignmentInput input,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID staffId = user != null ? user.userId() : null;
        return ApiResponse.ok(operationsService.assignPlatform(input, staffId));
    }

    // =========================================================================
    // Incident Management
    // =========================================================================

    @GetMapping("/operations/incidents")
    @PreAuthorize("hasAuthority('operations:incident:manage')")
    public ApiResponse<List<IncidentView>> listIncidents(
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(operationsService.listIncidents(status));
    }

    @PostMapping("/operations/incidents")
    @PreAuthorize("hasAuthority('operations:incident:manage')")
    public ApiResponse<IncidentView> createIncident(
            @Valid @RequestBody IncidentInput input,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID staffId = user != null ? user.userId() : null;
        return ApiResponse.ok(operationsService.createIncident(input, staffId));
    }

    // =========================================================================
    // Disruptions & Passenger Impact Assessment
    // =========================================================================

    @PostMapping("/operations/disruptions")
    @PreAuthorize("hasAuthority('operations:disruption:manage')")
    public ApiResponse<DisruptionView> reportDisruption(
            @Valid @RequestBody DisruptionInput input,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID staffId = user != null ? user.userId() : null;
        return ApiResponse.ok(operationsService.reportDisruption(input, staffId));
    }

    // =========================================================================
    // Operational Announcements
    // =========================================================================

    @GetMapping("/operations/announcements")
    @PreAuthorize("hasAuthority('operations:announcement:publish')")
    public ApiResponse<List<OperationalAnnouncementView>> listAnnouncements(
            @RequestParam(defaultValue = "PUBLISHED") String status,
            @RequestParam(defaultValue = "15") int limit
    ) {
        return ApiResponse.ok(operationsService.listAnnouncements(status, limit));
    }

    @PostMapping("/operations/announcements")
    @PreAuthorize("hasAuthority('operations:announcement:publish')")
    public ApiResponse<OperationalAnnouncementView> publishAnnouncement(
            @Valid @RequestBody OperationalAnnouncementInput input,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID staffId = user != null ? user.userId() : null;
        return ApiResponse.ok(operationsService.publishAnnouncement(input, staffId));
    }
}
