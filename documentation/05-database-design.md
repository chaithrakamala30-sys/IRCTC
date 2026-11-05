# PostgreSQL database design

This is a logical design, not an executed migration. PostgreSQL is the authority for reservation, inventory, and financial invariants. Use Flyway for ordered, reviewed migrations; validate the syntax and lock behavior on the selected PostgreSQL version before implementation.

## ER overview

See [domain-relationships.mmd](diagrams/domain-relationships.mmd). Use generated surrogate UUIDs for public-facing entities; unique business keys remain explicit. Use UTC `timestamptz`, `date` for local operating date, and integer stop sequence. Currency amounts use integer minor units plus ISO currency or exact `numeric`; never float.

## Table catalogue

| Area | Tables and principal relationships |
|---|---|
| Identity | `users`, `credentials`, `user_mfa_factors`, `sessions`, `roles`, `permissions`, `user_role_assignments`, `role_permissions`, `admin_scope_assignments` |
| Catalogue | `stations`, `trains`, `route_versions`, `train_stops`, `operating_schedules`, `train_classes`, `coach_templates`, `coach_template_seats` |
| Operations | `train_runs`, `run_stops`, `run_status_events`, `disruptions`, `operational_announcements` |
| Inventory | `run_coaches`, `run_seats`, `journey_inventory`, `quota_inventory`, `inventory_holds`, `seat_allocations`, `inventory_events` |
| Rules/fare | `rule_sets`, `rule_versions`, `fare_rules`, `cancellation_rules`, `quota_rules`, `fare_quotes` |
| Booking | `reservations`, `reservation_segments`, `reservation_passengers`, `waitlist_entries`, `rac_entries`, `tickets`, `pnr_records`, `chart_snapshots` |
| Finance | `payment_attempts`, `payment_provider_events`, `financial_ledger_entries`, `refunds`, `reconciliation_batches`, `reconciliation_items` |
| Platform | `idempotency_records`, `outbox_events`, `job_runs`, `audit_events`, `support_cases`, `notification_deliveries` |

Primary keys: UUID; use opaque random public references for PNR/ticket IDs. Foreign keys are non-null where lifecycle requires an owner. Unique constraints include station code, train number, route-version sequence, run/date/train identity, `(run_id, coach_code)`, `(run_id, seat_code)`, `(reservation_id, passenger_ordinal)`, provider event ID, `(scope, idempotency_key)`, and rule-version number per ruleset.

## Critical constraints and indexes

1. `train_stops`: unique `(route_version_id, sequence_no)` and valid arrival/departure ordering; run-stop station and sequence are unique per run.
2. `reservation_segments`: foreign keys to origin/destination stops on the same run; `from_sequence < to_sequence`; class/quota/rule version non-null.
3. `inventory_holds`: run/seat, half-open `int4range` segment, state, `expires_at`, reservation, and idempotency key. Index `(state, expires_at)` for reclamation and `(run_id, seat_id)`.
4. `seat_allocations`: run/seat/segment range, state, reservation passenger, allocation source. Install `btree_gist` where available, then a partial exclusion constraint equivalent to:

   ```sql
   EXCLUDE USING gist (
     train_run_id WITH =,
     seat_id WITH =,
     segment_range WITH &&
   ) WHERE (state IN ('HELD', 'CONFIRMED'));
   ```

   The range is `[from_sequence, to_sequence)`. This prevents two active claims on the same physical seat/run with overlapping segments. Validate parent FKs and ensure seat belongs to run/class.
5. A partial unique index prevents more than one live allocation per reservation passenger and segment. State checks enforce allowed enum values; state transitions are enforced in the application service and audited.
6. `quota_inventory`: unique `(run_id, class_id, quota_id, segment_range)` or a normalized per-stop-segment key; nonnegative counters/capacities. Keep capacity allocation in the same DB transaction with row lock and check `used + requested <= capacity`.
7. `idempotency_records`: unique `(principal_scope, idempotency_key)`, request hash, response/resource reference, state, created/expiry timestamps. Never store raw secrets in keys.
8. Payment/provider event: unique `(provider, provider_event_id)`; ledger has immutable event key and nonnegative amount constraints where applicable. Refund total must not exceed settled refundable amount (enforced transactionally under payment-row lock).
9. Index foreign keys and access paths: `(train_id, journey_date)`, `(origin_station_id, destination_station_id, journey_date)`, `(train_run_id, sequence_no)`, `(reservation_id, state)`, `(user_id, created_at DESC)`, `(payment_state, updated_at)`, `(outbox_state, available_at)`, audit `(actor_id, occurred_at DESC)`.

