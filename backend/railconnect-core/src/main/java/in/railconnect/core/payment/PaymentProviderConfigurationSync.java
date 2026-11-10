package in.railconnect.core.payment;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderConfigurationSync implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PaymentGateway paymentGateway;

    public PaymentProviderConfigurationSync(JdbcTemplate jdbc, PaymentGateway paymentGateway) {
        this.jdbc = jdbc;
        this.paymentGateway = paymentGateway;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean enabled = paymentGateway.isAvailable(PaymentProvider.DHRUVA_BANK);
        jdbc.update("""
                UPDATE payment_provider_configurations
                SET enabled = ?, demo_mode = TRUE,
                    contract_status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE provider_code = ?
                """, enabled, enabled ? "VERIFIED" : "MISSING", PaymentProvider.DHRUVA_BANK.name());
    }
}
