package in.railconnect.core.payment;

import in.railconnect.core.payment.PaymentModels.PaymentAdminDetails;
import in.railconnect.core.payment.PaymentModels.PaymentAdminSummary;
import in.railconnect.core.payment.PaymentModels.PaymentOrderResponse;
import in.railconnect.core.payment.PaymentModels.PaymentProviderOption;
import in.railconnect.core.payment.PaymentProviderAdapter.VerifiedProviderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static in.railconnect.core.payment.PaymentState.SUCCEEDED;

@Service
public class PaymentOrchestrationService {
    private final PaymentGateway gateway;
    private final PaymentOrderService orders;
    private final PaymentRateLimiter rateLimiter;

    public PaymentOrchestrationService(
            PaymentGateway gateway,
            PaymentOrderService orders,
            PaymentRateLimiter rateLimiter
    ) {
        this.gateway = gateway;
        this.orders = orders;
        this.rateLimiter = rateLimiter;
    }

    public List<PaymentProviderOption> providers() {
        return List.of(new PaymentProviderOption(
                PaymentProvider.DHRUVA_BANK.name(),
                "Dhruva Bank demo checkout",
                true,
                gateway.isAvailable(PaymentProvider.DHRUVA_BANK)));
    }

    public PaymentOrderResponse initiate(
            UUID bookingId,
            UUID ownerId,
            String idempotencyKey,
            String experience
    ) {
        requireRateLimit(ownerId);
        if (!gateway.isAvailable(PaymentProvider.DHRUVA_BANK)) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNAVAILABLE",
                    "Dhruva Bank demo payments are not enabled");
        }
        PaymentOrderService.PreparedPayment prepared =
                orders.prepare(bookingId, ownerId, idempotencyKey, experience);
        if (prepared.checkoutUrl() != null
                || prepared.providerPaymentIntentId() != null
                || !List.of("CREATED", "INITIATED", "REQUIRES_RECONCILIATION").contains(prepared.status())) {
            return orders.readOwn(prepared.paymentId(), ownerId);
        }

        orders.markInitiationStarted(prepared.paymentId());
        try {
            PaymentProviderAdapter.CheckoutSession checkout =
                    gateway.createCheckout(PaymentProvider.DHRUVA_BANK, prepared.toProviderRequest());
            return orders.recordCheckout(prepared.paymentId(), checkout);
        } catch (PaymentProviderUnavailableException exception) {
            orders.recordInitiationFailure(prepared.paymentId());
            throw exception;
        }
    }

    public PaymentOrderResponse readOwn(UUID paymentId, UUID ownerId) {
        return orders.readOwn(paymentId, ownerId);
    }

    public PaymentOrderResponse refresh(UUID paymentId, UUID ownerId) {
        requireRateLimit(ownerId);
        PaymentOrderService.PaymentRecord payment = orders.readForRefresh(paymentId, ownerId);
        if (payment.providerPaymentIntentId() == null) {
            return orders.readOwn(paymentId, ownerId);
        }
        try {
            VerifiedProviderStatus status = gateway.retrieveStatus(
                    PaymentProvider.DHRUVA_BANK, payment.providerPaymentIntentId());
            if (status.state() == SUCCEEDED) {
                status = gateway.verifyPayment(PaymentProvider.DHRUVA_BANK, payment.providerPaymentIntentId());
            }
            orders.recordProviderStatus(paymentId, status);
        } catch (PaymentProviderUnavailableException exception) {
            orders.recordStatusRefreshFailure(paymentId);
            throw exception;
        }
        return orders.readOwn(paymentId, ownerId);
    }

    public List<PaymentAdminSummary> adminSearch(String query, int page, int size) {
        return orders.adminSearch(query, page, size);
    }

    public PaymentAdminDetails adminRead(UUID paymentId) {
        return orders.adminRead(paymentId);
    }

    private void requireRateLimit(UUID ownerId) {
        if (!rateLimiter.allow(ownerId)) {
            throw new PaymentException(HttpStatus.TOO_MANY_REQUESTS, "PAYMENT_RATE_LIMITED",
                    "Too many payment requests; please wait before trying again", true);
        }
    }
}
