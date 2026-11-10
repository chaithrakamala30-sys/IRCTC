# Payment testing

## Implemented checks

Backend unit tests cover exact INR/paise conversion, payment state transitions, disabled-provider behavior, startup rejection for missing credentials, untrusted origin rejection, and mocked HTTP requests for the documented intent-create, status, and verify responses. The HTTP tests run against an ephemeral local server and do not call Dhruva Bank.

The current full unit suite contains 45 tests. PostgreSQL migration/integration tests are conditional on `RAILCONNECT_DB_URL`; without it, V12/V13 have not been executed against PostgreSQL. Frontend typecheck/build should be run for both passenger and admin workspaces.

## Remaining verification

No live or sandbox bank call was made. Webhook delivery/signature/replay behavior, refunds, settlement, automated reconciliation, transaction-ledger lookup, and end-to-end hosted checkout remain unverified or unimplemented. Use contract fixtures for provider errors, mismatched amounts/references, terminal failures, retries, provider timeouts, concurrent idempotent requests, and late success before claiming production readiness.
