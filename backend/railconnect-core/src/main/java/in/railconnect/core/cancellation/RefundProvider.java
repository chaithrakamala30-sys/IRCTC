package in.railconnect.core.cancellation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface RefundProvider {
    String providerCode();

    boolean available();

    RefundExecutionResult executeRefund(RefundRequest request);

    RefundStatusResult queryRefund(String providerRefundId);

    PaymentRefundsResult queryPaymentRefunds(String paymentIntentId);

    RefundVerificationResult verifyRefund(String providerRefundId);

    record RefundRequest(
            UUID refundOrderId,
            String paymentIntentId,
            String merchantRefundRef,
            String cancellationReference,
            BigDecimal amount,
            String currency,
            String reason,
            String idempotencyKey
    ) {}

    record RefundExecutionResult(
            String providerRefundId,
            String paymentIntentId,
            String merchantRefundRef,
            String cancellationReference,
            BigDecimal amount,
            String currency,
            String status,
            Instant completedAt,
            boolean isIdempotentReplay,
            String rawResponse
    ) {}

    record RefundStatusResult(
            String providerRefundId,
            String merchantRefundRef,
            String cancellationReference,
            String paymentIntentId,
            BigDecimal amount,
            String currency,
            String status,
            Instant completedAt,
            String rawResponse
    ) {}

    record PaymentRefundsResult(
            String paymentIntentId,
            BigDecimal originalAmount,
            BigDecimal totalRefunded,
            BigDecimal remainingRefundable,
            String currency,
            java.util.List<PaymentRefundItem> refunds,
            String rawResponse
    ) {}

    record PaymentRefundItem(
            String refundId,
            String merchantRefundRef,
            String cancellationReference,
            BigDecimal amount,
            String status,
            Instant completedAt
    ) {}

    record RefundVerificationResult(
            String providerRefundId,
            String merchantRefundRef,
            String cancellationReference,
            String paymentIntentId,
            BigDecimal amount,
            String currency,
            String status,
            boolean verified,
            int ledgerEntriesCount,
            Instant completedAt,
            Instant verifiedAt,
            String rawResponse
    ) {}
}

