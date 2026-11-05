package in.railconnect.core.health;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.ApiError;
import in.railconnect.core.api.ApiErrorDetail;
import in.railconnect.core.api.RequestIds;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final HealthEndpoint healthEndpoint;

    public HealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<HealthData>> health() {
        return readiness();
    }

    @GetMapping("/readiness")
    public ResponseEntity<ApiEnvelope<HealthData>> readiness() {
        HealthComponent health = healthEndpoint.healthForPath("readiness");
        return response(health.getStatus().getCode());
    }

    @GetMapping("/liveness")
    public ResponseEntity<ApiEnvelope<HealthData>> liveness() {
        HealthComponent health = healthEndpoint.healthForPath("liveness");
        return response(health.getStatus().getCode());
    }

    private ResponseEntity<ApiEnvelope<HealthData>> response(String status) {
        HttpStatus httpStatus = "UP".equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        ApiEnvelope<HealthData> envelope = httpStatus.is2xxSuccessful()
                ? ApiEnvelope.success(new HealthData(status), RequestIds.current())
                : new ApiEnvelope<>(
                        false,
                        new HealthData(status),
                        new ApiError(
                                "SERVICE_UNAVAILABLE",
                                "The service is not ready",
                                List.of(new ApiErrorDetail("status", status))),
                        java.time.Instant.now(),
                        RequestIds.current());
        return ResponseEntity.status(httpStatus).body(envelope);
    }

    public record HealthData(String status) {
    }
}
