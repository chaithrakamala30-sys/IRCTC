package in.railconnect.core.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminUserRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "^\\+[1-9][0-9 ()-]{6,20}$") String mobileNumber,
        @NotBlank @Size(min = 12, max = 128) String temporaryPassword,
        @NotEmpty @Valid List<RoleGrantRequest> roles
) {
}
