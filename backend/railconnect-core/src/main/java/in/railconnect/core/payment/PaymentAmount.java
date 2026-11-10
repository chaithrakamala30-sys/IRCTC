package in.railconnect.core.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PaymentAmount {
    private PaymentAmount() {
    }

    public static long toMinorUnits(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() < 0 || !"INR".equals(currency)) {
            throw new IllegalArgumentException("A non-negative INR amount is required");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("INR amount must fit exactly in integer paise", exception);
        }
    }

    public static BigDecimal fromMinorUnits(long amountMinor) {
        if (amountMinor < 0) {
            throw new IllegalArgumentException("Payment amount cannot be negative");
        }
        return BigDecimal.valueOf(amountMinor, 2);
    }
}
