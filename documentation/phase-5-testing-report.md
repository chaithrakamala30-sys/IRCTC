# Phase 5 testing report

**Status:** Backend and PostgreSQL CI pass. Browser rendering/E2E and performance/query-plan validation remain unverified.

## Results

| Layer | Result | Evidence |
|---|---|---|
| Frontend lint, typecheck, and production builds | PASS | `npm run check:frontend` completed successfully for admin, operations, and passenger apps. |
| Frontend unit tests | PASS | `npm test` exit code 0; 7 tests passed. |
| Fare calculator unit tests | PASS | Maven test phase includes four new cases for exact rounding, discount/tax ordering, slab boundaries/gaps, and discount capping. |
| Backend clean verification | PASS | `mvn clean verify` exit code 0; 25 unit tests passed and the package was built. |
| PostgreSQL migration and integration | PASS | GitHub Actions run [42464102473](https://github.com/chaithrakamala30-sys/IRCTC/actions/runs/42464102473) passed against PostgreSQL 16: all 12 integration tests passed with no failures or skips. This includes Flyway migration, rule lifecycle, quote persistence, and ownership checks. |
| Browser rendering/E2E | UNVERIFIED | Node.js 24.18.1 and Playwright CLI 1.65.0 are available, but `npx playwright install chromium` failed with `read ECONNRESET` while downloading Chromium. No local PostgreSQL-backed app/fixtures are available for the flow. |
| Performance/query plans | UNVERIFIED | No representative PostgreSQL dataset or load test. |

## Added automated coverage

`FareCalculatorTest` checks paise rounding, itemized totals, the inclusive-lower/exclusive-upper slab boundary, uncovered-distance errors, discount/tax ordering, and discount capping. GitHub Actions run [42464102473](https://github.com/chaithrakamala30-sys/IRCTC/actions/runs/42464102473) ran against PostgreSQL 16 and passed all 12 integration tests, including migration, rule creation, maker-checker approval/publication, quote persistence/ownership, and API authorization checks.

## Blocking evidence

Docker is unavailable locally and `RAILCONNECT_DB_URL` is unset, so the local runtime and browser E2E could not run. Chromium setup was attempted independently and failed with `read ECONNRESET` at about 90% of the download; it retried and was stopped after the same transfer failure. The two CI-discovered PostgreSQL issues (nullable date inference and JDBC `Instant` binding) were fixed in commits `535f2ca` and `ef18f60`; the latest PostgreSQL CI run passed. No representative load test or query-plan analysis has run.

The Phase 4 V7 repair and V8 fare migration passed in PostgreSQL CI. No paid services, official fare data, or production pricing have been activated.

## Phase 5 checklist gaps

- Coach-class-specific scopes are supported, but the integration flow does not compare quotes for two differently priced classes.
- The simulator and passenger quote show labeled component amounts and totals, but do not expose formula-level explanations or running subtotals for every calculation step.
- PostgreSQL CI verifies quote persistence and ownership; it does not yet assert that direct quote updates/deletes are rejected or that a quote transitions to `EXPIRED`.
- Mobile and laptop routes compile and have responsive styles, but browser rendering was not exercised because Chromium download failed and no local PostgreSQL-backed app was available.
