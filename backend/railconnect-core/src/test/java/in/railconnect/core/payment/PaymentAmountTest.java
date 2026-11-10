package in.railconnect.core.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentAmountTest {
    @Test
    void convertsExactRupeesToIntegerPaise() {
        assertThat(PaymentAmount.toMinorUnits(new BigDecimal("125.34"), "INR")).isEqualTo(12534);
        assertThat(PaymentAmount.toMinorUnits(new BigDecimal("12.3"), "INR")).isEqualTo(1230);
    }

    @Test
    void rejectsNegativeAmountsUnsupportedCurrenciesAndFractionalPaise() {
        assertThatThrownBy(() -> PaymentAmount.toMinorUnits(new BigDecimal("-0.01"), "INR"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentAmount.toMinorUnits(new BigDecimal("1.00"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentAmount.toMinorUnits(new BigDecimal("1.001"), "INR"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
