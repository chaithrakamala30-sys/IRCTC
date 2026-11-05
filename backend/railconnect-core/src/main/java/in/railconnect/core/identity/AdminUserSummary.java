package in.railconnect.core.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserSummary(
        UUID userId,
        String fullName,
        String email,
        String mobileNumber,
        String status,
        boolean emailVerified,
        boolean mobileVerified,
        boolean mustChangePassword,
        List<RoleAssignmentResponse> roles,
        Instant createdAt
) {
}
