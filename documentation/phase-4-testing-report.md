# Phase 4 testing report

**Generated:** 2026-11-08
**Scope:** Passenger search frontend and Spring Boot search API changes
**Environment:** Windows; Node.js 24.18.1; Java 17.0.11; Maven 3.9.15

## Summary

| Check | Result | Evidence |
|---|---|---|
| Passenger TypeScript check | PASS | `npm run typecheck --workspace @railconnect/passenger-web`, exit code 0 |
| Passenger production build | PASS | `npm run build:passenger`, exit code 0; Next.js generated mobile, laptop, and train-detail routes |
| Backend compilation and unit tests | PASS | Maven `test`, exit code 0; 21 tests, 0 failures, 0 errors, 0 skips |
| Backend package and integration-test phase | PASS WITH SKIPS | Maven `verify`, exit code 0; package built; 12 integration tests were skipped because the PostgreSQL JDBC URL was not configured |
| Journey-time unit coverage | PASS | 3 tests cover overnight offsets, DST overlap, and DST gap behavior |
| Browser rendering smoke check | PARTIAL | `/mobile` at 390 px and `/laptop` at 1440 px rendered; measured page width equaled viewport width for both. No end-to-end search with persisted results was possible. |
| PostgreSQL integration and V6 migration | UNVERIFIED | `RAILCONNECT_DB_URL` was not configured and Docker was unavailable. The DB-backed `MasterDataFlowIT` therefore was not executed. |
| Real-data search, query plans, and load | UNVERIFIED | No PostgreSQL test database or representative railway data was available; no benchmark or `EXPLAIN ANALYZE` result is claimed. |

## Test evidence and limits

The backend commands were run using JDK 17 and Maven 3.9.15:

```powershell
mvn --batch-mode --no-transfer-progress -f backend\railconnect-core\pom.xml test
mvn --batch-mode --no-transfer-progress -f backend\railconnect-core\pom.xml verify
```

The runs compiled 60 production source files and 12 test source files. The unit-test phase executed 21 tests. The `verify` phase packaged the backend and discovered 12 integration tests, all skipped because `RAILCONNECT_DB_URL` was not configured; therefore integration is not a pass.

The browser smoke check confirmed that each route rendered its device-specific navigation and that neither viewport had horizontal document overflow at the tested width. The API was not running, so the browser showed an API response error instead of real suggestions or results. This does not verify backend behavior, results, filters, or detail navigation.

## Remaining verification

Before production use:

1. Apply V6 to an isolated PostgreSQL database through Flyway and execute the PostgreSQL-backed integration flow.
2. Verify public API authorization, actual autocomplete/search/details, exceptions, effective versions, filters, pagination, and route stop times against synthetic PostgreSQL fixtures.
3. Review query plans and benchmark representative data; do not infer capacity from this report.
4. Exercise keyboard and touch search flows and route details in real mobile and laptop browser viewports with the API available.

No paid infrastructure, external railway provider, or production database was used or activated by these checks.
