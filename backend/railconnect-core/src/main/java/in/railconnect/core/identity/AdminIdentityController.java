package in.railconnect.core.identity;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminIdentityController {
    private final AdminIdentityService adminIdentityService;
    private final IdentityService identityService;

    public AdminIdentityController(
            AdminIdentityService adminIdentityService,
            IdentityService identityService
    ) {
        this.adminIdentityService = adminIdentityService;
        this.identityService = identityService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('identity:users:read')")
    public ApiEnvelope<PageResponse<AdminUserSummary>> users(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @Size(max = 40) String role
    ) {
        return ApiEnvelope.success(
                adminIdentityService.users(page, size, search, status, role),
                RequestIds.current()
        );
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('identity:users:read')")
    public ApiEnvelope<AdminUserSummary> user(@PathVariable UUID userId) {
        return ApiEnvelope.success(adminIdentityService.user(userId), RequestIds.current());
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('identity:users:create')")
    public ApiEnvelope<AdminUserSummary> createUser(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @Valid @RequestBody AdminUserRequest request,
            HttpServletRequest servletRequest
    ) {
        identityService.assertRecentAuthentication(servletRequest);
        return ApiEnvelope.success(
                adminIdentityService.createUser(actor.userId(), request, servletRequest),
                RequestIds.current()
        );
    }

    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasAuthority('identity:users:status')")
    public ApiEnvelope<AdminUserSummary> changeStatus(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID userId,
            @Valid @RequestBody AccountStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        identityService.assertRecentAuthentication(servletRequest);
        return ApiEnvelope.success(
                adminIdentityService.changeStatus(actor.userId(), userId, request, servletRequest),
                RequestIds.current()
        );
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('identity:users:roles')")
    public ApiEnvelope<AdminUserSummary> replaceRoles(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID userId,
            @Valid @RequestBody RoleGrantsRequest request,
            HttpServletRequest servletRequest
    ) {
        identityService.assertRecentAuthentication(servletRequest);
        return ApiEnvelope.success(
                adminIdentityService.replaceRoles(actor.userId(), userId, request, servletRequest),
                RequestIds.current()
        );
    }

    @DeleteMapping("/users/{userId}/sessions/{sessionId}")
    @PreAuthorize("hasAuthority('identity:users:status')")
    public ApiEnvelope<Void> revokeSession(
            @AuthenticationPrincipal RailConnectPrincipal actor,
            @PathVariable UUID userId,
            @PathVariable UUID sessionId,
            HttpServletRequest servletRequest
    ) {
        identityService.assertRecentAuthentication(servletRequest);
        adminIdentityService.revokeUserSession(
                actor.userId(), userId, sessionId, servletRequest);
        return ApiEnvelope.success(null, RequestIds.current());
    }

    @GetMapping("/users/{userId}/authentication-events")
    @PreAuthorize("hasAuthority('identity:users:read')")
    public ApiEnvelope<PageResponse<AdminAuthEventResponse>> authenticationEvents(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return ApiEnvelope.success(
                adminIdentityService.authenticationEvents(userId, page, size),
                RequestIds.current()
        );
    }

    @GetMapping("/users/{userId}/sessions")
    @PreAuthorize("hasAuthority('identity:users:read')")
    public ApiEnvelope<List<SessionResponse>> sessions(@PathVariable UUID userId) {
        return ApiEnvelope.success(adminIdentityService.sessions(userId), RequestIds.current());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('identity:roles:read')")
    public ApiEnvelope<List<AdminRoleResponse>> roles() {
        return ApiEnvelope.success(adminIdentityService.roles(), RequestIds.current());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('identity:roles:read')")
    public ApiEnvelope<List<PermissionResponse>> permissions() {
        return ApiEnvelope.success(adminIdentityService.permissions(), RequestIds.current());
    }

    @GetMapping("/security/audit-logs")
    @PreAuthorize("hasAuthority('security:audit:read')")
    public ApiEnvelope<PageResponse<AdminAuditResponse>> auditLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return ApiEnvelope.success(
                adminIdentityService.auditLogs(page, size),
                RequestIds.current()
        );
    }
}
