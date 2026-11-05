package in.railconnect.core.health;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {
    private final String applicationName;

    public SystemInfoController(
            @Value("${spring.application.name:RailConnect}") String applicationName
    ) {
        this.applicationName = applicationName;
    }

    @GetMapping("/info")
    public ApiEnvelope<SystemInfo> info() {
        return ApiEnvelope.success(
                new SystemInfo(applicationName, "v1"),
                RequestIds.current());
    }

    public record SystemInfo(String application, String apiVersion) {
    }
}
