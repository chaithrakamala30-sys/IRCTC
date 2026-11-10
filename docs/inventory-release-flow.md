# Inventory Release Flow

---

## Controlled Release Sequence
1. Transaction acquires `FOR UPDATE` lock on `bookings` and `tickets`.
2. Targets individual passenger's `reservation_allocation_id`.
3. Deletes `inventory_segment_claims` exclusively matching the target allocation.
4. Updates `reservation_allocations.status` to `RELEASED`.
5. Logs released segments to `inventory_release_history`.
6. Leaves non-cancelled passenger allocations and claims intact.

