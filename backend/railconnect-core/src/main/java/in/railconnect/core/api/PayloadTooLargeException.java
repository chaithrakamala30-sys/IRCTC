package in.railconnect.core.api;

import java.io.IOException;

public class PayloadTooLargeException extends IOException {
    public PayloadTooLargeException(long limit) {
        super("Request body exceeds the configured limit of " + limit + " bytes");
    }
}
