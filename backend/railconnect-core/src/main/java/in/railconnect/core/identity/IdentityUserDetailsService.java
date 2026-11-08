package in.railconnect.core.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IdentityUserDetailsService {
    private final JdbcTemplate jdbcTemplate;

    public IdentityUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RailConnectPrincipal loadUserByUserId(UUID userId) throws UsernameNotFoundException {
        List<UserRow> users = jdbcTemplate.query(
                """
                        SELECT user_id, email, full_name, status, must_change_password
                        FROM users
                        WHERE user_id = ?
                        """,
                (result, rowNumber) -> new UserRow(
                        result.getObject("user_id", UUID.class),
                        result.getString("email"),
                        result.getString("full_name"),
                        result.getString("status"),
                        result.getBoolean("must_change_password")
                ),
                userId
        );
        if (users.isEmpty()) {
            throw new UsernameNotFoundException("Account not found");
        }
        return principal(users.get(0));
    }

    public RailConnectPrincipal loadUserByAuthUserId(UUID authUserId) throws UsernameNotFoundException {
        List<UserRow> users = jdbcTemplate.query(
                """
                        SELECT user_id, email, full_name, status, must_change_password
                        FROM users
                        WHERE auth_user_id = ? OR (user_id = ? AND auth_user_id IS NULL)
                        """,
                (result, rowNumber) -> new UserRow(
                        result.getObject("user_id", UUID.class),
                        result.getString("email"),
                        result.getString("full_name"),
                        result.getString("status"),
                        result.getBoolean("must_change_password")
                ),
                authUserId,
                authUserId
        );
        if (users.isEmpty()) {
            throw new UsernameNotFoundException("Account not found");
        }
        return principal(users.get(0));
    }

    private RailConnectPrincipal principal(UserRow user) {
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
                user.fullName(),
                user.status(),
                user.mustChangePassword(),
                authorities.stream().distinct().toList()
        );
    }

    private record UserRow(
            UUID userId,
            String email,
            String fullName,
            String status,
            boolean mustChangePassword
    ) {
    }
}
