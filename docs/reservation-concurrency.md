# Reservation Concurrency & Locking Strategy

To ensure zero over-allocation, gap-free queue numbering, and race-free promotions, RailConnect employs PostgreSQL row-level locks and transaction isolation safeguards.

---

## 1. Concurrency Controls

1. **Queue Numbering**:
   - `reservation_queues` maintains `current_highest_sequence`.
   - Increments are performed within an exclusive row lock:
     ```sql
     SELECT current_highest_sequence FROM reservation_queues
     WHERE train_run_id = ? AND coach_class_code = ? AND quota_code = ? AND queue_type = ?
     FOR UPDATE;
     ```

2. **Berth Allocation & Claim Verification**:
   - Free berths in `inventory_berths` are selected using `FOR UPDATE SKIP LOCKED`.
   - Prevents duplicate allocation across parallel booking workers.

3. **Quota Capacity Enforcement**:
   - Aggregations on `quota_inventory_allocations` validate that cumulative quota berths do not exceed total physical berths in `inventory_coaches`.

