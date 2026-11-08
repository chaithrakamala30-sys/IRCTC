package in.railconnect.core.reservation;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.reservation.ReservationModels.AllocationAuditEvent;
import in.railconnect.core.reservation.ReservationModels.AllocationCounts;
import in.railconnect.core.reservation.ReservationModels.AllocationPolicyAuditEvent;
import in.railconnect.core.reservation.ReservationModels.AllocationPolicyRequest;
import in.railconnect.core.reservation.ReservationModels.AllocationPolicyVersion;
import in.railconnect.core.reservation.ReservationModels.CreateReservationRequest;
import in.railconnect.core.reservation.ReservationModels.Page;
import in.railconnect.core.reservation.ReservationModels.PassengerAllocation;
import in.railconnect.core.reservation.ReservationModels.ReleaseRequest;
import in.railconnect.core.reservation.ReservationModels.ReservationDetails;
import in.railconnect.core.reservation.ReservationModels.ReservationSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/api/v1/passenger/reservations/requests")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('reservations:own:create')")
    public ApiEnvelope<ReservationDetails> create(
            @Valid @RequestBody CreateReservationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(reservationService.createRequest(request, idempotencyKey, principal.userId()));
    }

    @GetMapping("/api/v1/passenger/reservations/requests/{reservationId}")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('reservations:own:read')")
    public ApiEnvelope<ReservationDetails> read(
            @PathVariable UUID reservationId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(reservationService.readOwn(reservationId, principal.userId()));
    }

    @GetMapping("/api/v1/passenger/reservations/requests/{reservationId}/allocations")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('reservations:own:read')")
    public ApiEnvelope<List<PassengerAllocation>> allocations(
            @PathVariable UUID reservationId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(reservationService.readOwnAllocations(reservationId, principal.userId()));
    }

    @PostMapping("/api/v1/passenger/reservations/requests/{reservationId}/allocate")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('reservations:own:allocate')")
    public ApiEnvelope<ReservationDetails> allocate(
            @PathVariable UUID reservationId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(reservationService.allocate(reservationId, principal.userId()));
    }

    @PostMapping("/api/v1/passenger/reservations/requests/{reservationId}/release")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('reservations:own:release')")
    public ApiEnvelope<ReservationDetails> release(
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReleaseRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(reservationService.releaseOwn(
                reservationId, principal.userId(), request.reasonCode()));
    }

    @GetMapping("/api/v1/admin/reservations")
    @PreAuthorize("hasAuthority('reservations:admin:read')")
    public ApiEnvelope<Page<ReservationSummary>> adminReservations(
            @RequestParam(required = false) @Size(max = 32) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive int size
    ) {
        return success(reservationService.adminReservations(status, page, size));
    }

    @GetMapping("/api/v1/admin/reservations/{reservationId}")
    @PreAuthorize("hasAuthority('reservations:admin:read')")
    public ApiEnvelope<ReservationDetails> adminReservation(@PathVariable UUID reservationId) {
        return success(reservationService.adminRead(reservationId));
    }

    @GetMapping("/api/v1/admin/allocations/policies")
    @PreAuthorize("hasAuthority('reservations:policy:read')")
    public ApiEnvelope<List<AllocationPolicyVersion>> allocationPolicies() {
        return success(reservationService.allocationPolicyVersions());
    }

    @PostMapping("/api/v1/admin/allocations/policies")
    @PreAuthorize("hasAuthority('reservations:policy:manage')")
    public ApiEnvelope<AllocationPolicyVersion> createAllocationPolicy(
            @Valid @RequestBody AllocationPolicyRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(reservationService.createAllocationPolicyDraft(request, principal.userId()));
    }

    @PostMapping("/api/v1/admin/allocations/policies/{version}/approve")
    @PreAuthorize("hasAuthority('reservations:policy:approve')")
    public ApiEnvelope<AllocationPolicyVersion> approveAllocationPolicy(
            @PathVariable int version,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(reservationService.approveAllocationPolicy(version, principal.userId()));
    }

    @PostMapping("/api/v1/admin/allocations/policies/{version}/publish")
    @PreAuthorize("hasAuthority('reservations:policy:publish')")
    public ApiEnvelope<AllocationPolicyVersion> publishAllocationPolicy(
            @PathVariable int version,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(reservationService.publishAllocationPolicy(version, principal.userId()));
    }

    @GetMapping("/api/v1/admin/allocations/policies/audit")
    @PreAuthorize("hasAuthority('reservations:policy:read')")
    public ApiEnvelope<Page<AllocationPolicyAuditEvent>> allocationPolicyAudit(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive int size
    ) {
        return success(reservationService.allocationPolicyAudit(page, size));
    }

    @GetMapping("/api/v1/admin/allocations/summary")
    @PreAuthorize("hasAuthority('reservations:admin:read')")
    public ApiEnvelope<List<AllocationCounts>> adminAllocationSummary() {
        return success(reservationService.allocationSummary());
    }

    @GetMapping("/api/v1/admin/allocations/failures")
    @PreAuthorize("hasAuthority('reservations:admin:read')")
    public ApiEnvelope<Page<ReservationSummary>> allocationFailures(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive int size
    ) {
        return success(reservationService.allocationFailures(page, size));
    }

    @GetMapping("/api/v1/admin/allocations/audit")
    @PreAuthorize("hasAuthority('reservations:admin:read')")
    public ApiEnvelope<Page<AllocationAuditEvent>> audit(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive int size
    ) {
        return success(reservationService.auditEvents(page, size));
    }

    @PostMapping("/api/v1/admin/reservations/{reservationId}/release")
    @PreAuthorize("hasAuthority('reservations:admin:release')")
    public ApiEnvelope<ReservationDetails> adminRelease(
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReleaseRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(reservationService.adminRelease(
                reservationId, principal.userId(), request.reasonCode()));
    }

    @GetMapping("/api/v1/operations/allocations/summary")
    @PreAuthorize("hasAuthority('reservations:operations:summary')")
    public ApiEnvelope<List<AllocationCounts>> operationsSummary(@RequestParam UUID trainRunId) {
        return success(reservationService.operationsSummary(trainRunId));
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }
}
