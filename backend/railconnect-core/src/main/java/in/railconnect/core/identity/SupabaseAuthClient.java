package in.railconnect.core.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class SupabaseAuthClient {
    private final RestClient restClient;
    private final String projectUrl;
    private final String anonKey;
    private final String serviceRoleKey;
    private final ObjectMapper objectMapper;

    public SupabaseAuthClient(
            ObjectMapper objectMapper,
            @Value("${railconnect.supabase.url:}") String projectUrl,
            @Value("${railconnect.supabase.anon-key:}") String anonKey,
            @Value("${railconnect.supabase.service-role-key:}") String serviceRoleKey
    ) {
        HttpClient httpClient = Objects.requireNonNull(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Objects.requireNonNull(Duration.ofSeconds(10)));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.projectUrl = projectUrl == null ? "" : projectUrl.replaceAll("/+$", "");
        this.anonKey = anonKey == null ? "" : anonKey;
        this.serviceRoleKey = serviceRoleKey == null ? "" : serviceRoleKey;
    }

    public SupabaseUser signUp(String email, String password, Map<String, Object> metadata) {
        JsonNode response = publicRequest()
                .uri(authUrl() + "/signup")
                .body(Objects.requireNonNull((Object) Map.of(
                        "email", email, "password", password, "data", metadata)))
                .retrieve()
                .onStatus(status -> status.isError(), (request, result) -> throwAuthError(result))
                .body(JsonNode.class);
        JsonNode identities = response == null ? null : response.get("identities");
        return parseUser(response, identities == null || !identities.isArray() || !identities.isEmpty());
    }

    public SupabaseUser createStaffUser(
            String email,
            String password,
            Map<String, Object> metadata
    ) {
        JsonNode response = adminRequest()
                .uri(authUrl() + "/admin/users")
                .body(Objects.requireNonNull((Object) Map.of(
                        "email", email,
                        "password", password,
                        "email_confirm", true,
                        "user_metadata", metadata
                )))
                .retrieve()
                .onStatus(status -> status.isError(), (request, result) -> throwAuthError(result))
                .body(JsonNode.class);
        return parseUser(response, true);
    }

    public SupabaseUser authenticate(String email, String password) {
        JsonNode response = publicRequest()
                .uri(authUrl() + "/token?grant_type=password")
                .body(Objects.requireNonNull((Object) Map.of("email", email, "password", password)))
                .retrieve()
                .onStatus(status -> status.isError(), (request, result) -> throwAuthError(result))
                .body(JsonNode.class);
        if (response == null || !response.hasNonNull("user")) {
            throw new SupabaseAuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        return parseUser(response.path("user"), true);
    }

    public void updatePassword(UUID userId, String password) {
        adminPutRequest()
                .uri(authUrl() + "/admin/users/" + userId)
                .body(Objects.requireNonNull((Object) Map.of("password", password)))
                .retrieve()
                .onStatus(status -> status.isError(), (request, response) -> throwAuthError(response))
                .toBodilessEntity();
    }

    public void deleteUser(UUID userId) {
        requireConfigured(serviceRoleKey, "RAILCONNECT_SUPABASE_SERVICE_ROLE_KEY");
        restClient.delete()
                .uri(authUrl() + "/admin/users/" + userId)
                .header("apikey", serviceRoleKey)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .retrieve()
                .onStatus(status -> status.isError(), (request, response) -> throwAuthError(response))
                .toBodilessEntity();
    }

    private RestClient.RequestBodyUriSpec publicRequest() {
        requireConfigured(anonKey, "RAILCONNECT_SUPABASE_ANON_KEY");
        RestClient.RequestBodyUriSpec request = restClient.post();
        request.headers(headers -> {
            headers.set("apikey", anonKey);
            headers.set("Authorization", "Bearer " + anonKey);
            headers.set("Content-Type", "application/json");
        });
        return request;
    }

    private RestClient.RequestBodyUriSpec adminRequest() {
        requireConfigured(serviceRoleKey, "RAILCONNECT_SUPABASE_SERVICE_ROLE_KEY");
        RestClient.RequestBodyUriSpec request = restClient.post();
        request.headers(headers -> {
            headers.set("apikey", serviceRoleKey);
            headers.set("Authorization", "Bearer " + serviceRoleKey);
            headers.set("Content-Type", "application/json");
        });
        return request;
    }

    private RestClient.RequestBodyUriSpec adminPutRequest() {
        requireConfigured(serviceRoleKey, "RAILCONNECT_SUPABASE_SERVICE_ROLE_KEY");
        RestClient.RequestBodyUriSpec request = restClient.put();
        request.headers(headers -> {
            headers.set("apikey", serviceRoleKey);
            headers.set("Authorization", "Bearer " + serviceRoleKey);
            headers.set("Content-Type", "application/json");
        });
        return request;
    }

    private String authUrl() {
        requireConfigured(projectUrl, "RAILCONNECT_SUPABASE_URL");
        validateProjectUrl();
        return projectUrl + "/auth/v1";
    }

    private void requireConfigured(String value, String variable) {
        if (value.isBlank()) {
            throw new SupabaseAuthException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SUPABASE_AUTH_NOT_CONFIGURED",
                    variable + " must be configured on the backend");
        }
    }

    private void validateProjectUrl() {
        try {
            java.net.URI uri = java.net.URI.create(projectUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new SupabaseAuthException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SUPABASE_AUTH_CONFIGURATION_INVALID",
                    "RAILCONNECT_SUPABASE_URL must be an HTTPS URL");
        }
    }

    private void throwAuthError(ClientHttpResponse response) throws java.io.IOException {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.BAD_GATEWAY : status;
        String code = "SUPABASE_AUTH_REQUEST_FAILED";
        JsonNode error = objectMapper.readTree(response.getBody());
        if (error != null) {
            code = error.path("error_code").asText(
                    error.path("code").asText(code));
        }
        throw new SupabaseAuthException(
                resolved,
                code);
    }

    private SupabaseUser parseUser(JsonNode node, boolean newlyCreated) {
        if (node == null || !node.hasNonNull("id") || !node.hasNonNull("email")) {
            throw new SupabaseAuthException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SUPABASE_AUTH_INVALID_RESPONSE",
                    "Supabase Auth returned an incomplete user record");
        }
        Map<String, Object> metadata = new HashMap<>();
        JsonNode metadataNode = node.path("user_metadata");
        if (metadataNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = metadataNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                metadata.put(field.getKey(), field.getValue().isNull()
                        ? null
                        : field.getValue().asText());
            }
        }
        return new SupabaseUser(
                UUID.fromString(node.path("id").asText()),
                node.path("email").asText(),
                node.path("email_confirmed_at").asText(null),
                metadata,
                newlyCreated
        );
    }

    public record SupabaseUser(
            UUID id,
            String email,
            String emailConfirmedAt,
            Map<String, Object> metadata,
            boolean newlyCreated
    ) {
    }

    public static class SupabaseAuthException extends RuntimeException {
        private final HttpStatus status;
        private final String code;

        public SupabaseAuthException(HttpStatus status, String code) {
            this(status, code, "Supabase Auth request failed");
        }

        public SupabaseAuthException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public HttpStatus status() {
            return status;
        }

        public String code() {
            return code;
        }
    }
}
