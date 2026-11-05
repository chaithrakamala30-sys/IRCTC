package in.railconnect.core.api;

import java.time.Instant;

public record ApiEnvelope<T>(
        boolean success,
        T data,
        ApiError error,
        Instant timestamp,
        String requestId
) {
    public static <T> ApiEnvelope<T> success(T data, String requestId) {
        return new ApiEnvelope<>(true, data, null, Instant.now(), requestId);
    }

    public static ApiEnvelope<Void> failure(ApiError error, String requestId) {
        return new ApiEnvelope<>(false, null, error, Instant.now(), requestId);
    }
}
