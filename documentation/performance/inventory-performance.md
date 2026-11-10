# Seat Inventory & High-Concurrency Allocation Performance

## 1. Concurrency Model
- PostgreSQL remains the single authoritative source of truth for seat allocation.
- Segment-based berth locking prevents duplicate seat allocation across overlapping journey segments without locking entire coaches.

## 2. Integrity Guarantees Under Load
- **Zero Overselling**: Database unique constraints on `(train_run_id, coach_code, berth_number, segment_index)` strictly reject conflicting concurrent reservations.
- **Temporary Hold Expiry**: Redis/in-memory hold locks expire after 10 minutes, releasing unconfirmed berths automatically.
- **Idempotent Confirmations**: Payment confirmations verify existing reservation state, preventing duplicate ticket generation upon webhook retries.

