# Phase 5 testing report

**Status:** Partial validation; PostgreSQL-backed behavior and browser/API integration are not yet verified.

## Results

| Layer | Result | Evidence |
|---|---|---|
| Frontend lint, typecheck, and production builds | PASS | `npm run check:frontend` completed successfully for admin, operations, and passenger apps. |
| Frontend unit tests | PASS | `npm test` exit code 0; 7 tests passed. |
| Fare calculator unit tests | PASS | Maven test phase includes four new cases for exact rounding, discount/tax ordering, slab boundaries/gaps, and discount capping. |
| Backend clean verification | PASS | `mvn clean verify` exit code 0; 25 unit tests passed and the package was built. |
| PostgreSQL migration and integration | UNVERIFIED | Docker CLI is unavailable and `RAILCONNECT_DB_URL` is unset. `DatabaseMigrationIT` and the expanded `MasterDataFlowIT` were not run against PostgreSQL. |
| Browser rendering/E2E | UNVERIFIED | No running backend with PostgreSQL fixtures; build output verifies compilation only. |
| Performance/query plans | UNVERIFIED | No representative PostgreSQL dataset or load test. |

## Added automated coverage

`FareCalculatorTest` checks paise rounding, itemized totals, the inclusive-lower/exclusive-upper slab boundary, uncovered-distance errors, and clamping a configured discount to the current subtotal. The clean Maven verification compiled the integration tests, but Failsafe skipped all 12 integration cases because no PostgreSQL environment was configured. The `DatabaseMigrationIT` and `MasterDataFlowIT` assertions cover the fare schema, no-rule error, author/checker separation, rule publication, quote persistence and ownership, anonymous quote endpoints, and admin authentication; they remain unexecuted against PostgreSQL.

## Blocking evidence

`docker info` could not run because the Docker executable is not installed. The Maven test environment has no `RAILCONNECT_DB_URL`; PostgreSQL-specific migrations, database triggers, Flyway application, and runtime quote flow therefore remain unverified. Do not interpret skipped integration tests as passing.

The existing Phase 4 V7 repair and V8 fare migration must pass PostgreSQL CI before deployment. No paid services, official fare data, or production pricing have been activated.
