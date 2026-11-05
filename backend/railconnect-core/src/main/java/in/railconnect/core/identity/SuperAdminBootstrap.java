package in.railconnect.core.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "railconnect.bootstrap-super-admin", havingValue = "true")
public class SuperAdminBootstrap implements ApplicationRunner {
    private final SuperAdminBootstrapService bootstrapService;
    private final ApplicationContext applicationContext;
    private final String fullName;
    private final String email;
    private final String mobile;
    private final String password;

    public SuperAdminBootstrap(
            SuperAdminBootstrapService bootstrapService,
            ApplicationContext applicationContext,
            @Value("${RAILCONNECT_BOOTSTRAP_SUPER_ADMIN_NAME:}") String fullName,
            @Value("${RAILCONNECT_BOOTSTRAP_SUPER_ADMIN_EMAIL:}") String email,
            @Value("${RAILCONNECT_BOOTSTRAP_SUPER_ADMIN_MOBILE:}") String mobile,
            @Value("${RAILCONNECT_BOOTSTRAP_SUPER_ADMIN_PASSWORD:}") String password
    ) {
        this.bootstrapService = bootstrapService;
        this.applicationContext = applicationContext;
        this.fullName = fullName;
        this.email = email;
        this.mobile = mobile;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (fullName.isBlank() || email.isBlank() || mobile.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap requires name, email, mobile, and password environment variables");
        }
        bootstrapService.create(fullName, email, mobile, password);
        SpringApplication.exit(applicationContext, () -> 0);
    }
}
