package in.railconnect.core.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class IdentityService {
    private static final String TERMS_VERSION = "phase2-2026-11";
    private static final String PRIVACY_VERSION = "phase2-2026-11";
    private static final String INVALID_CREDENTIALS = "Invalid email/mobile number or password";
    private static final List<String> COMMON_PASSWORDS = List.of(
            "password1234", "password123!", "qwerty123456", "letmein12345",
            "welcome12345", "admin123456", "iloveyou1234", "railconnect123"
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final LoginThrottleService loginThrottleService;
    private final RegistrationThrottleService registrationThrottleService;
    private final SecurityHashing hashing;
    private final SecurityAuditService auditService;
    private final SecurityContextRepository securityContextRepository;
    private final ChangeSessionIdAuthenticationStrategy sessionAuthenticationStrategy;
    private final SessionRepository<? extends Session> sessionRepository;

    public IdentityService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            LoginThrottleService loginThrottleService,
            RegistrationThrottleService registrationThrottleService,
            SecurityHashing hashing,
            SecurityAuditService auditService,
            SessionRepository<? extends Session> sessionRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.loginThrottleService = loginThrottleService;
        this.registrationThrottleService = registrationThrottleService;
        this.hashing = hashing;
        this.auditService = auditService;
        this.sessionRepository = sessionRepository;
        this.securityContextRepository = new HttpSessionSecurityContextRepository();
        this.sessionAuthenticationStrategy = new ChangeSessionIdAuthenticationStrategy();
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request, HttpServletRequest servletRequest) {
        String email = normalizeEmail(request.email());
        String mobile = normalizeMobile(request.mobileNumber());
        if (!mobile.matches("^\\+[1-9][0-9]{7,14}$")) {
            throw new IdentityException(
                    HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Enter a mobile number with a valid international country code");
        }
        String name = normalizeName(request.fullName());
        validatePassword(request.password());
        if (!request.password().equals(request.confirmPassword())) {
            throw new IdentityException(
                    HttpStatus.BAD_REQUEST, "PASSWORD_CONFIRMATION_MISMATCH",
                    "Password and confirmation do not match");
        }
        String remoteAddressHash = hashing.hash("registration-ip:" + servletRequest.getRemoteAddr());
        if (registrationThrottleService.isBlocked(remoteAddressHash)) {
            auditService.record("AUTH_REGISTER", "BLOCKED", null, null, remoteAddressHash, servletRequest);
            return registrationResponse();
        }
        registrationThrottleService.recordAttempt(remoteAddressHash);
        String passwordHash = passwordEncoder.encode(request.password());

        Integer duplicateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? OR mobile_number = ?",
                Integer.class,
                email,
                mobile
        );
        if (duplicateCount != null && duplicateCount > 0) {
            recordDuplicateRegistration(email, mobile, servletRequest);
            return registrationResponse();
        }

        UUID userId = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
                """
                        INSERT INTO users (
                            user_id, full_name, email, mobile_number, password_hash,
                            terms_version, privacy_version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                userId,
                name,
                email,
                mobile,
                passwordHash,
                TERMS_VERSION,
                PRIVACY_VERSION
        );
        if (inserted == 0) {
            recordDuplicateRegistration(email, mobile, servletRequest);
            return registrationResponse();
        }
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_code, assigned_by) VALUES (?, 'PASSENGER', ?)",
                userId,
                userId
        );
        auditService.record("AUTH_REGISTER", "SUCCESS", userId, userId, null, servletRequest);
        return registrationResponse();
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public IdentityUserResponse login(
            LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String identifier = normalizeIdentifier(request.identifier());
        String identifierHash = hashing.hash("login:" + identifier);
        if (loginThrottleService.isBlocked(identifierHash)) {
            auditService.record(
                    "AUTH_LOGIN", "BLOCKED", null, null, identifierHash, servletRequest);
            throw invalidCredentials();
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(identifier, request.password()));
        } catch (AuthenticationException exception) {
            loginThrottleService.recordFailure(identifierHash);
            auditService.record(
                    "AUTH_LOGIN", "FAILURE", null, null, identifierHash, servletRequest);
            throw invalidCredentials();
        }

        RailConnectPrincipal principal = (RailConnectPrincipal) authentication.getPrincipal();
        if (!"ACTIVE".equals(principal.status())) {
            loginThrottleService.recordFailure(identifierHash);
            auditService.record(
                    "AUTH_LOGIN", "FAILURE", null, null, identifierHash, servletRequest);
            throw invalidCredentials();
        }
        loginThrottleService.clear(identifierHash);
        HttpSession session = servletRequest.getSession(true);
        session.setMaxInactiveInterval(30 * 60);
        sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
        session = servletRequest.getSession(false);
        session.setAttribute(
                AuthenticatedSessionFilter.AUTHENTICATED_AT,
                Instant.now().toEpochMilli());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        String userAgent = servletRequest.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 512) {
            userAgent = userAgent.substring(0, 512);
        }
        jdbcTemplate.update(
                """
                        INSERT INTO user_sessions (
                            session_id, public_session_id, user_id, user_agent, remote_address_hash
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                session.getId(),
                UUID.randomUUID(),
                principal.userId(),
                userAgent,
                hashing.hash("remote-address:" + servletRequest.getRemoteAddr())
        );
        auditService.record(
                "AUTH_LOGIN", "SUCCESS", principal.userId(), principal.userId(), identifierHash, servletRequest);
        return userResponse(principal.userId());
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            SecurityContextHolder.clearContext();
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = principalId(authentication);
        String sessionId = session.getId();
        jdbcTemplate.update(
                "UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE session_id = ?",
                sessionId
        );
        sessionRepository.deleteById(sessionId);
        session.invalidate();
        SecurityContextHolder.clearContext();
        auditService.record("AUTH_LOGOUT", "SUCCESS", userId, userId, null, request);
    }

    public IdentityUserResponse currentUser(UUID userId) {
        return userResponse(userId);
    }

    @Transactional
    public IdentityUserResponse updateProfile(
            UUID userId,
            UpdateProfileRequest request,
            HttpServletRequest servletRequest
    ) {
        jdbcTemplate.update(
                "UPDATE users SET full_name = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                normalizeName(request.fullName()),
                userId
        );
        auditService.record("PROFILE_UPDATED", "SUCCESS", userId, userId, null, servletRequest);
        return userResponse(userId);
    }

    @Transactional
    public void changePassword(
            UUID userId,
            ChangePasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        String existingHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE user_id = ?",
                String.class,
                userId
        );
        if (!passwordEncoder.matches(request.currentPassword(), existingHash)) {
            throw new IdentityException(
                    HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID",
                    "The current password is incorrect");
        }
        validatePassword(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), existingHash)) {
            throw new IdentityException(
                    HttpStatus.BAD_REQUEST, "PASSWORD_REUSE",
                    "Choose a password that is different from the current password");
        }

        jdbcTemplate.update(
                """
                        UPDATE users
                        SET password_hash = ?, must_change_password = FALSE, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ?
                        """,
                passwordEncoder.encode(request.newPassword()),
                userId
        );
        revokeAllSessions(userId);
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        auditService.record("PASSWORD_CHANGED", "SUCCESS", userId, userId, null, servletRequest);
    }

    public List<SessionResponse> sessions(UUID userId, String currentSessionId) {
        return jdbcTemplate.query(
                """
                        SELECT us.public_session_id, us.session_id, us.created_at, us.last_seen_at, us.user_agent
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
                        result.getString("session_id").trim().equals(currentSessionId)
                ),
                userId,
                Instant.now().toEpochMilli()
        );
    }

    @Transactional
    public void revokeSession(
            UUID userId,
            UUID publicSessionId,
            HttpServletRequest request
    ) {
        List<String> internalIds = jdbcTemplate.query(
                """
                        SELECT session_id FROM user_sessions
                        WHERE public_session_id = ? AND user_id = ? AND revoked_at IS NULL
                        """,
                (result, rowNumber) -> result.getString(1),
                publicSessionId,
                userId
        );
        if (internalIds.isEmpty()) {
            throw new IdentityException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found");
        }
        String internalSessionId = internalIds.get(0);
        int updated = jdbcTemplate.update(
                """
                        UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP
                        WHERE session_id = ? AND user_id = ? AND revoked_at IS NULL
                        """,
                internalSessionId,
                userId
        );
        if (updated == 0) {
            throw new IdentityException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found");
        }
        sessionRepository.deleteById(internalSessionId);
        HttpSession current = request.getSession(false);
        if (current != null && current.getId().equals(internalSessionId)) {
            current.invalidate();
            SecurityContextHolder.clearContext();
        }
        auditService.record("SESSION_REVOKED", "SUCCESS", userId, userId, null, request);
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
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

    public void recordUnavailableRecovery(HttpServletRequest request) {
        auditService.record("AUTH_RECOVERY_UNAVAILABLE", "FAILURE", null, null, null, request);
        throw new IdentityException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PASSWORD_RECOVERY_UNAVAILABLE",
                "Self-service password recovery is unavailable until an approved delivery channel is configured");
    }

    public IdentityUserResponse userResponse(UUID userId) {
        List<IdentityUserResponse> responses = jdbcTemplate.query(
                """
                        SELECT user_id, full_name, email, mobile_number, status,
                            email_verified_at, mobile_verified_at, must_change_password, created_at
                        FROM users WHERE user_id = ?
                        """,
                (result, rowNumber) -> {
                    UUID id = result.getObject("user_id", UUID.class);
                    List<String> roles = jdbcTemplate.query(
                            "SELECT role_code FROM user_roles WHERE user_id = ? ORDER BY role_code",
                            (roleResult, roleRow) -> roleResult.getString(1),
                            id
                    );
                    return new IdentityUserResponse(
                            id,
                            result.getString("full_name"),
                            result.getString("email"),
                            result.getString("mobile_number"),
                            result.getString("status"),
                            result.getTimestamp("email_verified_at") != null,
                            result.getTimestamp("mobile_verified_at") != null,
                            result.getBoolean("must_change_password"),
                            roles,
                            result.getTimestamp("created_at").toInstant()
                    );
                },
                userId
        );
        if (responses.isEmpty()) {
            throw new IdentityException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Account not found");
        }
        return responses.get(0);
    }

    static void validatePassword(String password) {
        String normalized = Normalizer.normalize(password, Normalizer.Form.NFKC);
        boolean lower = normalized.codePoints().anyMatch(Character::isLowerCase);
        boolean upper = normalized.codePoints().anyMatch(Character::isUpperCase);
        boolean digit = normalized.codePoints().anyMatch(Character::isDigit);
        boolean symbol = normalized.codePoints().anyMatch(character -> !Character.isLetterOrDigit(character));
        long categories = List.of(lower, upper, digit, symbol).stream().filter(Boolean::booleanValue).count();
        String lowerPassword = normalized.toLowerCase(Locale.ROOT);
        if (normalized.length() < 12 || normalized.length() > 128 || categories < 3
                || COMMON_PASSWORDS.contains(lowerPassword)) {
            throw new IdentityException(
                    HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_VIOLATION",
                    "Use at least 12 characters with at least three character types, and avoid common passwords");
        }
    }

    private void recordDuplicateRegistration(
            String email,
            String mobile,
            HttpServletRequest request
    ) {
        auditService.record(
                "AUTH_REGISTER",
                "FAILURE",
                null,
                null,
                hashing.hash("registration:" + email + ":" + mobile),
                request
        );
    }

    private static RegistrationResponse registrationResponse() {
        return new RegistrationResponse(
                true,
                "Registration request received. You can try signing in; no verification message was sent.");
    }

    public void assertRecentAuthentication(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null
                ? null
                : session.getAttribute(AuthenticatedSessionFilter.AUTHENTICATED_AT);
        if (!(value instanceof Long authenticatedAt)
                || Instant.now().toEpochMilli() - authenticatedAt > 5 * 60 * 1000L) {
            throw new IdentityException(
                    HttpStatus.UNAUTHORIZED,
                    "REAUTHENTICATION_REQUIRED",
                    "Sign in again before performing this sensitive action");
        }
    }

    private static String normalizeIdentifier(String identifier) {
        String trimmed = identifier.trim();
        return trimmed.contains("@") ? normalizeEmail(trimmed) : normalizeMobile(trimmed);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMobile(String mobile) {
        return mobile.replaceAll("[\\s().-]", "");
    }

    private static String normalizeName(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFC).trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private static IdentityException invalidCredentials() {
        return new IdentityException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_CREDENTIALS);
    }

    private static UUID principalId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof RailConnectPrincipal principal) {
            return principal.userId();
        }
        return null;
    }
}
