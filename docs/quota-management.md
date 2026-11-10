# Quota Management Architecture

RailConnect provides a configurable, versioned quota management engine enabling administrators to define and control quota inventory across train runs.

---

## 1. Supported Quota Types

- `GENERAL`: Open public booking quota.
- `TATKAL_STYLE`: Premium short-notice travel quota open within 24 hours of departure.
- `LADIES`: Dedicated quota for solo female passengers or women traveling with children under 12.
- `SENIOR_CITIZEN`: Lower berth preference for passengers aged 60 and above.
- `PERSONS_WITH_DISABILITIES`: Specially accessible berth allocations requiring disability certificate verification.
- `DEFENCE`: Dedicated quota for active and retired armed forces personnel.
- `STAFF`: Railway operational duty pass holders.
- `PREMIUM`: Flexible dynamic pricing quota.
- `SPECIAL`: Seasonal and tourist allocations.
- `CUSTOM`: Configurable festival or high-demand route quotas.

---

## 2. Invariant Rules

1. **Ceiling Invariant**:
   Allocated quota berths across all configured quotas for a class cannot exceed the total physical berths in the train's coaches:
   $$\sum_{q} \text{allocated\_berths}(q) \le \text{TotalPhysicalBerths}$$

2. **Historical Booking Preservation**:
   All bookings and reservations store their assigned quota at booking creation time and cannot be retroactively mutated.

