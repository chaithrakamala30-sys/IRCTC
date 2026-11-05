package in.railconnect.core.identity;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {
    private final IdentityService identityService;

    public AuthController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/csrf")
    public ApiEnvelope<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ApiEnvelope.success(Map.of("token", csrfToken.getToken()), RequestIds.current());
    }

    @PostMapping("/register")
    public ApiEnvelope<RegistrationResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiEnvelope.success(
                identityService.register(request, servletRequest),
                RequestIds.current()
        );
    }

    @PostMapping("/login")
    public ApiEnvelope<IdentityUserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        return ApiEnvelope.success(
                identityService.login(request, servletRequest, servletResponse),
                RequestIds.current()
        );
    }

    @PostMapping("/logout")
    public ApiEnvelope<Map<String, String>> logout(HttpServletRequest request) {
        identityService.logout(request);
        return ApiEnvelope.success(Map.of("status", "logged_out"), RequestIds.current());
    }

    @GetMapping("/me")
    public ApiEnvelope<IdentityUserResponse> me(
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return ApiEnvelope.success(
                identityService.currentUser(principal.userId()),
                RequestIds.current()
        );
    }

    @GetMapping("/sessions")
    public ApiEnvelope<List<SessionResponse>> sessions(
            @AuthenticationPrincipal RailConnectPrincipal principal,
            HttpServletRequest request
    ) {
        HttpSession session = request.getSession(false);
        String currentSessionId = session == null ? "" : session.getId();
        return ApiEnvelope.success(
                identityService.sessions(principal.userId(), currentSessionId),
                RequestIds.current()
        );
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiEnvelope<Map<String, String>> revokeSession(
            @AuthenticationPrincipal RailConnectPrincipal principal,
            @PathVariable UUID sessionId,
            HttpServletRequest request
    ) {
        identityService.revokeSession(principal.userId(), sessionId, request);
        return ApiEnvelope.success(Map.of("status", "revoked"), RequestIds.current());
    }

    @PostMapping("/password/change")
    public ApiEnvelope<Map<String, String>> changePassword(
            @AuthenticationPrincipal RailConnectPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        identityService.changePassword(principal.userId(), request, servletRequest);
        return ApiEnvelope.success(
                Map.of("status", "password_changed", "reauthenticationRequired", "true"),
                RequestIds.current()
        );
    }

    @PostMapping("/password/forgot")
    public ApiEnvelope<Void> forgotPassword(HttpServletRequest request) {
        identityService.recordUnavailableRecovery(request);
        return ApiEnvelope.success(null, RequestIds.current());
    }
}
