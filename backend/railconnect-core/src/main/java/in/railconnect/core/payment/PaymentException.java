package in.railconnect.core.payment;

import org.springframework.http.HttpStatus;

public class PaymentException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final boolean safeToExpose;

    public PaymentException(HttpStatus status, String code, String message) {
        this(status, code, message, false);
    }

    public PaymentException(HttpStatus status, String code, String message, boolean safeToExpose) {
        super(message);
        this.status = status;
        this.code = code;
        this.safeToExpose = safeToExpose;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public boolean safeToExpose() {
        return safeToExpose;
    }
}
