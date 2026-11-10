package in.railconnect.core.payment;

public interface PaymentGateway {
    PaymentProviderAdapter.CheckoutSession createCheckout(
            PaymentProvider provider,
            PaymentProviderAdapter.PaymentInitiationRequest request
    );

    PaymentProviderAdapter.VerifiedProviderStatus retrieveStatus(
            PaymentProvider provider,
            String providerPaymentIntentId
    );

    PaymentProviderAdapter.VerifiedProviderStatus verifyPayment(
            PaymentProvider provider,
            String providerPaymentIntentId
    );

    boolean isAvailable(PaymentProvider provider);
}
