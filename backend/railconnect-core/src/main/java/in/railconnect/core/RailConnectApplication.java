package in.railconnect.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RailConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(RailConnectApplication.class, args);
    }
}
