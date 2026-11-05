package in.railconnect.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(
        named = "RAILCONNECT_DB_URL",
        matches = "jdbc:postgresql:.+"
)
class ApiEndpointsIT {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReadinessAndSystemInfoUseTheApiEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/api/v1/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application").value("RailConnect Core"))
                .andExpect(jsonPath("$.data.apiVersion").value("v1"));
    }

    @Test
    void protectedRoutesReturnStandardUnauthorizedErrors() throws Exception {
        mockMvc.perform(get("/api/v1/not-enabled"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", matchesPattern("[A-Za-z0-9._-]{1,64}")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void livenessIsIndependentOfDatabaseReadinessGroup() throws Exception {
        mockMvc.perform(get("/api/v1/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