### Expired hold correctness

Do not put `now()` in a partial index predicate; it is not immutable. A hold remains protecting inventory until a transaction changes its persisted state. The hold sweeper selects expired `HELD` rows with `FOR UPDATE SKIP LOCKED`, rechecks expiry, transitions hold/allocation to released, records an event, and commits. An allocation attempt may perform the same cleanup for its locked candidate before retrying. Delayed cleanup may reduce apparent availability but cannot double-book.

## Atomic allocation and concurrency

1. Validate payload/rule-version references and store an idempotency record (same key + different canonical request hash -> conflict).
2. Start a transaction; lock the idempotency row or insert it using a unique constraint.
3. Lock quota-capacity rows in stable sorted order; verify capacity for all passengers and segment(s).
4. Lock candidate seats in deterministic `(run_id, coach_sequence, seat_sequence)` order using `FOR UPDATE SKIP LOCKED`; exclude candidates with overlapping active allocations. Optionally clean stale holds under lock.
5. Insert holds and matching segment ranges. The exclusion constraint is the final integrity barrier if concurrent sessions race or selection logic is wrong.
6. Insert reservation, passenger-segment outcomes, payment intent, and outbox event in the same transaction. Commit before calling any payment provider.
7. On exclusion/serialization/deadlock conflict, roll back and retry a bounded number of times with jitter only for retryable SQL states; otherwise return explicit conflict/unavailable.
8. After verified payment success, lock reservation/payment/holds in stable order, validate state and expiry/grace rule, transition allocations and reservation atomically, write ledger and outbox. If payment succeeded after hold expiry, reconcile and apply an explicit recovery/refund policy; never silently allocate an already-taken berth.

The reservation sequence is in [inventory-allocation.mmd](diagrams/inventory-allocation.mmd). Redis is never an integrity boundary.

## Transaction boundaries

- Search/read queries: read-only transaction; snapshot freshness disclosed.
- Fare quote: persist immutable inputs/rule reference and expiry; no inventory lock.
- Booking command: one DB transaction for idempotency, seat/quota holds, reservation/passengers, payment intent, outbox.
- Payment callback: persist authenticated raw/minimized event and unique provider event ID; state/ledger update transaction; acknowledge only after durable commit.
- Cancellation: transaction for eligibility decision, reservation transition, allocation release, refund obligation, ledger/outbox; provider refund occurs afterward.
- Outbox/job: lease and mark completion in transactions; handler side effect must be idempotent.
- Admin configuration: versioned write with approval and audit in same transaction.

## Migration, partitioning, retention

Migrations are append-only and run once per release with a single migration lock. Use expand/contract for zero-downtime only if deployment supports overlapping app versions; on free demo hosting, a maintenance window is safer. Never use schema auto-update in production.

Start unpartitioned; add partitioning only after measurement. Candidate time partitions: append-heavy `audit_events`, `payment_provider_events`, `outbox_events`, and historical operational events by month; reservations may partition by journey month only if foreign-key/unique-key constraints and operational queries are proven. Keep active run allocations and inventory rows co-located and accessible for atomic locking.

Retention policy needs privacy/legal approval. Separate operational retention from financial/audit retention; archive/pseudonymize only under approved policy. Do not delete records needed to reconcile pending payments or prove booking state. Apply TTL only to disposable data such as expired idempotency responses after a safe replay window and cache.

## Backups and recovery

Use provider backups only after limits/restore semantics are verified; maintain an encrypted, independently stored logical backup and test restoration before any real user data. Free Supabase currently lists no included backups in the fetched free-tier summary; free Render Postgres explicitly has no backups and expires. A single database copy is not disaster recovery. Define owner-approved RPO/RTO and restore drills before production.
