# Railway reservation domain model

## Core concepts and identities

- **Station:** approved location, unique RailConnect code, display names, timezone, lifecycle, and data provenance.
- **Train:** permanent service identity and train number, independent of any one operating date.
- **Route version:** ordered permanent/dated stop pattern for a train. Each `train_stop` has a unique sequence within the version, station, planned arrival/departure offsets, day offsets, and boarding/alighting eligibility.
- **Operating schedule:** recurrence/effective dates and operating weekdays referencing an approved route version. It is configuration, not an actual departure instance.
- **Train run:** one concrete train on one local operating date, with resolved UTC timestamps and a frozen route/schedule version. Delays/disruptions are mutable operational events against that run, not edits to the historical route.
- **Travel segment:** a passenger journey from one stop sequence to a later sequence on the same run; use a half-open interval `[from_sequence, to_sequence)` to make adjacency non-overlapping.
- **Class / coach / seat (berth):** class is a sellable product category; coach is a physical vehicle/configuration; seat/berth is an individually identifiable physical place with type, coach, and class.
- **Quota:** policy-defined allocation pool or eligibility class applied to a run/segment/class. It does not create another physical seat. Capacity must never allow parallel quota allocations of the same berth.
- **Fare rule:** immutable, versioned computation inputs and eligibility/rounding behavior; quote records the rule version and expiry.
- **Passenger:** person record associated with a booking. Minimize identity attributes, protect access, and distinguish account holder from travelers.
- **Reservation:** booking aggregate with owner, reference/PNR, state, rule versions, and one or more passenger journey segments.
- **Inventory hold / seat allocation:** temporary or confirmed claim on one physical berth over one run segment; hold expiry/state is persisted.
- **RAC / waitlist entry:** ordered, versioned queue record when policy allows acceptance without a confirmed berth; never imply a confirmed seat.
- **Payment / refund:** external payment attempt and immutable internal ledger events; separate from reservation and inventory states.
- **Ticket / PNR record:** issued artifact/reference for an eligible confirmed reservation; PNR is opaque and must not be a predictable database ID.
- **Chart preparation:** policy-controlled snapshot/cutoff process for a run; exact behavior is intentionally unspecified until approved rules exist.
- **Disruption:** operational event affecting run status, schedule, service, inventory, passenger communication, or refund obligations.

## Relationship summary

`train -> route_version -> ordered train_stops -> station`

`train -> operating_schedule -> train_run(local operating date, frozen route_version) -> run stops`

`train_run -> coach -> seat`; `train_run -> journey_inventory/quota_inventory`

`reservation -> passengers`; each passenger segment references `(train_run, from_stop, to_stop, class, quota)` and may have one seat allocation or an explicit RAC/waitlist outcome.

`seat_allocation -> physical seat + run + [from_stop, to_stop)`; overlapping active ranges for that seat/run are forbidden by the database.

`reservation -> payment_attempts -> provider_events / ledger_entries -> refunds`; ticket issuance follows the approved reservation transition.

The ER relationship diagram is in [domain-relationships.mmd](diagrams/domain-relationships.mmd).

The first implemented catalogue slice is described in [Phase 3 master data](phase-3-master-data.md). Its concrete tables are `stations`/`station_versions`, `trains`, `train_routes`/`train_route_versions`/`train_route_stops`, `train_schedules` and child day/exception rows, plus coach configuration/layout and composition version tables. It does not create dated `train_runs` or inventory records.

## Permanent route vs dated run

A permanent route describes intended service topology. It must not itself be mutated to represent a particular date's delay, cancellation, or temporary rerouting. A schedule creates a run for a specific local operating date using the schedule's timezone and frozen route version. The run snapshots stop sequence and planned instants, allowing later route edits to affect only future runs. A disruption creates an auditable operational revision for the selected run and its impacted passengers.

Timezone conversion must handle midnight/day offsets and daylight-saving transitions using the station's IANA zone; India currently uses Asia/Kolkata as the project default, but station timezone is explicit data. A journey date is a local service date, not a UTC date.

## Passenger journey representation

Store origin and destination as foreign keys to stop rows on the same train run, not merely station codes. Enforce `origin.sequence < destination.sequence`. Store class/quota and selected rule version at quote/booking time. One reservation can contain multiple passengers and segments; a future multi-train itinerary is a separate explicit composition, not an overloaded single-segment record.

Seat assignment is at passenger-segment granularity. For a journey that uses a seat across adjacent intervals, allocations may be represented as one range or normalized, but the overlap invariant must remain equivalent. Reusing the same berth on disjoint segments is allowed only after database validation.

## State and policy ownership

Reservation, payment, refund, and inventory are separate state machines; see [reservation engine](07-reservation-engine-design.md) and [payment architecture](08-payment-architecture.md). Do not encode assumed Indian Railways policy in UI conditionals. Rules require an owner, version, effective range, change reason, tests, and approval. The system should store original decision inputs and outcome for audit/replay, while restricting replay from creating new side effects.

## Extensible rule evaluation

Use a versioned rule registry keyed by domain and effective period. Rule evaluation is deterministic and side-effect-free: input snapshot + version -> decision + explanation. Persist rule-version IDs and normalized quote inputs on the reservation. Publish a new version instead of editing historical rule content. Define explicit behavior for unknown/missing policy as “not bookable” rather than a permissive default.

## Data provenance and open questions

Initial station, route, schedule, quota, and fare sources are not supplied. Each import must identify provider/owner, authorization, effective date, checksums, validation outcomes, and approving administrator. Exact PNR format, quota semantics, RAC allocation, waitlist ordering/promotion, chart preparation, cancellation fees, refunds, accessibility guarantees, and disruption policy require approval and verified specifications.
