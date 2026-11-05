package in.railconnect.core.api;

import org.slf4j.MDC;

public final class RequestIds {
    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestIds() {
    }

    public static String current() {
        String requestId = MDC.get(MDC_KEY);
        return requestId == null ? "unavailable" : requestId;
    }
}
