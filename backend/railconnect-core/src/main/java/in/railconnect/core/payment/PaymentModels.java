package in.railconnect.core.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public final class PaymentModels {
    private PaymentModels() {
    }

    public record InitiatePaymentRequest(
            @NotNull UUID bookingId,
            @Pattern(regexp = "mobile|laptop") String experience
    ) {
    }

    public record PaymentOrderResponse(
            UUID paymentId,
            UUID bookingId,
            String bookingReference,
            String status,
            BigDecimal amount,
            String currency,
            Instant expiresAt,
            URI checkoutUrl,
            boolean demoPayment,
            boolean requiresReconciliation
    ) {
    }

    public record PaymentProviderOption(
            String providerCode,
            String displayName,
            boolean demoPayment,
            boolean available
    ) {
    }

    public record PaymentAdminSummary(
            UUID paymentId,
            UUID bookingId,
            String bookingReference,
            String providerCode,
            String providerPaymentIntentId,
            String status,
            BigDecimal amount,
            String currency,
            Instant expiresAt,
            Instant createdAt
    ) {
    }

    public record PaymentAdminDetails(
            PaymentAdminSummary order,
            String checkoutUrl,
            String transactionReference,
            String attemptsJson,
            String stateHistoryJson,
            String webhookEventsJson,
            String reconciliationJson,
            String financialExceptionsJson
    ) {
    }
}
