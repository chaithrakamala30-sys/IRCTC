# Fare calculation policy

## Inputs and authoritative distance

The fare service validates the train, origin, destination, journey date, and selected class against Phase 4's published journey and composition data. It uses the backend-computed origin-to-destination distance, not a client-provided distance. The journey date is used for effective-date selection, including overnight services.

## Base pricing models

- `DISTANCE_RATE`: `distance × configured rate per km`.
- `BASE_PLUS_DISTANCE`: `configured base + distance × configured rate per km`.
- `DISTANCE_SLABS`: select the one configured slab containing the journey distance, then calculate `slab base + distance × slab rate`. Lower bounds are inclusive; upper bounds are exclusive. Gaps are allowed in configuration but a journey in a gap receives a controlled configuration error. Overlap and an open-ended slab followed by another slab are rejected.

Every model must define a positive base calculation for a positive journey distance. There is no implicit default rate.

## Passenger adjustments and components

Passenger category adjustments support explicitly configured fixed or percentage charges/discounts; percentage adjustments are based on the base fare. Components support fixed, per-kilometre, percentage-of-base, and percentage-of-running-subtotal calculation. Components have an explicit `CHARGE`, `TAX`, or `DISCOUNT` type, optional passenger-category scope, minimum distance, calculation order, and optional maximum amount. Percentage values are bounded to 100%.

Discount amounts are capped at the running passenger subtotal and the final passenger amount is never negative. Charges, taxes, and discounts are not inferred from category names or train classes.

## Precision

All calculation inputs and intermediate amounts use Java `BigDecimal`; floating-point arithmetic is not used for fare decisions. Rates support four fractional digits. Each line is rounded to INR paise (two decimals) using `HALF_UP`, and passenger/quote totals are sums of those rounded lines. The stored JSON snapshot preserves the itemized result.

## Example

For a configured base of INR 2.00, a 10 km journey at INR 0.555/km yields INR 7.55. A configured 10% passenger discount is INR 0.76 after line rounding. A following 5% tax on the running subtotal is INR 0.34. The itemized total is INR 7.13. This is a calculation example, not an actual railway fare or tax policy.
