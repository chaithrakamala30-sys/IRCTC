package in.railconnect.core.identity;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.UUID;

public record RailConnectPrincipal(
        UUID userId,
        String email,
        String fullName,
        String status,
        boolean mustChangePassword,
        Collection<? extends GrantedAuthority> authorities
) {
}
