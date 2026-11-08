package in.railconnect.core.inventory;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.inventory.InventoryModels.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/api/v1/public/inventory/runs")
    public ApiEnvelope<Page<RunSummary>> publicRuns(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(required = false) UUID trainId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return success(inventory.runs(serviceDate, trainId, page, size));
    }

    @GetMapping("/api/v1/public/inventory/runs/{runId}")
    public ApiEnvelope<RunDetails> run(@PathVariable UUID runId) {
        return success(inventory.runDetails(runId));
    }

    @GetMapping("/api/v1/public/inventory/availability")
    public ApiEnvelope<AvailabilityResponse> availability(@Valid JourneyRequest request) {
        return success(inventory.availability(request));
    }

    @GetMapping("/api/v1/public/inventory/berths")
    public ApiEnvelope<BerthPage> berths(
            @Valid JourneyRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return success(inventory.berths(request, page, size));
    }

    @PostMapping("/api/v1/passenger/inventory/holds")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('inventory:holds:manage')")
    public ApiEnvelope<HoldResponse> createHold(
            @Valid @RequestBody HoldRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(inventory.createHold(request, idempotencyKey, principal.userId()));
    }

    @GetMapping("/api/v1/passenger/inventory/holds")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('inventory:holds:manage')")
    public ApiEnvelope<List<HoldResponse>> holds(@AuthenticationPrincipal RailConnectPrincipal principal) {
        return success(inventory.holds(principal.userId()));
    }

    @DeleteMapping("/api/v1/passenger/inventory/holds/{holdId}")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('inventory:holds:manage')")
    public ApiEnvelope<HoldResponse> releaseHold(
            @PathVariable UUID holdId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(inventory.releaseHold(holdId, principal.userId()));
    }

    @PostMapping("/api/v1/admin/inventory/runs")
    @PreAuthorize("hasAuthority('inventory:runs:initialize')")
    public ApiEnvelope<RunSummary> initializeRun(
            @Valid @RequestBody InitializeRunRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(inventory.initializeRun(request, principal.userId()));
    }

    @GetMapping("/api/v1/admin/inventory/trains")
    @PreAuthorize("hasAuthority('inventory:runs:initialize')")
    public ApiEnvelope<List<TrainOption>> initializableTrains() {
        return success(inventory.initializableTrains());
    }

    @GetMapping("/api/v1/admin/inventory/runs")
    @PreAuthorize("hasAuthority('inventory:admin:read')")
    public ApiEnvelope<Page<RunSummary>> adminRuns(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(required = false) UUID trainId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return success(inventory.runs(serviceDate, trainId, page, size));
    }

    @GetMapping("/api/v1/admin/inventory/runs/{runId}/audit")
    @PreAuthorize("hasAuthority('inventory:admin:read')")
    public ApiEnvelope<List<AuditEvent>> audit(
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return success(inventory.audit(runId, limit));
    }

    @GetMapping("/api/v1/admin/inventory/runs/{runId}/blocks")
    @PreAuthorize("hasAuthority('inventory:admin:read')")
    public ApiEnvelope<Page<BlockResponse>> blocks(
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return success(inventory.blocks(runId, page, size));
    }

    @PostMapping("/api/v1/admin/inventory/runs/{runId}/blocks")
    @PreAuthorize("hasAuthority('inventory:blocks:manage')")
    public ApiEnvelope<BlockResponse> createBlock(
            @PathVariable UUID runId,
            @Valid @RequestBody BlockRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(inventory.createBlock(runId, request, principal.userId()));
    }

    @DeleteMapping("/api/v1/admin/inventory/blocks/{blockId}")
    @PreAuthorize("hasAuthority('inventory:blocks:manage')")
    public ApiEnvelope<BlockResponse> releaseBlock(
            @PathVariable UUID blockId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(inventory.releaseBlock(blockId, principal.userId()));
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }
}
