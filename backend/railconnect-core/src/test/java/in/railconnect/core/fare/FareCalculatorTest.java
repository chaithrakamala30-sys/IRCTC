package in.railconnect.core.fare;

import in.railconnect.core.fare.FareModels.PassengerFare;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FareCalculatorTest {
    private static final UUID ADULT_ID = UUID.randomUUID();

    @Test
    void appliesPassengerDiscountThenTaxWithInrRounding() {
        FareCalculator.Pricing pricing = new FareCalculator.Pricing(
                "BASE_PLUS_DISTANCE",
                new BigDecimal("2.00"),
                new BigDecimal("0.5550"),
                List.of(),
                List.of(new FareCalculator.Component(
                        null, "TAX", "Configured tax", "TAX", "PERCENT_OF_SUBTOTAL",
                        new BigDecimal("5.0000"), null, BigDecimal.ZERO, 10, true)),
                List.of(new FareCalculator.PassengerPricing(
                        ADULT_ID, "ADULT", "Adult",
                        new FareCalculator.PassengerAdjustment(
                                "Passenger discount", "DISCOUNT_PERCENT", new BigDecimal("10.0000"), null))));

        FareCalculator.Calculation result = FareCalculator.calculate(new BigDecimal("10.00"), pricing);
        PassengerFare passenger = result.passengers().get(0);

        assertThat(passenger.items()).extracting("amount")
                .containsExactly(new BigDecimal("7.55"), new BigDecimal("-0.76"), new BigDecimal("0.34"));
        assertThat(passenger.totalAmount()).isEqualByComparingTo("7.13");
        assertThat(result.totalAmount()).isEqualByComparingTo("7.13");
    }

    @Test
    void selectsDistanceSlabUsingInclusiveLowerAndExclusiveUpperBoundary() {
        FareCalculator.Pricing pricing = new FareCalculator.Pricing(
                "DISTANCE_SLABS",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(
                        new FareCalculator.DistanceSlab(
                                BigDecimal.ZERO, new BigDecimal("100.00"),
                                BigDecimal.ZERO, new BigDecimal("1.2500")),
                        new FareCalculator.DistanceSlab(
                                new BigDecimal("100.00"), null,
                                new BigDecimal("200.00"), new BigDecimal("1.5000"))),
                List.of(),
                List.of(passenger()));

        assertThat(FareCalculator.calculate(new BigDecimal("100.00"), pricing).totalAmount())
                .isEqualByComparingTo("350.00");
    }

    @Test
    void rejectsJourneysThatDoNotFitAnyConfiguredSlab() {
        FareCalculator.Pricing pricing = new FareCalculator.Pricing(
                "DISTANCE_SLABS",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(new FareCalculator.DistanceSlab(
                        BigDecimal.ZERO, new BigDecimal("100.00"),
                        BigDecimal.ZERO, new BigDecimal("1.0000"))),
                List.of(),
                List.of(passenger()));

        assertThatThrownBy(() -> FareCalculator.calculate(new BigDecimal("100.00"), pricing))
                .isInstanceOf(FareException.class)
                .hasMessageContaining("no distance slab");
    }

    @Test
    void capsExplicitDiscountAtThePassengerSubtotal() {
        FareCalculator.Pricing pricing = new FareCalculator.Pricing(
                "DISTANCE_RATE",
                BigDecimal.ZERO,
                new BigDecimal("1.0000"),
                List.of(),
                List.of(new FareCalculator.Component(
                        null, "PROMO", "Configured discount", "DISCOUNT", "FIXED",
                        new BigDecimal("20.0000"), null, BigDecimal.ZERO, 1, true)),
                List.of(passenger()));

        FareCalculator.Calculation result = FareCalculator.calculate(new BigDecimal("10.00"), pricing);

        assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(result.passengers().get(0).items().get(1).amount()).isEqualByComparingTo("-10.00");
    }

    private static FareCalculator.PassengerPricing passenger() {
        return new FareCalculator.PassengerPricing(ADULT_ID, "ADULT", "Adult", null);
    }
}
