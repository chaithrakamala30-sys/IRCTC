package in.railconnect.core.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminIdentityService {
    private static final int MAX_PAGE_SIZE = 100;
    private final JdbcTemplate jdbcTemplate;
    private final SupabaseAuthClient supabaseAuthClient;
    private final SecurityAuditService auditService;
    private final SessionRepository<? extends Session> sessionRepository;
    private final SuperAdminSafetyLock superAdminSafetyLock;

    public AdminIdentityService(
            JdbcTemplate jdbcTemplate,
            SupabaseAuthClient supabaseAuthClient,
            SecurityAuditService auditService,
            SessionRepository<? extends Session> sessionRepository,
            SuperAdminSafetyLock superAdminSafetyLock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.supabaseAuthClient = supabaseAuthClient;
        this.auditService = auditService;
        this.sessionRepository = sessionRepository;
        this.superAdminSafetyLock = superAdminSafetyLock;
    }

    public PageResponse<AdminUserSummary> users(
            int page,
            int size,
            String search,
            String status,
            String roleCode
    ) {
        validatePage(page, size);
        String searchTerm = search == null || search.isBlank()
                ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        String normalizedStatus = status == null || status.isBlank()
                ? null
                : status.trim().toUpperCase(Locale.ROOT);
        String normalizedRole = roleCode == null || roleCode.isBlank()
                ? null
                : roleCode.trim().toUpperCase(Locale.ROOT);
        String where = """
                WHERE (CAST(? AS VARCHAR) IS NULL OR lower(u.full_name) LIKE ? OR lower(u.email) LIKE ?
                    OR u.mobile_number LIKE ?)
                    AND (CAST(? AS VARCHAR) IS NULL OR u.status = ?)
                    AND (CAST(? AS VARCHAR) IS NULL OR EXISTS (
                        SELECT 1 FROM user_roles filter_ur
                        WHERE filter_ur.user_id = u.user_id AND filter_ur.role_code = ?
                    ))
                """;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users u " + where,
                Long.class,
                searchTerm, searchTerm, searchTerm, searchTerm,
                normalizedStatus, normalizedStatus,
                normalizedRole, normalizedRole
        );
        List<AdminUserSummary> items = jdbcTemplate.query(
                """
                        SELECT u.user_id, u.full_name, u.email, u.mobile_number, u.status,
                            u.email_verified_at, u.mobile_verified_at, u.must_change_password,
                            u.created_at
                        FROM users u
                        """ + where + """
                        ORDER BY u.created_at DESC, u.user_id
                        LIMIT ? OFFSET ?
                        """,
                (result, rowNumber) -> {
                    UUID userId = result.getObject("user_id", UUID.class);
                    return new AdminUserSummary(
                            userId,
                            result.getString("full_name"),
                            result.getString("email"),
                            result.getString("mobile_number"),
                            result.getString("status"),
                            result.getTimestamp("email_verified_at") != null,
                            result.getTimestamp("mobile_verified_at") != null,
                            result.getBoolean("must_change_password"),
                            roleAssignments(userId),
                            result.getTimestamp("created_at").toInstant()
                    );
                },
                searchTerm, searchTerm, searchTerm, searchTerm,
                normalizedStatus, normalizedStatus,
                normalizedRole, normalizedRole,
                size, (long) page * size
        );
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public AdminUserSummary user(UUID userId) {
        List<AdminUserSummary> result = jdbcTemplate.query(
                """
                        SELECT user_id, full_name, email, mobile_number, status,
                            email_verified_at, mobile_verified_at, must_change_password, created_at
                        FROM users WHERE user_id = ?
                        """,
                (row, rowNumber) -> new AdminUserSummary(
                        row.getObject("user_id", UUID.class),
                        row.getString("full_name"),
                        row.getString("email"),
                        row.getString("mobile_number"),
                        row.getString("status"),
                        row.getTimestamp("email_verified_at") != null,
                        row.getTimestamp("mobile_verified_at") != null,
                        row.getBoolean("must_change_password"),
                        roleAssignments(row.getObject("user_id", UUID.class)),
                        row.getTimestamp("created_at").toInstant()
                ),
                userId
        );
        if (result.isEmpty()) {
            throw new IdentityException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Account not found");
        }
        return result.get(0);
    }

    @Transactional
    public AdminUserSummary createUser(
            UUID actorUserId,
            AdminUserRequest request,
            HttpServletRequest servletRequest
    ) {
        superAdminSafetyLock.acquire();
        validateGrants(request.roles());
        IdentityService.validatePassword(request.temporaryPassword());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String mobile = request.mobileNumber().replaceAll("[\\s().-]", "");
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? OR mobile_number = ?",
                Integer.class,
                email,
                mobile
        );
        if (existing != null && existing > 0) {
            throw new IdentityException(
                    HttpStatus.CONFLICT, "ACCOUNT_CONFLICT",
                    "An account already uses this email address or mobile number");
        }

        SupabaseAuthClient.SupabaseUser authUser;
        try {
            authUser = supabaseAuthClient.createStaffUser(email, request.temporaryPassword(), java.util.Map.of(
                    "railconnect_account_type", "STAFF",
                    "full_name", request.fullName().trim().replaceAll("\\s+", " "),
                    "mobile_number", mobile
            ));
        } catch (SupabaseAuthClient.SupabaseAuthException exception) {
            if ("user_already_exists".equalsIgnoreCase(exception.code())
                    || "email_exists".equalsIgnoreCase(exception.code())
                    || "identity_already_exists".equalsIgnoreCase(exception.code())) {
                throw new IdentityException(
                        HttpStatus.CONFLICT,
                        "ACCOUNT_CONFLICT",
                        "An account already uses this email address");
            }
            throw IdentityService.authProviderUnavailable(exception);
        } catch (org.springframework.web.client.RestClientException exception) {
            throw IdentityService.authProviderUnavailable(exception);
        }

        UUID userId = authUser.id();
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO users (
                                user_id, auth_user_id, full_name, email, mobile_number, password_hash, email_verified_at,
                                must_change_password, terms_version, privacy_version
                            ) VALUES (?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, TRUE, 'staff-provisioned', 'staff-provisioned')
                            """,
                    userId,
                    userId,
                    request.fullName().trim().replaceAll("\\s+", " "),
                    email,
                    mobile
            );
            persistGrants(userId, actorUserId, request.roles());
            auditService.record("STAFF_ACCOUNT_CREATED", "SUCCESS", actorUserId, userId, null, servletRequest);
            return user(userId);
        } catch (RuntimeException exception) {
            IdentityService.deleteSupabaseUserAfterFailure(supabaseAuthClient, userId, exception);
            if (exception instanceof DuplicateKeyException && exception.getSuppressed().length == 0) {
                throw new IdentityException(
                        HttpStatus.CONFLICT, "ACCOUNT_CONFLICT",
                        "An account already uses this email address or mobile number");
            }
            throw exception;
        }
    }

    @Transactional
    public AdminUserSummary changeStatus(
            UUID actorUserId,
            UUID targetUserId,
            AccountStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        superAdminSafetyLock.acquire();
        String status = request.status();
        if (status == null) {
            throw new IdentityException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Status is required");
        }
        String normalized = status.toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "SUSPENDED", "DISABLED").contains(normalized)) {
            throw new IdentityException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unsupported account status");
        }
        String currentStatus = statusFor(targetUserId);
        if ("ACTIVE".equals(currentStatus) && !"ACTIVE".equals(normalized)
                && hasRole(targetUserId, "SUPER_ADMIN") && activeSuperAdminCount() <= 1) {
            throw lastSuperAdministrator();
        }
        jdbcTemplate.update(
                "UPDATE users SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                normalized,
                targetUserId
        );
        if (!"ACTIVE".equals(normalized)) {
            revokeSessions(targetUserId);
        }
        auditService.record(
                "ACCOUNT_STATUS_CHANGED", "SUCCESS", actorUserId, targetUserId, null, servletRequest);
        return user(targetUserId);
    }

    @Transactional
    public AdminUserSummary replaceRoles(
            UUID actorUserId,
            UUID targetUserId,
            RoleGrantsRequest request,
            HttpServletRequest servletRequest
    ) {
        superAdminSafetyLock.acquire();
        validateGrants(request.roles());
        List<String> currentRoles = rolesFor(targetUserId);
        boolean wasSuperAdmin = currentRoles.contains("SUPER_ADMIN");
        boolean remainsSuperAdmin = request.roles().stream()
                .anyMatch(grant -> "SUPER_ADMIN".equals(grant.roleCode()));
        if (wasSuperAdmin && !remainsSuperAdmin
                && "ACTIVE".equals(statusFor(targetUserId)) && activeSuperAdminCount() <= 1) {
            throw lastSuperAdministrator();
        }
        if (!userExists(targetUserId)) {
            throw new IdentityException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Account not found");
        }
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", targetUserId);
        persistGrants(targetUserId, actorUserId, request.roles());
        revokeSessions(targetUserId);
        auditService.record("USER_ROLES_REPLACED", "SUCCESS", actorUserId, targetUserId, null, servletRequest);
        return user(targetUserId);
    }

    public PageResponse<AdminAuditResponse> auditLogs(int page, int size) {
        validatePage(page, size);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security_audit_logs",
                Long.class
        );
        List<AdminAuditResponse> items = jdbcTemplate.query(
                """
                        SELECT audit.audit_id, audit.actor_user_id, actor.full_name AS actor_name,
                            audit.target_user_id, target.full_name AS target_name,
                            audit.event_type, audit.outcome, audit.correlation_id, audit.occurred_at
                        FROM security_audit_logs audit
                        LEFT JOIN users actor ON actor.user_id = audit.actor_user_id
                        LEFT JOIN users target ON target.user_id = audit.target_user_id
                        ORDER BY audit.occurred_at DESC, audit.audit_id
                        LIMIT ? OFFSET ?
                        """,
                (result, rowNumber) -> new AdminAuditResponse(
                        result.getObject("audit_id", UUID.class),
                        result.getObject("actor_user_id", UUID.class),
                        result.getString("actor_name"),
                        result.getObject("target_user_id", UUID.class),
                        result.getString("target_name"),
                        result.getString("event_type"),
                        result.getString("outcome"),
                        result.getString("correlation_id"),
                        result.getTimestamp("occurred_at").toInstant()
                ),
                size, (long) page * size
        );
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public PageResponse<AdminAuthEventResponse> authenticationEvents(
            UUID userId,
            int page,
            int size
    ) {
        validatePage(page, size);
        if (!userExists(userId)) {
            throw new IdentityException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Account not found");
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authentication_events WHERE user_id = ?",
                Long.class,
                userId
        );
        List<AdminAuthEventResponse> items = jdbcTemplate.query(
                """
                        SELECT event_id, user_id, event_type, outcome, correlation_id, occurred_at
                        FROM authentication_events
                        WHERE user_id = ?
                        ORDER BY occurred_at DESC, event_id
                        LIMIT ? OFFSET ?
                        """,
                (result, rowNumber) -> new AdminAuthEventResponse(
                        result.getObject("event_id", UUID.class),
                        result.getObject("user_id", UUID.class),
                        result.getString("event_type"),
                        result.getString("outcome"),
                        result.getString("correlation_id"),
                        result.getTimestamp("occurred_at").toInstant()
                ),
                userId, size, (long) page * size
        );
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public List<SessionResponse> sessions(UUID userId) {
        if (!userExists(userId)) {
            throw new IdentityException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Account not found");
        }
        return jdbcTemplate.query(
                """
                        SELECT us.public_session_id, us.created_at, us.last_seen_at, us.user_agent
                        FROM user_sessions us
                        JOIN SPRING_SESSION s ON s.session_id = us.session_id
                        WHERE us.user_id = ? AND us.revoked_at IS NULL
                            AND s.expiry_time > ?
                        ORDER BY us.last_seen_at DESC
                        """,
                (result, rowNumber) -> new SessionResponse(
                        result.getObject("public_session_id", UUID.class).toString(),
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("last_seen_at").toInstant(),
                        result.getString("user_agent"),
                        false
                ),
                userId,
                java.time.Instant.now().toEpochMilli()
        );
    }

    public List<AdminRoleResponse> roles() {
        return jdbcTemplate.query(
                "SELECT role_code, display_name FROM roles ORDER BY role_code",
                (result, rowNumber) -> {
                    String code = result.getString("role_code");
                    List<String> permissions = jdbcTemplate.query(
                            "SELECT permission_code FROM role_permissions WHERE role_code = ? ORDER BY permission_code",
                            (permissionResult, permissionRow) -> permissionResult.getString(1),
                            code
                    );
                    return new AdminRoleResponse(code, result.getString("display_name"), permissions);
                }
        );
    }

    public List<PermissionResponse> permissions() {
        return jdbcTemplate.query(
                "SELECT permission_code, description FROM permissions ORDER BY permission_code",
                (result, rowNumber) -> new PermissionResponse(
                        result.getString("permission_code"),
                        result.getString("description")
                )
        );
    }

    @Transactional
    public void revokeUserSession(
            UUID actorUserId,
            UUID targetUserId,
            UUID publicSessionId,
            HttpServletRequest servletRequest
    ) {
        List<String> internalIds = jdbcTemplate.query(
                """
                        SELECT session_id FROM user_sessions
                        WHERE user_id = ? AND public_session_id = ? AND revoked_at IS NULL
                        """,
                (result, rowNumber) -> result.getString(1),
                targetUserId,
                publicSessionId
        );
        if (internalIds.isEmpty()) {
            throw new IdentityException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found");
        }
        String internalSessionId = internalIds.get(0);
        int updated = jdbcTemplate.update(
                """
                        UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND session_id = ? AND revoked_at IS NULL
                        """,
                targetUserId,
                internalSessionId
        );
        if (updated == 0) {
            throw new IdentityException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found");
        }
        sessionRepository.deleteById(internalSessionId);
        auditService.record("ADMIN_SESSION_REVOKED", "SUCCESS", actorUserId, targetUserId, null, servletRequest);
    }

    private void validateGrants(List<RoleGrantRequest> grants) {
        Set<String> unique = new HashSet<>();
        for (RoleGrantRequest grant : grants) {
            if (!unique.add(grant.roleCode() + ":" + grant.scopeType() + ":" + grant.scopeId())) {
                throw new IdentityException(
                        HttpStatus.BAD_REQUEST, "DUPLICATE_ROLE_GRANT", "A role scope can be assigned only once");
            }
            if (!roleExists(grant.roleCode())) {
                throw new IdentityException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE", "The requested role does not exist");
            }
            if ("STATION_MANAGER".equals(grant.roleCode())) {
                if (!"STATION".equals(grant.scopeType()) || "*".equals(grant.scopeId())) {
                    throw new IdentityException(
                            HttpStatus.BAD_REQUEST, "ROLE_SCOPE_REQUIRED",
                            "Station Managers must be assigned to a specific station scope");
                }
            } else if (!"GLOBAL".equals(grant.scopeType()) || !"*".equals(grant.scopeId())) {
                throw new IdentityException(
                        HttpStatus.BAD_REQUEST, "ROLE_SCOPE_INVALID",
                        "This role must use the global scope");
            }
        }
    }

    private void persistGrants(UUID targetUserId, UUID actorUserId, List<RoleGrantRequest> grants) {
        for (RoleGrantRequest grant : grants) {
            jdbcTemplate.update(
                    """
                            INSERT INTO user_roles (
                                user_id, role_code, scope_type, scope_id, assigned_by
                            ) VALUES (?, ?, ?, ?, ?)
                            """,
                    targetUserId,
                    grant.roleCode(),
                    grant.scopeType(),
                    grant.scopeId(),
                    actorUserId
            );
        }
    }

    private List<RoleAssignmentResponse> roleAssignments(UUID userId) {
        return jdbcTemplate.query(
                """
                        SELECT role_code, scope_type, scope_id FROM user_roles
                        WHERE user_id = ? ORDER BY role_code, scope_type, scope_id
                        """,
                (result, rowNumber) -> new RoleAssignmentResponse(
                        result.getString("role_code"),
                        result.getString("scope_type"),
                        result.getString("scope_id")
                ),
                userId
        );
    }

    private List<String> rolesFor(UUID userId) {
        return jdbcTemplate.query(
                "SELECT DISTINCT role_code FROM user_roles WHERE user_id = ? ORDER BY role_code",
                (result, rowNumber) -> result.getString(1),
                userId
        );
    }

    private String statusFor(UUID userId) {
        List<String> statuses = jdbcTemplate.query(
                "SELECT status FROM users WHERE user_id = ?",
                (result, rowNumber) -> result.getString(1),
                userId
        );
        if (statuses.isEmpty()) {
            throw new IdentityException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Account not found");
        }
        return statuses.get(0);
    }

    private boolean userExists(UUID userId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE user_id = ?)",
                Boolean.class,
                userId
        ));
    }

    private boolean roleExists(String roleCode) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM roles WHERE role_code = ?)",
                Boolean.class,
                roleCode
        ));
    }

    private boolean hasRole(UUID userId, String roleCode) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM user_roles WHERE user_id = ? AND role_code = ?)",
                Boolean.class,
                userId,
                roleCode
        ));
    }

    private int activeSuperAdminCount() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(DISTINCT u.user_id)
                        FROM users u
                        JOIN user_roles ur ON ur.user_id = u.user_id
                        WHERE u.status = 'ACTIVE' AND ur.role_code = 'SUPER_ADMIN'
                        """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private void revokeSessions(UUID userId) {
        List<String> sessionIds = jdbcTemplate.query(
                "SELECT session_id FROM user_sessions WHERE user_id = ? AND revoked_at IS NULL",
                (result, rowNumber) -> result.getString(1).trim(),
                userId
        );
        for (String sessionId : sessionIds) {
            sessionRepository.deleteById(sessionId);
        }
        jdbcTemplate.update(
                "UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL",
                userId
        );
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IdentityException(
                    HttpStatus.BAD_REQUEST, "INVALID_PAGE",
                    "Page must be non-negative and size must be between 1 and 100");
        }
    }

    private static IdentityException lastSuperAdministrator() {
        return new IdentityException(
                HttpStatus.CONFLICT,
                "LAST_SUPER_ADMINISTRATOR",
                "The last active Super Administrator cannot be removed or deactivated");
    }
}
