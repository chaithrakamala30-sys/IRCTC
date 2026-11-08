# Phase 5 testing report

**Status:** Partial validation; publication initially failed in PostgreSQL CI due to an untyped nullable date parameter. The query now explicitly casts the nullable end date to `date`; the fix still requires PostgreSQL CI verification and deployment.

## Results

| Layer | Result | Evidence |
|---|---|---|
| Frontend lint, typecheck, and production builds | PASS | `npm run check:frontend` completed successfully for admin, operations, and passenger apps. |
| Frontend unit tests | PASS | `npm test` exit code 0; 7 tests passed. |
| Fare calculator unit tests | PASS | Maven test phase includes four new cases for exact rounding, discount/tax ordering, slab boundaries/gaps, and discount capping. |
| Backend clean verification | PASS | `mvn clean verify` exit code 0; 25 unit tests passed and the package was built. |
| PostgreSQL migration and integration | FAIL (CI before fix) | GitHub Actions run `42462562915` applied migrations and reached `MasterDataFlowIT`, then failed publishing a rule: PostgreSQL could not infer the type of nullable parameter `$7` in the effective-range overlap query. The query now casts the nullable end date; a passing post-fix PostgreSQL CI run is still required. |
| Browser rendering/E2E | UNVERIFIED | No running backend with PostgreSQL fixtures; build output verifies compilation only. |
| Performance/query plans | UNVERIFIED | No representative PostgreSQL dataset or load test. |

## Added automated coverage

`FareCalculatorTest` checks paise rounding, itemized totals, the inclusive-lower/exclusive-upper slab boundary, uncovered-distance errors, and clamping a configured discount to the current subtotal. Local Maven verification compiled the integration tests, but Failsafe skipped all 12 because no PostgreSQL environment was configured. PostgreSQL CI did execute the migration and integration flow, proving the schema starts but exposing the nullable-date publication failure. `DatabaseMigrationIT` and `MasterDataFlowIT` cover the fare schema, no-rule error, author/checker separation, rule publication, quote persistence and ownership, anonymous quote endpoints, and admin authentication; post-fix CI must pass to verify them.

## Blocking evidence

`docker info` could not run because the Docker executable is not installed. The Maven test environment has no `RAILCONNECT_DB_URL`; local PostgreSQL integration and runtime quote flow therefore remain unverified. CI run `42462562915` used PostgreSQL 16 and failed at fare-rule publication with `could not determine data type of parameter $7`. A typed cast was applied locally; do not mark the PostgreSQL flow as passing until a post-fix CI run succeeds.

The existing Phase 4 V7 repair and V8 fare migration must pass PostgreSQL CI before deployment. No paid services, official fare data, or production pricing have been activated.
