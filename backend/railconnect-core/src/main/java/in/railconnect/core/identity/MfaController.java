package in.railconnect.core.identity;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/mfa")
public class MfaController {
    @PostMapping("/enroll")
    @PreAuthorize("isAuthenticated()")
    public ApiEnvelope<Void> enroll(@AuthenticationPrincipal RailConnectPrincipal principal) {
        throw unavailable();
    }

    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    public ApiEnvelope<Void> verify(@Valid @RequestBody MfaCodeRequest request) {
        throw unavailable();
    }

    @PostMapping("/disable")
    @PreAuthorize("isAuthenticated()")
    public ApiEnvelope<Void> disable(@Valid @RequestBody MfaCodeRequest request) {
        throw unavailable();
    }

    @PostMapping("/recovery")
    @PreAuthorize("isAuthenticated()")
    public ApiEnvelope<Void> useRecoveryCode(@Valid @RequestBody RecoveryCodeRequest request) {
        throw unavailable();
    }

    private static IdentityException unavailable() {
        return new IdentityException(
                HttpStatus.NOT_IMPLEMENTED,
                "MFA_NOT_AVAILABLE",
                "Authenticator enrollment is not available in Phase 2");
    }

    public record MfaCodeRequest(
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code
    ) {
    }

    public record RecoveryCodeRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{8,32}$") String code
    ) {
    }
}
