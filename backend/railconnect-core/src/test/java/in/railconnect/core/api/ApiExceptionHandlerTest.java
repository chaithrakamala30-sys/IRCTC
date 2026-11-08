package in.railconnect.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {
    private static final long REQUEST_LIMIT_BYTES = 16;
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ValidationTestController())
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(
                    new RequestCorrelationFilter(),
                    new RequestBodyLimitFilter(
                            new ObjectMapper().registerModule(new JavaTimeModule()),
                            REQUEST_LIMIT_BYTES))
            .build();

    @Test
    void validationFailureUsesStandardErrorEnvelopeAndRequestId() throws Exception {
        mockMvc.perform(post("/test-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", matchesPattern("[A-Za-z0-9._-]{1,64}")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Request validation failed"))
                .andExpect(jsonPath("$.error.details[0].field").value("name"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void malformedJsonReturnsSanitizedClientError() throws Exception {
        mockMvc.perform(post("/test-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.error.message")
                        .value("The request body is missing or malformed"));
    }

    @Test
    void oversizedRequestIsRejectedWithPayloadTooLarge() throws Exception {
        mockMvc.perform(post("/test-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"longer-than-limit\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void authorizationDeniedUsesForbiddenErrorEnvelope() throws Exception {
        mockMvc.perform(post("/test-forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message")
                        .value("You are not allowed to perform this action"));
    }

    @RestController
    static class ValidationTestController {
        @PostMapping("/test-validation")
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @PostMapping("/test-forbidden")
        void forbidden() {
            throw new AuthorizationDeniedException("insufficient permissions");
        }
    }

    record TestRequest(@NotBlank @Size(max = 40) String name) {
    }
}
