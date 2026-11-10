# Cancellation Policy Engine

RailConnect utilizes a configurable, versioned cancellation policy engine stored in `cancellation_policy_versions`.

---

## 1. Version 1 Standard Tiers

1. **More than 48 Hours Before Departure**:
   - Flat administrative deduction per travel class:
     - `1A`: ₹240
     - `EC`: ₹200
     - `2A`: ₹180
     - `3A`: ₹140
     - `CC`: ₹120
     - `SL`: ₹80
     - `2S`: ₹40

2. **Between 12 and 48 Hours Before Departure**:
   - `25%` deduction with class-based minimums matching the flat fee above.

3. **Between 4 and 12 Hours Before Departure**:
   - `50%` deduction of the passenger's fare.

4. **Less than 4 Hours Before Departure / Post-Departure**:
   - Non-refundable (`100%` deduction).
