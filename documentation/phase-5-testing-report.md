# Phase 5 testing report

**Status:** Partial validation; PostgreSQL CI exposed two runtime issues in the fare publication/quote path. Both have local fixes and require a passing post-fix PostgreSQL CI run and deployment.

## Results

| Layer | Result | Evidence |
|---|---|---|
| Frontend lint, typecheck, and production builds | PASS | `npm run check:frontend` completed successfully for admin, operations, and passenger apps. |
| Frontend unit tests | PASS | `npm test` exit code 0; 7 tests passed. |
| Fare calculator unit tests | PASS | Maven test phase includes four new cases for exact rounding, discount/tax ordering, slab boundaries/gaps, and discount capping. |
| Backend clean verification | PASS | `mvn clean verify` exit code 0; 25 unit tests passed and the package was built. |
| PostgreSQL migration and integration | FAIL (CI before latest fix) | Run `42462562915` failed because PostgreSQL could not infer the nullable effective-end-date parameter type. After adding an explicit date cast, run `42463772458` advanced to quote persistence and failed because JDBC could not bind `Instant` for `expires_at`. The insert now uses `java.sql.Timestamp`; another PostgreSQL CI run is required. |
| Browser rendering/E2E | UNVERIFIED | No running backend with PostgreSQL fixtures; build output verifies compilation only. |
| Performance/query plans | UNVERIFIED | No representative PostgreSQL dataset or load test. |

## Added automated coverage

`FareCalculatorTest` checks paise rounding, itemized totals, the inclusive-lower/exclusive-upper slab boundary, uncovered-distance errors, and clamping a configured discount to the current subtotal. Local Maven verification compiled the integration tests, but Failsafe skipped all 12 because no PostgreSQL environment was configured. PostgreSQL CI has applied the migration and exercised rule creation, submission, author/checker separation, approval, and publication after the date-cast fix; it then exposed quote timestamp binding. `DatabaseMigrationIT` and `MasterDataFlowIT` also cover quote persistence and ownership, anonymous quote endpoints, and admin authentication; a passing post-fix run is still required.

## Blocking evidence

`docker info` could not run because the Docker executable is not installed. The Maven test environment has no `RAILCONNECT_DB_URL`; local PostgreSQL integration and runtime quote flow therefore remain unverified. CI run `42462562915` failed on an untyped nullable date; run `42463772458` passed that step and failed to bind the quote expiry `Instant`. The date cast and `Timestamp` binding fixes are pushed/in progress; do not mark the PostgreSQL flow as passing until a new CI run succeeds.

The existing Phase 4 V7 repair and V8 fare migration must pass PostgreSQL CI before deployment. No paid services, official fare data, or production pricing have been activated.
