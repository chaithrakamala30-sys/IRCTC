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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void registrationLoginProfileSessionAndLogoutUseCookieAuthentication() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "phase2-" + suffix + "@example.test";
        String mobile = "+1555" + String.format(
                Locale.ROOT,
                "%010d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000_000L));
        String password = "SecureRail!Password2026";
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
        var sessionCookie = loginResult.getResponse().getCookie("RCSESSION");
        assertThat(sessionCookie).isNotNull();

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
        var sessionCookie = loginResult.getResponse().getCookie("RCSESSION");
        assertThat(sessionCookie).isNotNull();

        assertThat(jdbcTemplate.update(
                """
                        UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP
                        WHERE session_id = ? AND revoked_at IS NULL
                        """,
                sessionCookie.getValue()
        )).isEqualTo(1);
        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));
    }
}
