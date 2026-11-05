package in.railconnect.core.identity;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileController {
    private final IdentityService identityService;

    public ProfileController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping
    public ApiEnvelope<IdentityUserResponse> profile(
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return ApiEnvelope.success(
                identityService.currentUser(principal.userId()),
                RequestIds.current()
        );
    }

    @PatchMapping
    public ApiEnvelope<IdentityUserResponse> update(
            @AuthenticationPrincipal RailConnectPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiEnvelope.success(
                identityService.updateProfile(principal.userId(), request, servletRequest),
                RequestIds.current()
        );
    }
}
