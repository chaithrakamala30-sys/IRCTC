package in.railconnect.core.payment;

public enum PaymentTransitionSource {
    SYSTEM,
    PROVIDER_RESPONSE,
    VERIFIED_WEBHOOK,
    VERIFIED_STATUS_QUERY,
    RECONCILIATION,
    EXPIRY_JOB,
    CHECKOUT_RETURN
}
