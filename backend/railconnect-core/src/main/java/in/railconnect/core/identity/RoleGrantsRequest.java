package in.railconnect.core.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RoleGrantsRequest(@NotEmpty @Valid List<RoleGrantRequest> roles) {
}
