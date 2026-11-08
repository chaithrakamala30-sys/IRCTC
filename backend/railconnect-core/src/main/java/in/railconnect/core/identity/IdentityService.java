package in.railconnect.core.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class IdentityService {
    private static final String TERMS_VERSION = "phase2-2026-11";
    private static final String PRIVACY_VERSION = "phase2-2026-11";
    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final List<String> COMMON_PASSWORDS = List.of(
            "password1234", "password123!", "qwerty123456", "letmein12345",
            "welcome12345", "admin123456", "iloveyou1234", "railconnect123"
    );

    private final JdbcTemplate jdbcTemplate;
    private final SupabaseAuthClient supabaseAuthClient;
    private final PasswordEncoder passwordEncoder;
    private final IdentityUserDetailsService userDetailsService;
    private final LoginThrottleService loginThrottleService;
    private final RegistrationThrottleService registrationThrottleService;
    private final SecurityHashing hashing;
    private final SecurityAuditService auditService;
    private final SecurityContextRepository securityContextRepository;
    private final ChangeSessionIdAuthenticationStrategy sessionAuthenticationStrategy;
    private final SessionRepository<? extends Session> sessionRepository;

    public IdentityService(
            JdbcTemplate jdbcTemplate,
            SupabaseAuthClient supabaseAuthClient,
            PasswordEncoder passwordEncoder,
            IdentityUserDetailsService userDetailsService,
            LoginThrottleService loginThrottleService,
            RegistrationThrottleService registrationThrottleService,
            SecurityHashing hashing,
            SecurityAuditService auditService,
            SessionRepository<? extends Session> sessionRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.supabaseAuthClient = supabaseAuthClient;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
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

        SupabaseAuthClient.SupabaseUser authUser;
        try {
            authUser = supabaseAuthClient.signUp(email, request.password(), Map.of(
                    "railconnect_account_type", "PASSENGER",
                    "full_name", name,
                    "mobile_number", mobile,
                    "terms_version", TERMS_VERSION,
                    "privacy_version", PRIVACY_VERSION
            ));
        } catch (SupabaseAuthClient.SupabaseAuthException exception) {
            if (isSupabaseDuplicate(exception)) {
                recordDuplicateRegistration(email, mobile, servletRequest);
                return registrationResponse();
            }
            throw authProviderUnavailable(exception);
        } catch (org.springframework.web.client.RestClientException exception) {
            throw authProviderUnavailable(exception);
        }

        if (!authUser.newlyCreated()) {
            recordDuplicateRegistration(email, mobile, servletRequest);
            return registrationResponse();
        }

        try {
            persistPassengerProfile(authUser, name, mobile, TERMS_VERSION, PRIVACY_VERSION);
        } catch (RuntimeException exception) {
            deleteSupabaseUserAfterFailure(supabaseAuthClient, authUser.id(), exception);
            if (exception instanceof DuplicateKeyException && exception.getSuppressed().length == 0) {
                recordDuplicateRegistration(email, mobile, servletRequest);
                return registrationResponse();
            }
            throw exception;
        }
        auditService.record("AUTH_REGISTER", "SUCCESS", authUser.id(), authUser.id(), null, servletRequest);
        return registrationResponse(authUser.emailConfirmedAt() == null
                ? "Registration received. Check your email to confirm your account before signing in."
                : "Registration complete. You can now sign in.");
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public IdentityUserResponse login(
            LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String email = normalizeEmail(request.identifier());
        if (!email.contains("@")) {
            throw invalidCredentials();
        }
        String identifierHash = hashing.hash("login:" + email);
        if (loginThrottleService.isBlocked(identifierHash)) {
            auditService.record(
                    "AUTH_LOGIN", "BLOCKED", null, null, identifierHash, servletRequest);
            throw invalidCredentials();
        }

        SupabaseAuthClient.SupabaseUser authUser;
        try {
            authUser = supabaseAuthClient.authenticate(email, request.password());
        } catch (SupabaseAuthClient.SupabaseAuthException exception) {
            if ("email_not_confirmed".equalsIgnoreCase(exception.code())) {
                throw new IdentityException(
                        HttpStatus.FORBIDDEN,
                        "EMAIL_CONFIRMATION_REQUIRED",
                        "Confirm your email address before signing in");
            }
            if (isInvalidSupabaseCredentials(exception)) {
                authUser = linkLegacyAccount(email, request.password(), identifierHash, servletRequest);
            } else {
                throw authProviderUnavailable(exception);
            }
        } catch (org.springframework.web.client.RestClientException exception) {
            throw authProviderUnavailable(exception);
        }
        if (authUser == null) {
            loginThrottleService.recordFailure(identifierHash);
            auditService.record(
                    "AUTH_LOGIN", "FAILURE", null, null, identifierHash, servletRequest);
            throw invalidCredentials();
        }
        if (!email.equals(normalizeEmail(authUser.email()))) {
            loginThrottleService.recordFailure(identifierHash);
            auditService.record(
                    "AUTH_LOGIN", "FAILURE", null, null, identifierHash, servletRequest);
            throw invalidCredentials();
        }

        RailConnectPrincipal principal;
        try {
            principal = userDetailsService.loadUserByAuthUserId(authUser.id());
        } catch (UsernameNotFoundException exception) {
            if (!"PASSENGER".equals(authUser.metadata().get("railconnect_account_type"))) {
                throw invalidCredentials();
            }
            ensurePassengerProfile(
                    authUser,
                    String.valueOf(authUser.metadata().getOrDefault("full_name", "")),
                    String.valueOf(authUser.metadata().getOrDefault("mobile_number", "")),
                    String.valueOf(authUser.metadata().getOrDefault("terms_version", TERMS_VERSION)),
                    String.valueOf(authUser.metadata().getOrDefault("privacy_version", PRIVACY_VERSION)),
                    email,
                    identifierHash,
                    servletRequest
            );
            principal = userDetailsService.loadUserByAuthUserId(authUser.id());
        }
        if (authUser.emailConfirmedAt() != null) {
            jdbcTemplate.update(
                    """
                            UPDATE users
                            SET email_verified_at = COALESCE(email_verified_at, ?::timestamptz)
                            WHERE user_id = ?
                            """,
                    authUser.emailConfirmedAt(),
                    principal.userId()
            );
        }
        if (!"ACTIVE".equals(principal.status())) {
            loginThrottleService.recordFailure(identifierHash);
            auditService.record(
                    "AUTH_LOGIN", "FAILURE", null, null, identifierHash, servletRequest);
            throw invalidCredentials();
        }

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities());
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

    private SupabaseAuthClient.SupabaseUser linkLegacyAccount(
            String email,
            String password,
            String identifierHash,
            HttpServletRequest request
    ) {
        List<LegacyAccount> accounts = jdbcTemplate.query(
                """
                        SELECT user_id, password_hash, full_name, mobile_number,
                               terms_version, privacy_version
                        FROM users
                        WHERE lower(email) = ? AND auth_user_id IS NULL
                          AND password_hash IS NOT NULL AND status = 'ACTIVE'
                        """,
                (result, rowNumber) -> new LegacyAccount(
                        result.getObject("user_id", UUID.class),
                        result.getString("password_hash"),
                        result.getString("full_name"),
                        result.getString("mobile_number"),
                        result.getString("terms_version"),
                        result.getString("privacy_version")
                ),
                email
        );
        if (accounts.isEmpty() || !passwordEncoder.matches(password, accounts.get(0).passwordHash())) {
            return null;
        }

        LegacyAccount account = accounts.get(0);
        SupabaseAuthClient.SupabaseUser createdUser;
        try {
            createdUser = supabaseAuthClient.signUp(email, password, Map.of(
                    "railconnect_account_type", "LEGACY",
                    "full_name", account.fullName(),
                    "mobile_number", account.mobileNumber(),
                    "terms_version", account.termsVersion(),
                    "privacy_version", account.privacyVersion()
            ));
        } catch (SupabaseAuthClient.SupabaseAuthException exception) {
            if (isSupabaseDuplicate(exception)) {
                return null;
            }
            throw authProviderUnavailable(exception);
        } catch (org.springframework.web.client.RestClientException exception) {
            throw authProviderUnavailable(exception);
        }
        if (!createdUser.newlyCreated()) {
            return null;
        }

        try {
            int updated = jdbcTemplate.update(
                    """
                            UPDATE users
                            SET auth_user_id = ?, password_hash = NULL, updated_at = CURRENT_TIMESTAMP
                            WHERE user_id = ? AND auth_user_id IS NULL AND password_hash IS NOT NULL
                            """,
                    createdUser.id(),
                    account.userId()
            );
            if (updated != 1) {
                throw invalidCredentials();
            }
            auditService.record(
                    "AUTH_ACCOUNT_LINKED",
                    "SUCCESS",
                    account.userId(),
                    account.userId(),
                    identifierHash,
                    request
            );
        } catch (RuntimeException exception) {
            deleteSupabaseUserAfterFailure(supabaseAuthClient, createdUser.id(), exception);
            throw exception;
        }
        if (createdUser.emailConfirmedAt() == null) {
            throw new IdentityException(
                    HttpStatus.FORBIDDEN,
                    "EMAIL_CONFIRMATION_REQUIRED",
                    "Check your email to confirm your new authentication account before signing in");
        }
        return createdUser;
    }

    private record LegacyAccount(
            UUID userId,
            String passwordHash,
            String fullName,
            String mobileNumber,
            String termsVersion,
            String privacyVersion
    ) {
    }

    private record ExistingPassengerProfile(
            UUID userId,
            UUID authUserId,
            String status,
            boolean hasPassengerRole,
            boolean passengerOnly
    ) {
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
        RailConnectPrincipal principal = userDetailsService.loadUserByUserId(userId);
        try {
            supabaseAuthClient.authenticate(principal.email(), request.currentPassword());
        } catch (SupabaseAuthClient.SupabaseAuthException exception) {
            if (exception.status().is4xxClientError()) {
                throw new IdentityException(
                        HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID",
                        "The current password is incorrect");
            }
            throw authProviderUnavailable(exception);
        } catch (org.springframework.web.client.RestClientException exception) {
            throw authProviderUnavailable(exception);
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new IdentityException(
                    HttpStatus.BAD_REQUEST, "PASSWORD_REUSE",
                    "Choose a password that is different from the current password");
        }
        validatePassword(request.newPassword());
        try {
            UUID authUserId = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(auth_user_id, user_id) FROM users WHERE user_id = ?",
                    UUID.class,
                    userId
            );
            supabaseAuthClient.updatePassword(authUserId, request.newPassword());
        } catch (SupabaseAuthClient.SupabaseAuthException
                 | org.springframework.web.client.RestClientException exception) {
            throw authProviderUnavailable(exception);
        }

        jdbcTemplate.update(
                """
                        UPDATE users
                        SET password_hash = NULL, must_change_password = FALSE, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ?
                        """,
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
        long categories = (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);
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
                "Registration request received. Check your email for next steps.");
    }

    private static RegistrationResponse registrationResponse(String message) {
        return new RegistrationResponse(true, message);
    }

    private void persistPassengerProfile(
            SupabaseAuthClient.SupabaseUser authUser,
            String fullName,
            String mobile,
            String termsVersion,
            String privacyVersion
    ) {
        String normalizedMobile = normalizeMobile(mobile);
        if (!normalizedMobile.matches("^\\+[1-9][0-9]{7,14}$")) {
            throw new IdentityException(
                    HttpStatus.BAD_REQUEST,
                    "PROFILE_DATA_INVALID",
                    "A valid international phone number is required");
        }
        String verifiedAt = authUser.emailConfirmedAt();
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            user_id, auth_user_id, full_name, email, mobile_number, password_hash,
                            email_verified_at, terms_version, privacy_version
                        ) VALUES (?, ?, ?, ?, ?, NULL, ?::timestamptz, ?, ?)
                        """,
                authUser.id(),
                authUser.id(),
                normalizeName(fullName),
                normalizeEmail(authUser.email()),
                normalizedMobile,
                verifiedAt,
                termsVersion,
                privacyVersion
        );
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_code, assigned_by) VALUES (?, 'PASSENGER', ?)",
                authUser.id(),
                authUser.id()
        );
    }

    private void ensurePassengerProfile(
            SupabaseAuthClient.SupabaseUser authUser,
            String fullName,
            String mobile,
            String termsVersion,
            String privacyVersion,
            String email,
            String identifierHash,
            HttpServletRequest request
    ) {
        List<ExistingPassengerProfile> profiles = jdbcTemplate.query(
                """
                        SELECT u.user_id, u.auth_user_id, u.status,
                               EXISTS (
                                   SELECT 1 FROM user_roles ur
                                   WHERE ur.user_id = u.user_id AND ur.role_code = 'PASSENGER'
                               ) AS has_passenger_role,
                               NOT EXISTS (
                                   SELECT 1 FROM user_roles ur
                                   WHERE ur.user_id = u.user_id AND ur.role_code <> 'PASSENGER'
                               ) AS passenger_only
                        FROM users u
                        WHERE lower(u.email) = ?
                        """,
                (result, rowNumber) -> new ExistingPassengerProfile(
                        result.getObject("user_id", UUID.class),
                        result.getObject("auth_user_id", UUID.class),
                        result.getString("status"),
                        result.getBoolean("has_passenger_role"),
                        result.getBoolean("passenger_only")
                ),
                email
        );
        if (profiles.isEmpty()) {
            persistPassengerProfile(authUser, fullName, mobile, termsVersion, privacyVersion);
            return;
        }
        if (profiles.size() != 1 || authUser.emailConfirmedAt() == null) {
            throw invalidCredentials();
        }

        ExistingPassengerProfile profile = profiles.get(0);
        if (!"ACTIVE".equals(profile.status())
                || !profile.hasPassengerRole()
                || !profile.passengerOnly()) {
            throw invalidCredentials();
        }
        int linked = jdbcTemplate.update(
                """
                        UPDATE users
                        SET auth_user_id = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND auth_user_id IS NOT DISTINCT FROM ? AND lower(email) = ?
                        """,
                authUser.id(),
                profile.userId(),
                profile.authUserId(),
                email
        );
        if (linked != 1) {
            throw invalidCredentials();
        }
        auditService.record(
                "AUTH_ACCOUNT_LINKED",
                "SUCCESS",
                profile.userId(),
                profile.userId(),
                identifierHash,
                request
        );
    }

    static IdentityException authProviderUnavailable(Exception exception) {
        return new IdentityException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AUTH_PROVIDER_UNAVAILABLE",
                "Account authentication is temporarily unavailable. Please try again later");
    }

    static void deleteSupabaseUserAfterFailure(
            SupabaseAuthClient supabaseAuthClient,
            UUID userId,
            RuntimeException originalFailure
    ) {
        try {
            supabaseAuthClient.deleteUser(userId);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private static boolean isSupabaseDuplicate(SupabaseAuthClient.SupabaseAuthException exception) {
        return "user_already_exists".equalsIgnoreCase(exception.code())
                || "email_exists".equalsIgnoreCase(exception.code())
                || "identity_already_exists".equalsIgnoreCase(exception.code());
    }

    private static boolean isInvalidSupabaseCredentials(
            SupabaseAuthClient.SupabaseAuthException exception
    ) {
        return "invalid_credentials".equalsIgnoreCase(exception.code())
                || "invalid_grant".equalsIgnoreCase(exception.code())
                || "user_not_found".equalsIgnoreCase(exception.code());
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
