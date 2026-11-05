package in.railconnect.core.identity;

import jakarta.validation.constraints.Pattern;

public record AccountStatusRequest(
        @Pattern(regexp = "^(ACTIVE|SUSPENDED|DISABLED)$") String status
) {
}
