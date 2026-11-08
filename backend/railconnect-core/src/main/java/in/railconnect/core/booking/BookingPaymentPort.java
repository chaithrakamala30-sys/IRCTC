package in.railconnect.core.booking;

import in.railconnect.core.booking.BookingModels.BookingDetails;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal boundary reserved for the Phase 9 payment module.
 * No provider operation or payment state transition is implemented in Phase 8.
 */
public interface BookingPaymentPort {
    PaymentStartContext getPaymentStartContext(UUID bookingId);

    BookingDetails acceptVerifiedSuccess(VerifiedPaymentResult result);

    BookingDetails acceptVerifiedFailure(VerifiedPaymentResult result);

    BookingDetails recordPendingOutcome(UUID bookingId, String paymentAttemptReference);

    BookingDetails reconcileUncertainOutcome(UUID bookingId, String reconciliationReference);

    record PaymentStartContext(
            UUID bookingId,
            String bookingReference,
            BigDecimal amount,
            String currency,
            Instant paymentDeadline
    ) { }

    record VerifiedPaymentResult(
            UUID bookingId,
            String eventIdempotencyKey,
            String providerReference,
            BigDecimal verifiedAmount,
            String verifiedCurrency
    ) { }
}
