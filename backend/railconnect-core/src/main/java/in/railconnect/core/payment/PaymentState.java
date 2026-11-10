package in.railconnect.core.payment;

public enum PaymentState {
    CREATED,
    INITIATED,
    CHECKOUT_PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED,
    REQUIRES_RECONCILIATION,
    REVERSED
}
