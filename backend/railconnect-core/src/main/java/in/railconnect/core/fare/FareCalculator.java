package in.railconnect.core.fare;

import in.railconnect.core.fare.FareModels.FareLineItem;
import in.railconnect.core.fare.FareModels.PassengerFare;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class FareCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private FareCalculator() {
    }

    static Calculation calculate(
            BigDecimal distanceKm,
            Pricing pricing
    ) {
        List<PassengerFare> passengerFares = new ArrayList<>();
        BigDecimal grandTotal = ZERO;
        for (PassengerPricing passenger : pricing.passengers()) {
            BigDecimal baseAmount = baseFare(pricing, distanceKm);
            List<FareLineItem> items = new ArrayList<>();
            items.add(new FareLineItem("BASE_FARE", "Base fare", "BASE", baseAmount));
            BigDecimal subtotal = baseAmount;

            if (passenger.adjustment() != null) {
                BigDecimal adjustment = adjustmentAmount(passenger.adjustment(), baseAmount, subtotal);
                if (adjustment.signum() != 0) {
                    boolean discount = passenger.adjustment().type().startsWith("DISCOUNT");
                    items.add(new FareLineItem(
                            "PASSENGER_ADJUSTMENT",
                            passenger.adjustment().label(),
                            discount ? "DISCOUNT" : "CHARGE",
                            discount ? adjustment.negate() : adjustment));
                    subtotal = subtotal.add(discount ? adjustment.negate() : adjustment);
                }
            }

            for (Component component : pricing.components().stream()
                    .filter(item -> item.passengerCategoryId() == null
                            || item.passengerCategoryId().equals(passenger.categoryId()))
                    .filter(item -> item.enabled()
                            && distanceKm.compareTo(item.minimumDistanceKm()) >= 0)
                    .sorted(Comparator.comparingInt(Component::calculationOrder)
                            .thenComparing(Component::code))
                    .toList()) {
                BigDecimal amount = componentAmount(component, baseAmount, subtotal, distanceKm);
                if (component.type().equals("DISCOUNT")) {
                    amount = amount.min(subtotal.max(ZERO));
                    amount = amount.negate();
                }
                if (amount.signum() != 0) {
                    items.add(new FareLineItem(component.code(), component.label(), component.type(), amount));
                    subtotal = subtotal.add(amount);
                }
            }

            subtotal = money(subtotal.max(ZERO));
            passengerFares.add(new PassengerFare(
                    passenger.categoryCode(), passenger.categoryName(), List.copyOf(items), subtotal));
            grandTotal = grandTotal.add(subtotal);
        }
        return new Calculation(List.copyOf(passengerFares), money(grandTotal));
    }

    private static BigDecimal baseFare(Pricing pricing, BigDecimal distanceKm) {
        BigDecimal amount = switch (pricing.model()) {
            case "DISTANCE_RATE" -> distanceKm.multiply(pricing.perKmRate());
            case "BASE_PLUS_DISTANCE" -> pricing.baseAmount()
                    .add(distanceKm.multiply(pricing.perKmRate()));
            case "DISTANCE_SLABS" -> pricing.slabs().stream()
                    .filter(slab -> distanceKm.compareTo(slab.minimumDistanceKm()) >= 0)
                    .filter(slab -> slab.maximumDistanceKm() == null
                            || distanceKm.compareTo(slab.maximumDistanceKm()) < 0)
                    .findFirst()
                    .map(slab -> slab.baseAmount().add(distanceKm.multiply(slab.perKmRate())))
                    .orElseThrow(() -> new FareException(
                            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                            "FARE_DISTANCE_NOT_COVERED",
                            "The approved fare rule has no distance slab for this journey"));
            default -> throw new FareException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "FARE_RULE_INVALID",
                    "The approved fare rule uses an unsupported pricing model");
        };
        return money(amount);
    }

    private static BigDecimal adjustmentAmount(
            PassengerAdjustment adjustment,
            BigDecimal baseAmount,
            BigDecimal subtotal
    ) {
        boolean percentage = adjustment.type().endsWith("PERCENT");
        BigDecimal amount = percentage
                ? baseAmount.multiply(adjustment.value()).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)
                : money(adjustment.value());
        if (adjustment.maximumAmount() != null) amount = amount.min(adjustment.maximumAmount());
        if (adjustment.type().startsWith("DISCOUNT")) amount = amount.min(subtotal.max(ZERO));
        return money(amount);
    }

    private static BigDecimal componentAmount(
            Component component,
            BigDecimal baseAmount,
            BigDecimal subtotal,
            BigDecimal distanceKm
    ) {
        BigDecimal amount = switch (component.calculationMethod()) {
            case "FIXED" -> component.amount();
            case "PER_KM" -> component.amount().multiply(distanceKm);
            case "PERCENT_OF_BASE" -> baseAmount.multiply(component.amount())
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            case "PERCENT_OF_SUBTOTAL" -> subtotal.max(ZERO).multiply(component.amount())
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            default -> throw new FareException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "FARE_RULE_INVALID",
                    "The approved fare rule has an unsupported component calculation");
        };
        amount = money(amount);
        return component.maximumAmount() == null
                ? amount
                : amount.min(component.maximumAmount());
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    record Pricing(
            String model,
            BigDecimal baseAmount,
            BigDecimal perKmRate,
            List<DistanceSlab> slabs,
            List<Component> components,
            List<PassengerPricing> passengers
    ) {
    }

    record DistanceSlab(
            BigDecimal minimumDistanceKm,
            BigDecimal maximumDistanceKm,
            BigDecimal baseAmount,
            BigDecimal perKmRate
    ) {
    }

    record Component(
            UUID passengerCategoryId,
            String code,
            String label,
            String type,
            String calculationMethod,
            BigDecimal amount,
            BigDecimal maximumAmount,
            BigDecimal minimumDistanceKm,
            int calculationOrder,
            boolean enabled
    ) {
    }

    record PassengerPricing(
            UUID categoryId,
            String categoryCode,
            String categoryName,
            PassengerAdjustment adjustment
    ) {
    }

    record PassengerAdjustment(
            String label,
            String type,
            BigDecimal value,
            BigDecimal maximumAmount
    ) {
    }

    record Calculation(List<PassengerFare> passengers, BigDecimal totalAmount) {
    }
}
