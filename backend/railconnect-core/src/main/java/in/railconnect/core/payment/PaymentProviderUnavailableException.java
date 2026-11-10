package in.railconnect.core.payment;

public class PaymentProviderUnavailableException extends RuntimeException {
    public PaymentProviderUnavailableException(String message) {
        super(message);
    }
}
