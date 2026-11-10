package in.railconnect.core.cancellation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DhruvaBankRefundAdapterTest {

    @Test
    @DisplayName("Should return available false when disabled or credentials missing")
    void availableFalseWhenDisabled() {
        DhruvaBankRefundAdapter adapter = new DhruvaBankRefundAdapter(
                false, "https://dhruva-bank.vercel.app", "", ""
        );
        assertThat(adapter.available()).isFalse();
    }

    @Test
    @DisplayName("Should simulate demo refund safely when adapter is in demo/disabled mode")
    void simulatesDemoRefundWhenDisabled() {
        DhruvaBankRefundAdapter adapter = new DhruvaBankRefundAdapter(
                false, "https://dhruva-bank.vercel.app", "", ""
        );

        var request = new RefundProvider.RefundRequest(
                UUID.randomUUID(),
                "pi_test_123",
                "RC-REF-999",
                "CAN-999",
                BigDecimal.valueOf(500),
                "INR",
                "Test Cancellation",
                "idemp_999"
        );

        var result = adapter.executeRefund(request);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.merchantRefundRef()).isEqualTo("RC-REF-999");
        assertThat(result.cancellationReference()).isEqualTo("CAN-999");
        assertThat(result.amount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(result.providerRefundId()).startsWith("demo_ref_");
    }

    @Test
    @DisplayName("Should verify demo refund successfully")
    void verifiesDemoRefund() {
        DhruvaBankRefundAdapter adapter = new DhruvaBankRefundAdapter(
                false, "https://dhruva-bank.vercel.app", "", ""
        );

        var ver = adapter.verifyRefund("demo_ref_123");

        assertThat(ver.verified()).isTrue();
        assertThat(ver.status()).isEqualTo("SUCCEEDED");
        assertThat(ver.ledgerEntriesCount()).isEqualTo(2);
    }
}

