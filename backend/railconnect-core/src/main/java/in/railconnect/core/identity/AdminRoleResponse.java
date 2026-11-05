package in.railconnect.core.identity;

import java.util.List;

public record AdminRoleResponse(String roleCode, String displayName, List<String> permissions) {
}
