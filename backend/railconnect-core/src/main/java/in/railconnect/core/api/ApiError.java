package in.railconnect.core.api;

import java.util.List;

public record ApiError(String code, String message, List<ApiErrorDetail> details) {
    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
