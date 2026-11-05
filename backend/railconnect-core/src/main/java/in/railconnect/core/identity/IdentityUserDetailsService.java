package in.railconnect.core.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class IdentityUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbcTemplate;

    public IdentityUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        List<UserRow> users = jdbcTemplate.query(
                """
                        SELECT user_id, email, password_hash, full_name, status, must_change_password
                        FROM users
                        WHERE email = ? OR mobile_number = ?
                        """,
                (result, rowNumber) -> new UserRow(
                        result.getObject("user_id", UUID.class),
                        result.getString("email"),
                        result.getString("password_hash"),
                        result.getString("full_name"),
                        result.getString("status"),
                        result.getBoolean("must_change_password")
                ),
                normalized,
                normalized
        );
        if (users.isEmpty()) {
            throw new UsernameNotFoundException("Invalid credentials");
        }

        UserRow user = users.get(0);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("identity:self:read"));
        authorities.add(new SimpleGrantedAuthority("identity:self:update"));
        authorities.add(new SimpleGrantedAuthority("identity:self:password"));
        authorities.add(new SimpleGrantedAuthority("identity:self:sessions"));
        authorities.addAll(jdbcTemplate.query(
                """
                        SELECT DISTINCT r.role_code, rp.permission_code
                        FROM user_roles ur
                        JOIN roles r ON r.role_code = ur.role_code
                        LEFT JOIN role_permissions rp ON rp.role_code = r.role_code
                        WHERE ur.user_id = ?
                        """,
                (result, rowNumber) -> {
                    String role = result.getString("role_code");
                    String permission = result.getString("permission_code");
                    if (permission != null) {
                        authorities.add(new SimpleGrantedAuthority(permission));
                    }
                    return new SimpleGrantedAuthority("ROLE_" + role);
                },
                user.userId()
        ));

        return new RailConnectPrincipal(
                user.userId(),
                user.email(),
                user.passwordHash(),
                user.fullName(),
                user.status(),
                user.mustChangePassword(),
                authorities.stream().distinct().toList()
        );
    }

    private record UserRow(
            UUID userId,
            String email,
            String passwordHash,
            String fullName,
            String status,
            boolean mustChangePassword
    ) {
    }
}
