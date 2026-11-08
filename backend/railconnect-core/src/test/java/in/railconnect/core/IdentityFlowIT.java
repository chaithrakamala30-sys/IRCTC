package in.railconnect.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import in.railconnect.core.identity.AdminIdentityService;
import in.railconnect.core.identity.SupabaseAuthClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(
        named = "RAILCONNECT_DB_URL",
        matches = "jdbc:postgresql:.+"
)
class IdentityFlowIT {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AdminIdentityService adminIdentityService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private SupabaseAuthClient supabaseAuthClient;

    @Test
    void securityAuditEventsIncludeActorAndTargetNames() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        insertAuditTestUser(actorId, "Audit Actor");
        insertAuditTestUser(targetId, "Audit Target");
        UUID auditId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO security_audit_logs (
                            audit_id, actor_user_id, target_user_id, event_type, outcome, correlation_id
                        ) VALUES (?, ?, ?, 'TEST_AUDIT_EVENT', 'SUCCESS', ?)
                        """,
                auditId,
                actorId,
                targetId,
                UUID.randomUUID().toString()
        );

        var auditPage = adminIdentityService.auditLogs(0, 100);

        assertThat(auditPage.items())
                .filteredOn(entry -> auditId.equals(entry.auditId()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.actorName()).isEqualTo("Audit Actor");
                    assertThat(entry.targetName()).isEqualTo("Audit Target");
                });
    }

    @Test
    void registrationLoginProfileSessionAndLogoutUseCookieAuthentication() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "phase2-" + suffix + "@example.test";
        String mobile = "+1555" + String.format(
                Locale.ROOT,
                "%010d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000_000L));
        String password = "SecureRail!Password2026";
        stubSupabaseUser(email, password, "Phase Two Test", mobile);
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode csrfBody = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        String csrfToken = csrfBody.path("data").path("token").asText();
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        String registrationBody = """
                {
                  "fullName": "Phase Two Test",
                  "email": "%s",
                  "mobileNumber": "%s",
                  "password": "%s",
                  "confirmPassword": "%s",
                  "termsAccepted": true,
                  "privacyAccepted": true
                }
                """.formatted(email, mobile, password, password);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestReceived").value(true));

        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.requestReceived").value(true));

        String loginBody = """
                {"identifier":"%s","password":"%s"}
                """.formatted(email.toUpperCase(), password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn();
        var sessionCookie = Objects.requireNonNull(loginResult.getResponse().getCookie("RCSESSION"));
        String principalName = jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM users WHERE email = ?",
                String.class,
                email
        );
        String internalSessionId = jdbcTemplate.queryForObject(
                "SELECT SESSION_ID FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                String.class,
                principalName
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT PRINCIPAL_NAME FROM SPRING_SESSION WHERE SESSION_ID = ?",
                String.class,
                internalSessionId
        )).isEqualTo(principalName);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_sessions WHERE session_id = ?",
                Integer.class,
                internalSessionId
        )).isEqualTo(1);

        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        mockMvc.perform(get("/api/v1/admin/users").cookie(sessionCookie))
                .andExpect(status().isForbidden());

        MvcResult sessionsResult = mockMvc.perform(get("/api/v1/auth/sessions").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].current").value(true))
                .andReturn();
        JsonNode sessionsBody = objectMapper.readTree(sessionsResult.getResponse().getContentAsString());
        String publicSessionId = sessionsBody.path("data").path(0).path("sessionId").asText();
        assertThat(publicSessionId).isNotEqualTo(sessionCookie.getValue());

        mockMvc.perform(delete("/api/v1/auth/sessions/{sessionId}", publicSessionId)
                        .cookie(sessionCookie)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
        MvcResult secondLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn();
        var secondSessionCookie = Objects.requireNonNull(
                secondLoginResult.getResponse().getCookie("RCSESSION"));
        mockMvc.perform(get("/api/v1/auth/me").cookie(secondSessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(secondSessionCookie)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk());
        MvcResult thirdLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn();
        var thirdSessionCookie = Objects.requireNonNull(
                thirdLoginResult.getResponse().getCookie("RCSESSION"));
        mockMvc.perform(get("/api/v1/auth/me").cookie(thirdSessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void confirmedPassengerLoginLinksAnExistingUnlinkedProfileByEmail() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "linked-" + suffix + "@example.test";
        String mobile = "+1555" + String.format(
                Locale.ROOT,
                "%010d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000_000L));
        String password = "SecureRail!Password2026";
        UUID profileId = UUID.randomUUID();
        UUID authUserId = UUID.nameUUIDFromBytes(email.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID staleAuthUserId = UUID.randomUUID();
        stubSupabaseUser(email, password, "Existing Passenger", mobile);
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            user_id, auth_user_id, full_name, email, mobile_number, password_hash,
                            terms_version, privacy_version
                        ) VALUES (?, ?, 'Existing Passenger', ?, ?, NULL, 'legacy', 'legacy')
                        """,
                profileId,
                staleAuthUserId,
                email,
                mobile
        );
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_code) VALUES (?, 'PASSENGER')",
                profileId
        );

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode csrfBody = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        String csrfToken = csrfBody.path("data").path("token").asText();
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT auth_user_id FROM users WHERE user_id = ?",
                UUID.class,
                profileId
        )).isEqualTo(authUserId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role_code = 'PASSENGER'",
                Integer.class,
                profileId
        )).isEqualTo(1);
    }

    @Test
    void serverSideRevocationInvalidatesAnExistingBrowserSession() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "revoked-" + suffix + "@example.test";
        String mobile = "+1555" + String.format(
                Locale.ROOT,
                "%010d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000_000L));
        String password = "SecureRail!Password2026";
        stubSupabaseUser(email, password, "Revocation Test", mobile);
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode csrfBody = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        String csrfToken = csrfBody.path("data").path("token").asText();
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String registrationBody = """
                {
                  "fullName": "Revocation Test",
                  "email": "%s",
                  "mobileNumber": "%s",
                  "password": "%s",
                  "confirmPassword": "%s",
                  "termsAccepted": true,
                  "privacyAccepted": true
                }
                """.formatted(email, mobile, password, password);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        var sessionCookie = Objects.requireNonNull(loginResult.getResponse().getCookie("RCSESSION"));

        String internalSessionId = jdbcTemplate.queryForObject(
                """
                        SELECT us.session_id
                        FROM user_sessions us
                        JOIN users u ON u.user_id = us.user_id
                        WHERE u.email = ? AND us.revoked_at IS NULL
                        """,
                String.class,
                email
        );
        assertThat(jdbcTemplate.update(
                """
                        UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP
                        WHERE session_id = ? AND revoked_at IS NULL
                        """,
                internalSessionId
        )).isEqualTo(1);
        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));
    }

    @Test
    void superAdminCanListAccountsWithoutOptionalFilters() throws Exception {
        String email = "admin-list-" + UUID.randomUUID().toString().replace("-", "") + "@example.test";
        String mobile = "+1555" + String.format(
                Locale.ROOT,
                "%010d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000_000L));
        String password = "SecureRail!Password2026";
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            user_id, auth_user_id, full_name, email, mobile_number, password_hash,
                            email_verified_at, must_change_password, terms_version, privacy_version
                        ) VALUES (?, ?, 'Admin List Test', ?, ?, NULL, CURRENT_TIMESTAMP, FALSE, 'test', 'test')
                        """,
                userId,
                userId,
                email,
                mobile
        );
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_code) VALUES (?, 'SUPER_ADMIN')",
                userId
        );
        when(supabaseAuthClient.authenticate(eq(email), eq(password))).thenReturn(
                new SupabaseAuthClient.SupabaseUser(
                        userId,
                        email,
                        "2026-11-07T00:00:00Z",
                        Map.of("railconnect_account_type", "STAFF"),
                        true
                )
        );

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode csrfBody = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfBody.path("data").path("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        var sessionCookie = Objects.requireNonNull(loginResult.getResponse().getCookie("RCSESSION"));

        mockMvc.perform(get("/api/v1/admin/users?page=0&size=50").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].email").value(email));
    }

    @Test
    void legacyPasswordLoginLinksProfileToSupabaseAuthAndClearsLegacyHash() throws Exception {
        String email = "legacy-" + UUID.randomUUID().toString().replace("-", "") + "@example.test";
        String mobile = "+1555" + String.format(
                Locale.ROOT,
                "%010d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000_000L));
        String password = "LegacyRail!Password2026";
        UUID localUserId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            user_id, full_name, email, mobile_number, password_hash,
                            terms_version, privacy_version
                        ) VALUES (?, 'Legacy User', ?, ?, ?, 'legacy', 'legacy')
                        """,
                localUserId,
                email,
                mobile,
                passwordEncoder.encode(password)
        );
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_code, assigned_by) VALUES (?, 'PASSENGER', ?)",
                localUserId,
                localUserId
        );

        var authUser = new SupabaseAuthClient.SupabaseUser(
                authUserId,
                email,
                "2026-11-07T00:00:00Z",
                Map.of("railconnect_account_type", "LEGACY"),
                true
        );
        when(supabaseAuthClient.authenticate(eq(email), eq(password)))
                .thenThrow(new SupabaseAuthClient.SupabaseAuthException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS"));
        when(supabaseAuthClient.signUp(eq(email), eq(password), anyMap())).thenReturn(authUser);

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode csrfBody = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        String csrfToken = csrfBody.path("data").path("token").asText();
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn();

        assertThat(loginResult.getResponse().getCookie("RCSESSION")).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT auth_user_id FROM users WHERE user_id = ?",
                UUID.class,
                localUserId
        )).isEqualTo(authUserId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE user_id = ?",
                String.class,
                localUserId
        )).isNull();
    }

    private void stubSupabaseUser(String email, String password, String fullName, String mobile) {
        UUID userId = UUID.nameUUIDFromBytes(email.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, Object> metadata = Map.of(
                "railconnect_account_type", "PASSENGER",
                "full_name", fullName,
                "mobile_number", mobile,
                "terms_version", "phase2-2026-11",
                "privacy_version", "phase2-2026-11"
        );
        var user = new SupabaseAuthClient.SupabaseUser(
                userId,
                email,
                "2026-11-07T00:00:00Z",
                metadata,
                true
        );
        when(supabaseAuthClient.signUp(eq(email), eq(password), anyMap())).thenReturn(user);
        when(supabaseAuthClient.authenticate(eq(email), eq(password))).thenReturn(user);
    }

    private void insertAuditTestUser(UUID userId, String name) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            user_id, full_name, email, mobile_number, password_hash,
                            terms_version, privacy_version
                        ) VALUES (?, ?, ?, ?, NULL, 'test', 'test')
                        """,
                userId,
                name,
                "audit-" + suffix + "@example.test",
                "+1555" + suffix.substring(0, 10)
        );
    }
}
