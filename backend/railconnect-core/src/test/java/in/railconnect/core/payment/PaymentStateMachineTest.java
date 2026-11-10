package in.railconnect.core.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStateMachineTest {
    @Test
    void allowsExpectedPendingAndReconciliationTransitions() {
        assertThat(PaymentStateMachine.transition(
                PaymentState.CREATED, PaymentState.INITIATED, PaymentTransitionSource.SYSTEM))
                .isEqualTo(PaymentState.INITIATED);
        assertThat(PaymentStateMachine.transition(
                PaymentState.INITIATED, PaymentState.CHECKOUT_PENDING, PaymentTransitionSource.PROVIDER_RESPONSE))
                .isEqualTo(PaymentState.CHECKOUT_PENDING);
        assertThat(PaymentStateMachine.transition(
                PaymentState.CHECKOUT_PENDING,
                PaymentState.REQUIRES_RECONCILIATION,
                PaymentTransitionSource.PROVIDER_RESPONSE))
                .isEqualTo(PaymentState.REQUIRES_RECONCILIATION);
        assertThat(PaymentStateMachine.transition(
                PaymentState.REQUIRES_RECONCILIATION, PaymentState.SUCCEEDED,
                PaymentTransitionSource.VERIFIED_STATUS_QUERY))
                .isEqualTo(PaymentState.SUCCEEDED);
    }

    @Test
    void rejectsSkippingInitiationAndChangingTerminalFailure() {
        assertThatThrownBy(() -> PaymentStateMachine.transition(
                PaymentState.CREATED, PaymentState.SUCCEEDED, PaymentTransitionSource.CHECKOUT_RETURN))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PaymentStateMachine.transition(
                PaymentState.FAILED, PaymentState.PROCESSING, PaymentTransitionSource.SYSTEM))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void doesNotAllowCheckoutReturnToDeclarePaymentSuccessful() {
        assertThatThrownBy(() -> PaymentStateMachine.transition(
                PaymentState.PROCESSING, PaymentState.SUCCEEDED, PaymentTransitionSource.CHECKOUT_RETURN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checkout return cannot change payment state");
    }

    @Test
    void routesContradictoryEvidenceFromTerminalPaymentsToReconciliation() {
        assertThat(PaymentStateMachine.transition(
                PaymentState.FAILED,
                PaymentState.REQUIRES_RECONCILIATION,
                PaymentTransitionSource.RECONCILIATION))
                .isEqualTo(PaymentState.REQUIRES_RECONCILIATION);
    }
}
