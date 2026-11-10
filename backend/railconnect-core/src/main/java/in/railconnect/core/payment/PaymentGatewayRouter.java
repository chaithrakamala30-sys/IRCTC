package in.railconnect.core.payment;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentGatewayRouter implements PaymentGateway {
    private final Map<PaymentProvider, PaymentProviderAdapter> adapters;

    public PaymentGatewayRouter(List<PaymentProviderAdapter> adapters) {
        Map<PaymentProvider, PaymentProviderAdapter> byProvider = new EnumMap<>(PaymentProvider.class);
        for (PaymentProviderAdapter adapter : adapters) {
            if (byProvider.putIfAbsent(adapter.provider(), adapter) != null) {
                throw new IllegalStateException("More than one payment adapter is registered for "
                        + adapter.provider());
            }
        }
        this.adapters = Map.copyOf(byProvider);
    }

    @Override
    public PaymentProviderAdapter.CheckoutSession createCheckout(
            PaymentProvider provider,
            PaymentProviderAdapter.PaymentInitiationRequest request
    ) {
        return availableAdapter(provider).createCheckout(request);
    }

    @Override
    public PaymentProviderAdapter.VerifiedProviderStatus retrieveStatus(
            PaymentProvider provider,
            String providerPaymentIntentId
    ) {
        return availableAdapter(provider).retrieveStatus(providerPaymentIntentId);
    }

    @Override
    public PaymentProviderAdapter.VerifiedProviderStatus verifyPayment(
            PaymentProvider provider,
            String providerPaymentIntentId
    ) {
        return availableAdapter(provider).verifyPayment(providerPaymentIntentId);
    }

    @Override
    public boolean isAvailable(PaymentProvider provider) {
        PaymentProviderAdapter adapter = adapters.get(provider);
        return adapter != null && adapter.available();
    }

    private PaymentProviderAdapter availableAdapter(PaymentProvider provider) {
        PaymentProviderAdapter adapter = adapters.get(provider);
        if (adapter == null || !adapter.available()) {
            throw new PaymentProviderUnavailableException(
                    "Payment provider " + provider + " is unavailable or not configured");
        }
        return adapter;
    }
}
