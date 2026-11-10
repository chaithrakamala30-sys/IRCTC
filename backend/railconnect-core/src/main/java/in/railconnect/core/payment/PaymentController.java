package in.railconnect.core.payment;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.payment.PaymentModels.InitiatePaymentRequest;
import in.railconnect.core.payment.PaymentModels.PaymentAdminDetails;
import in.railconnect.core.payment.PaymentModels.PaymentAdminSummary;
import in.railconnect.core.payment.PaymentModels.PaymentOrderResponse;
import in.railconnect.core.payment.PaymentModels.PaymentProviderOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class PaymentController {
    private final PaymentOrchestrationService payments;

    public PaymentController(PaymentOrchestrationService payments) {
        this.payments = payments;
    }

    @GetMapping("/api/v1/passenger/payments/providers")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('payments:own:read')")
    public ApiEnvelope<List<PaymentProviderOption>> providers() {
        return success(payments.providers());
    }

    @PostMapping("/api/v1/passenger/payments/orders")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('payments:own:create')")
    public ApiEnvelope<PaymentOrderResponse> initiate(
            @Valid @RequestBody InitiatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(payments.initiate(
                request.bookingId(), principal.userId(), idempotencyKey, request.experience()));
    }

    @GetMapping("/api/v1/passenger/payments/orders/{paymentId}")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('payments:own:read')")
    public ApiEnvelope<PaymentOrderResponse> readOwn(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(payments.readOwn(paymentId, principal.userId()));
    }

    @PostMapping("/api/v1/passenger/payments/orders/{paymentId}/refresh")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('payments:own:read')")
    public ApiEnvelope<PaymentOrderResponse> refresh(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(payments.refresh(paymentId, principal.userId()));
    }

    @GetMapping("/api/v1/admin/payments/orders")
    @PreAuthorize("hasAuthority('payments:admin:read')")
    public ApiEnvelope<List<PaymentAdminSummary>> adminSearch(
            @RequestParam(required = false) @Size(max = 80) String query,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return success(payments.adminSearch(query, page, size));
    }

    @GetMapping("/api/v1/admin/payments/orders/{paymentId}")
    @PreAuthorize("hasAuthority('payments:admin:read')")
    public ApiEnvelope<PaymentAdminDetails> adminRead(@PathVariable UUID paymentId) {
        return success(payments.adminRead(paymentId));
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }
}
