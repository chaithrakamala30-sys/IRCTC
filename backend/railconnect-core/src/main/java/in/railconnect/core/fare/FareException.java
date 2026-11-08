package in.railconnect.core.fare;

import org.springframework.http.HttpStatus;

public class FareException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public FareException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
