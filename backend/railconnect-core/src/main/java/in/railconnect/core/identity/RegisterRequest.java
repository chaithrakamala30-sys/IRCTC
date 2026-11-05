package in.railconnect.core.identity;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "^\\+[1-9][0-9 ()-]{6,20}$") String mobileNumber,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(min = 12, max = 128) String confirmPassword,
        @AssertTrue boolean termsAccepted,
        @AssertTrue boolean privacyAccepted
) {
}
