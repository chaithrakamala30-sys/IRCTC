# Phase 5 — Configurable fare engine

## Scope

Phase 5 adds administrator-authored, versioned fare rules; maker-checker approval and publication; deterministic INR calculations; an admin simulator; and persisted, expiring itemized quotes for the published journeys from Phase 4. Rules can target a train, train category, coach class, passenger category, effective dates, and priority. Pricing supports distance-rate, base-plus-distance, and distance-slab policies, with optional explicitly configured charges, taxes, and passenger adjustments.

The backend is authoritative for all amounts. The client submits journey identifiers, passenger categories, and optional ages; it never submits a price or a rule version to the public quote endpoint.

## Explicit exclusions and safety boundaries

- No official railway fares, taxes, surcharges, concessions, or policy are asserted or seeded.
- The `ADULT` passenger category is only a generic base category; it has no age policy or concession.
- No matching published rule returns `FARE_CONFIGURATION_UNAVAILABLE`, not a zero fare.
- A tax, charge, discount, or age eligibility policy has no effect unless explicitly configured by an authorized administrator.
- A quote is indicative only. This phase does not reserve a seat, expose availability, create a booking or ticket, or accept payment.
- PostgreSQL remains the source of truth. No paid infrastructure or external fare provider is required.

## Implementation map

- Database: append-only Flyway migration `V8__create_fare_engine.sql`.
- Backend: `in.railconnect.core.fare`, using exact `BigDecimal` calculations and Phase 4 journey details/distances.
- Admin: responsive fare rules, passenger categories, version workflow, audit history, and simulator in the admin portal.
- Passenger: itemized quote controls and results on both mobile and laptop train-detail routes.
- Quote lifetime: `RAILCONNECT_FARE_QUOTE_VALIDITY`, ISO-8601 duration, default `PT10M`, maximum one hour.

## Rule selection

Only `PUBLISHED` versions whose inclusive effective date range contains the selected journey date are considered. Highest priority wins; ties prefer the most specific combination of train, category, and class, then the most recent start date, with a stable version-ID tie-break. An exact-scope/priority overlapping publication is rejected. See [fare rule governance](fare-rule-governance.md).

## Operational readiness

The backend and frontend compile, and deterministic calculator unit tests pass. PostgreSQL migration execution and `MasterDataFlowIT` remain unverified locally because Docker and `RAILCONNECT_DB_URL` are unavailable. Production use requires PostgreSQL-backed CI, representative rule review, and explicit verification of the deployed public API.
