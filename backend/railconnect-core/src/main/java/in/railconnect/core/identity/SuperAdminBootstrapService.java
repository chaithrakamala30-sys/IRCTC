package in.railconnect.core.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
public class SuperAdminBootstrapService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;
    private final SuperAdminSafetyLock superAdminSafetyLock;

    public SuperAdminBootstrapService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            SecurityAuditService auditService,
            SuperAdminSafetyLock superAdminSafetyLock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
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
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            user_id, full_name, email, mobile_number, password_hash,
                            must_change_password, terms_version, privacy_version
                        ) VALUES (?, ?, ?, ?, ?, TRUE, 'staff-provisioned', 'staff-provisioned')
                        """,
                userId,
                Normalizer.normalize(fullName, Normalizer.Form.NFC).trim().replaceAll("\\s+", " "),
                normalizedEmail,
                normalizedMobile,
                passwordEncoder.encode(password)
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
    }
}
