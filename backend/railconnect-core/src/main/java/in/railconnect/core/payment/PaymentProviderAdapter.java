package in.railconnect.core.payment;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface PaymentProviderAdapter {
    PaymentProvider provider();

    boolean available();

    CheckoutSession createCheckout(PaymentInitiationRequest request);

    VerifiedProviderStatus retrieveStatus(String providerPaymentIntentId);

    VerifiedProviderStatus verifyPayment(String providerPaymentIntentId);

    record PaymentInitiationRequest(
            UUID paymentId,
            String merchantReference,
            String bookingReference,
            String customerReference,
            String description,
            long amountMinor,
            String currency,
            Instant expiresAt,
            String idempotencyKey,
            URI successUrl,
            URI failureUrl,
            Duration expiresIn,
            Map<String, String> metadata
    ) {
        public PaymentInitiationRequest {
            if (paymentId == null || merchantReference == null || merchantReference.isBlank()
                    || bookingReference == null || bookingReference.isBlank()
                    || customerReference == null || customerReference.isBlank()
                    || description == null || description.isBlank()
                    || amountMinor <= 0 || !"INR".equals(currency)
                    || expiresAt == null || idempotencyKey == null || idempotencyKey.isBlank()
                    || successUrl == null || failureUrl == null || expiresIn == null
                    || expiresIn.isZero() || expiresIn.isNegative() || expiresIn.compareTo(Duration.ofHours(24)) > 0
                    || metadata == null) {
                throw new IllegalArgumentException("Invalid payment initiation request");
            }
            metadata = Map.copyOf(metadata);
        }
    }

    record CheckoutSession(
            String providerPaymentIntentId,
            String providerSessionId,
            URI checkoutUrl,
            Instant expiresAt,
            PaymentState state
    ) {
    }

    record VerifiedProviderStatus(
            String providerPaymentIntentId,
            String merchantReference,
            String bookingReference,
            PaymentState state,
            long amountMinor,
            String currency,
            boolean verified,
            String transactionReference,
            Instant completedAt,
            Instant expiresAt,
            Instant verifiedAt
    ) {
    }
}
