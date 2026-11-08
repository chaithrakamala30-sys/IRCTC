package in.railconnect.core.reservation;

import org.springframework.http.HttpStatus;

public class ReservationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ReservationException(HttpStatus status, String code, String message) {
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
