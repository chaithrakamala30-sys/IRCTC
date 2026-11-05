package in.railconnect.core.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleGrantRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{2,40}$") String roleCode,
        @NotBlank @Pattern(regexp = "^(GLOBAL|STATION)$") String scopeType,
        @NotBlank @Size(max = 100) String scopeId
) {
}
