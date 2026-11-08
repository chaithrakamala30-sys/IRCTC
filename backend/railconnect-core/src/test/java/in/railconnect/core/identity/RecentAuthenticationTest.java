package in.railconnect.core.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.session.SessionRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecentAuthenticationTest {
    @SuppressWarnings("unchecked")
    private final IdentityService identityService = new IdentityService(
            mock(org.springframework.jdbc.core.JdbcTemplate.class),
            mock(SupabaseAuthClient.class),
            mock(org.springframework.security.crypto.password.PasswordEncoder.class),
            mock(IdentityUserDetailsService.class),
            mock(LoginThrottleService.class),
            mock(RegistrationThrottleService.class),
            mock(SecurityHashing.class),
            mock(SecurityAuditService.class),
            mock(SessionRepository.class)
    );

    @Test
    void acceptsAuthenticationWithinFifteenMinutes() {
        assertThatCode(() -> identityService.assertRecentAuthentication(requestAuthenticatedMinutesAgo(10)))
                .doesNotThrowAnyException();
    }

    @Test
    void requiresAuthenticationAfterFifteenMinutes() {
        assertThatThrownBy(() ->
                identityService.assertRecentAuthentication(requestAuthenticatedMinutesAgo(16)))
                .isInstanceOf(IdentityException.class)
                .extracting(exception -> ((IdentityException) exception).code())
                .isEqualTo("REAUTHENTICATION_REQUIRED");
    }

    private static HttpServletRequest requestAuthenticatedMinutesAgo(int minutes) {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(AuthenticatedSessionFilter.AUTHENTICATED_AT))
                .thenReturn(Instant.now().minusSeconds(minutes * 60L).toEpochMilli());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(session);
        return request;
    }
}
