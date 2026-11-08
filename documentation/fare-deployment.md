# Fare engine deployment and configuration

## Database rollout

Apply Flyway migration V8 after V7 using the existing PostgreSQL migration pipeline. The migration adds fare rules, immutable versions, components, passenger categories, audit events, quotes, permission grants, and database guards. It seeds only the generic `ADULT` category; it does not seed a price, tax, or discount.

The pending V7 repair allows the Phase 4 `EFFECTIVE_RANGE_CLOSED` master-data audit action. V8 relies on the V5/V7 railway tables and must not be deployed without all preceding migrations. Back up according to the existing approved database policy and verify restore separately.

## Application setting

`RAILCONNECT_FARE_QUOTE_VALIDITY` accepts an ISO-8601 duration. It defaults to `PT10M`; startup rejects zero, negative, or greater-than-one-hour values. Existing PostgreSQL and session settings remain required. No Redis, new cloud resource, third-party fare service, or paid infrastructure is introduced.

## Rollout checks

1. Confirm V1–V8 succeeded in `flyway_schema_history`.
2. Confirm fare permissions and the `ADULT` category exist.
3. Deploy the backend before enabling passenger fare controls.
4. Have distinct author/editor and checker accounts configure a reviewed test rule in a non-production environment.
5. Confirm anonymous POST quote behavior, CSRF, owner-bound reads, expiry, audit history, and mobile/laptop UI.
6. Enable no production pricing until values and tax policies are supplied and reviewed by the responsible operator.

PostgreSQL CI and deployed-host verification are still required; local build success does not prove a migration was applied.
