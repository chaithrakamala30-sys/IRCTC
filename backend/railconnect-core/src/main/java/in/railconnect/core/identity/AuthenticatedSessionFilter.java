package in.railconnect.core.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.ApiError;
import in.railconnect.core.api.RequestIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class AuthenticatedSessionFilter extends OncePerRequestFilter {
    static final String AUTHENTICATED_AT = "RAILCONNECT_AUTHENTICATED_AT";
    private final ObjectMapper objectMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final SecurityHashing hashing;
    private final Duration absoluteTimeout;

    public AuthenticatedSessionFilter(
            ObjectMapper objectMapper,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            SecurityHashing hashing,
            @org.springframework.beans.factory.annotation.Value(
                    "${railconnect.security.absolute-session-timeout:8h}") Duration absoluteTimeout
    ) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.hashing = hashing;
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof RailConnectPrincipal principal
                && session != null) {
            Object authenticatedAtValue = session.getAttribute(AUTHENTICATED_AT);
            if (!(authenticatedAtValue instanceof Long authenticatedAt)
                    || Instant.now().toEpochMilli() - authenticatedAt > absoluteTimeout.toMillis()) {
                jdbcTemplate.update(
                        "UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE session_id = ?",
                        session.getId()
                );
                session.invalidate();
                SecurityContextHolder.clearContext();
                writeError(response, HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Your session has expired");
                return;
            }
            int activeSession = jdbcTemplate.update(
                    """
                            UPDATE user_sessions SET last_seen_at = CURRENT_TIMESTAMP
                            WHERE session_id = ? AND revoked_at IS NULL
                            """,
                    session.getId()
            );
            if (activeSession != 1) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                writeError(response, HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "Your session is no longer active");
                return;
            }
            if (principal.mustChangePassword() && !allowedDuringPasswordChange(request)) {
                writeError(
                        response,
                        HttpStatus.FORBIDDEN,
                        "PASSWORD_CHANGE_REQUIRED",
                        "Change your temporary password before continuing");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean allowedDuringPasswordChange(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ("/api/v1/auth/password/change".equals(path)
                && "POST".equals(request.getMethod()))
                || ("/api/v1/auth/logout".equals(path) && "POST".equals(request.getMethod()))
                || "/api/v1/auth/me".equals(path)
                || "/api/v1/auth/csrf".equals(path);
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiEnvelope.failure(
                        new ApiError(code, message, List.of()),
                        RequestIds.current()
                )
        );
    }
}
