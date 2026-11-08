# Phase 4 — Passenger train search

## Status and scope

Phase 4 adds anonymous, read-only search over RailConnect's own published railway master data. It does not import an official railway feed and does not create dated train runs. Results are timetable services derived from effective routes and schedules.

Included: station autocomplete, direct journey search, local-time filters, category and coach-class filters, sorting, pagination, route details, recent-search shortcuts, and separate mobile and laptop webpages at `/mobile` and `/laptop`.

Excluded: fares, seat availability, live running status, reservations, booking, payments, external railway data providers, paid search services, and Phase 5 work. No synthetic results are returned when the database has no matching published data.

## Data prerequisites

Search returns data only when administrators have published and validated active stations, a train, an ordered route version, a schedule version, and a published composition for coach-class display. No railway inventory is seeded by this feature. Until those records exist, a successful search may correctly return an empty result page.

## Public web and API surfaces

- `/mobile` and `/mobile/train-details` provide the compact touch-oriented experience.
- `/laptop` and `/laptop/train-details` provide the wide-screen experience.
- `/api/v1/public/search-config`, `/api/v1/public/stations/*`, and `/api/v1/public/trains/*` provide read-only public data.
- The public API routes are anonymous; administrative endpoints remain protected.

## Configuration and release notes

`RAILCONNECT_SEARCH_MAX_DAYS` configures the inclusive search horizon. The default is 365 days, and the backend accepts values from 1 to 730. The frontend reads the configured value and applies the same date bounds.

V6 adds PostgreSQL search indexes and is append-only; it does not edit V1–V5. Apply it through the normal Flyway deployment process after reviewing the target database and backup state. No paid infrastructure or cache service is required.

The local Phase 4 verification completed frontend type-check/build and backend unit tests. Database-backed integration, Flyway execution on PostgreSQL, and real-data search remain unverified in this environment because no PostgreSQL URL or Docker daemon was available. See [the Phase 4 testing report](phase-4-testing-report.md) before treating the work as production-ready.
