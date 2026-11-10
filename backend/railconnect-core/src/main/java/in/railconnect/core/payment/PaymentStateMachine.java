package in.railconnect.core.payment;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class PaymentStateMachine {
    private static final Map<PaymentState, Set<PaymentState>> TRANSITIONS = transitions();

    private PaymentStateMachine() {
    }

    public static PaymentState transition(
            PaymentState current,
            PaymentState next,
            PaymentTransitionSource source
    ) {
        if (current == null || next == null || source == null) {
            throw new IllegalArgumentException("Payment states and transition source are required");
        }
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException("Invalid payment state transition: " + current + " -> " + next);
        }
        if (source == PaymentTransitionSource.CHECKOUT_RETURN) {
            throw new IllegalStateException("A checkout return cannot change payment state");
        }
        if (next == PaymentState.SUCCEEDED && source != PaymentTransitionSource.VERIFIED_WEBHOOK
                && source != PaymentTransitionSource.VERIFIED_STATUS_QUERY
                && source != PaymentTransitionSource.RECONCILIATION) {
            throw new IllegalStateException("Payment success requires independently verified provider evidence");
        }
        if (next == PaymentState.EXPIRED && source != PaymentTransitionSource.EXPIRY_JOB
                && source != PaymentTransitionSource.RECONCILIATION) {
            throw new IllegalStateException("Payment expiration requires an expiry or reconciliation process");
        }
        return next;
    }

    private static Map<PaymentState, Set<PaymentState>> transitions() {
        Map<PaymentState, Set<PaymentState>> transitions = new EnumMap<>(PaymentState.class);
        transitions.put(PaymentState.CREATED, Set.of(
                PaymentState.INITIATED, PaymentState.CANCELLED, PaymentState.EXPIRED));
        transitions.put(PaymentState.INITIATED, Set.of(
                PaymentState.CHECKOUT_PENDING, PaymentState.PROCESSING, PaymentState.FAILED,
                PaymentState.CANCELLED, PaymentState.EXPIRED, PaymentState.REQUIRES_RECONCILIATION));
        transitions.put(PaymentState.CHECKOUT_PENDING, Set.of(
                PaymentState.PROCESSING, PaymentState.SUCCEEDED, PaymentState.FAILED,
                PaymentState.CANCELLED, PaymentState.EXPIRED, PaymentState.REQUIRES_RECONCILIATION));
        transitions.put(PaymentState.PROCESSING, Set.of(
                PaymentState.SUCCEEDED, PaymentState.FAILED, PaymentState.CANCELLED,
                PaymentState.EXPIRED, PaymentState.REQUIRES_RECONCILIATION));
        transitions.put(PaymentState.REQUIRES_RECONCILIATION, Set.of(
                PaymentState.CHECKOUT_PENDING, PaymentState.PROCESSING,
                PaymentState.SUCCEEDED, PaymentState.FAILED,
                PaymentState.CANCELLED, PaymentState.EXPIRED));
        transitions.put(PaymentState.SUCCEEDED, Set.of(PaymentState.REQUIRES_RECONCILIATION));
        transitions.put(PaymentState.FAILED, Set.of(PaymentState.REQUIRES_RECONCILIATION));
        transitions.put(PaymentState.CANCELLED, Set.of(PaymentState.REQUIRES_RECONCILIATION));
        transitions.put(PaymentState.EXPIRED, Set.of(PaymentState.REQUIRES_RECONCILIATION));
        transitions.put(PaymentState.REVERSED, Set.of(PaymentState.REQUIRES_RECONCILIATION));
        return Map.copyOf(transitions);
    }
}
