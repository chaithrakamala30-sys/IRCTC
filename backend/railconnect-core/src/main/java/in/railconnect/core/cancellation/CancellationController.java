package in.railconnect.core.cancellation;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.cancellation.CancellationModels.*;
import in.railconnect.core.identity.RailConnectPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
public class CancellationController {
    private final CancellationService cancellationService;

    public CancellationController(CancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }

    // ========================================================================
    // Passenger Endpoints
    // ========================================================================

    @GetMapping("/api/v1/bookings/{bookingId}/cancellation-eligibility")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('cancellations:own:read')")
    public ApiEnvelope<CancellationEligibility> eligibility(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(cancellationService.checkEligibility(bookingId, principal.userId()));
    }

    @PostMapping("/api/v1/bookings/{bookingId}/cancellation-quote")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('cancellations:own:read')")
    public ApiEnvelope<CancellationQuote> quote(
            @PathVariable UUID bookingId,
            @RequestBody(required = false) CancellationQuoteRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(cancellationService.calculateQuote(
                bookingId,
                request != null ? request.bookingPassengerIds() : null,
                principal.userId()
        ));
    }

    @PostMapping("/api/v1/bookings/{bookingId}/cancel")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('cancellations:own:create')")
    public ApiEnvelope<CancellationReceipt> cancel(
            @PathVariable UUID bookingId,
            @Valid @RequestBody CancelBookingRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(cancellationService.cancelBooking(bookingId, request, principal.userId()));
    }

    @GetMapping("/api/v1/cancellations/{cancellationId}")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('cancellations:own:read')")
    public ApiEnvelope<CancellationReceipt> cancellation(
            @PathVariable UUID cancellationId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(cancellationService.getCancellation(cancellationId, principal.userId()));
    }

    @GetMapping("/api/v1/refunds/{refundId}")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('cancellations:own:read')")
    public ApiEnvelope<RefundOrderView> refund(
            @PathVariable UUID refundId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(cancellationService.getRefund(refundId, principal.userId()));
    }

    // ========================================================================
    // Staff & Administration Endpoints
    // ========================================================================

    @GetMapping("/api/v1/admin/cancellations/{cancellationId}")
    @PreAuthorize("hasAuthority('cancellations:admin:read')")
    public ApiEnvelope<CancellationReceipt> adminCancellation(@PathVariable UUID cancellationId) {
        return success(cancellationService.getCancellation(cancellationId, null));
    }

    @GetMapping("/api/v1/admin/refunds/{refundId}")
    @PreAuthorize("hasAuthority('cancellations:admin:read')")
    public ApiEnvelope<RefundOrderView> adminRefund(@PathVariable UUID refundId) {
        return success(cancellationService.getRefund(refundId, null));
    }

    @PostMapping("/api/v1/admin/refunds/{refundId}/reconcile")
    @PreAuthorize("hasAuthority('refunds:admin:reconcile')")
    public ApiEnvelope<RefundOrderView> reconcileRefund(@PathVariable UUID refundId) {
        return success(cancellationService.reconcileRefund(refundId));
    }
}

