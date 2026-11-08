package in.railconnect.core.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
public class SuperAdminBootstrapService {
    private final JdbcTemplate jdbcTemplate;
    private final SupabaseAuthClient supabaseAuthClient;
    private final SecurityAuditService auditService;
    private final SuperAdminSafetyLock superAdminSafetyLock;

    public SuperAdminBootstrapService(
            JdbcTemplate jdbcTemplate,
            SupabaseAuthClient supabaseAuthClient,
            SecurityAuditService auditService,
            SuperAdminSafetyLock superAdminSafetyLock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.supabaseAuthClient = supabaseAuthClient;
        this.auditService = auditService;
        this.superAdminSafetyLock = superAdminSafetyLock;
    }

    @Transactional
    public void create(String fullName, String email, String mobile, String password) {
        superAdminSafetyLock.acquire();
        IdentityService.validatePassword(password);
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_roles WHERE role_code = 'SUPER_ADMIN'",
                Integer.class
        );
        if (existing != null && existing > 0) {
            throw new IllegalStateException(
                    "A Super Administrator already exists; bootstrap is a one-time operation");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedMobile = mobile.replaceAll("[\\s().-]", "");
        if (!normalizedMobile.matches("^\\+[1-9][0-9]{7,14}$")) {
            throw new IllegalStateException("Bootstrap mobile number must use international E.164 format");
        }
        String normalizedName = Normalizer.normalize(fullName, Normalizer.Form.NFC)
                .trim().replaceAll("\\s+", " ");
        SupabaseAuthClient.SupabaseUser authUser;
        try {
            authUser = supabaseAuthClient.createStaffUser(normalizedEmail, password, java.util.Map.of(
                    "railconnect_account_type", "STAFF",
                    "full_name", normalizedName,
                    "mobile_number", normalizedMobile
            ));
        } catch (SupabaseAuthClient.SupabaseAuthException exception) {
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
                    normalizedName,
                    normalizedEmail,
                    normalizedMobile
            );
            jdbcTemplate.update(
                    """
                            INSERT INTO user_roles (
                                user_id, role_code, scope_type, scope_id, assigned_by
                            ) VALUES (?, 'SUPER_ADMIN', 'GLOBAL', '*', ?)
                            """,
                    userId,
                    userId
            );
            auditService.record("SUPER_ADMIN_BOOTSTRAPPED", "SUCCESS", userId, userId, null, null);
        } catch (RuntimeException exception) {
            IdentityService.deleteSupabaseUserAfterFailure(supabaseAuthClient, userId, exception);
            throw exception;
        }
    }
}
