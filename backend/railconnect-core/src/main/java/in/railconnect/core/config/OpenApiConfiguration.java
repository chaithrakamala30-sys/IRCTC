package in.railconnect.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI railConnectOpenApi() {
        return new OpenAPI().info(new Info()
                .title("RailConnect Core API")
                .version("v1")
                .description("RailConnect API foundation. Booking operations are not enabled."));
    }
}
