# Reservation Promotion Engine

The Reservation Promotion Engine automates the cascade of berth allocations whenever inventory is freed through passenger cancellations or quota adjustments.

---

## 1. Concurrency Safeguards

1. **Row Locking (`FOR UPDATE SKIP LOCKED`)**:
   Prevents concurrent promotion workers from locking the same RAC or Waitlist entry or assigning the same physical berth.

2. **Idempotency**:
   Promotions are recorded in `reservation_promotions` with unique timestamps and `booking_passenger_id` links, preventing duplicate promotions.

3. **Status Transitions**:
   Valid transitions:
   - `WAITLISTED` $\rightarrow$ `RAC`
   - `RAC` $\rightarrow$ `CONFIRMED`
   - `WAITLISTED` $\rightarrow$ `CONFIRMED` (direct promotion when unshared berths are freed)
