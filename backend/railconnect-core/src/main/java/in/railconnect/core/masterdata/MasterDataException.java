package in.railconnect.core.masterdata;

import org.springframework.http.HttpStatus;

public class MasterDataException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public MasterDataException(HttpStatus status, String code, String message) {
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
