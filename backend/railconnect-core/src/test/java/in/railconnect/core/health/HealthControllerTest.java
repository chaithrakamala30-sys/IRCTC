package in.railconnect.core.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {
    @Test
    void readinessReturnsServiceUnavailableWhenDatabaseIsDown() {
        HealthEndpoint endpoint = mock(HealthEndpoint.class);
        when(endpoint.healthForPath("readiness"))
                .thenReturn(Health.down().build());
        HealthController controller = new HealthController(endpoint);

        var response = controller.readiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(false, response.getBody().success());
        assertEquals("SERVICE_UNAVAILABLE", response.getBody().error().code());
    }

    @Test
    void livenessDoesNotDependOnDatabaseReadiness() {
        HealthEndpoint endpoint = mock(HealthEndpoint.class);
        when(endpoint.healthForPath("liveness"))
                .thenReturn(Health.up().build());
        HealthController controller = new HealthController(endpoint);

        var response = controller.liveness();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().data().status());
    }
}
