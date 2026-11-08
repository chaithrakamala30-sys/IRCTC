# Phase 2 testing and acceptance report

**Status:** partial acceptance. Production health, proxy, validation, and browser smoke checks pass; successful live signup/sign-in and authenticated workflows remain unverified.
**Validation date:** 2026-11-08.

## Completed checks

| Check | Result | Evidence |
|---|---|---|
| Backend Java unit tests and production artifact | Pass (local) | Clean Maven `verify`: 12 unit tests passed; backend JAR packaged. All 9 PostgreSQL-dependent integration tests were skipped because no local database URL was configured |
| Frontend lint, strict type checks, production builds | Pass | `npm run check:frontend`; admin, operations, passenger |
| Frontend unit tests | Pass | `npm test`: 7 tests, 0 failures |
| Vercel → Render API proxy | Pass | Vercel `/api/v1/health/readiness` and `/api/v1/auth/csrf` returned HTTP 200 |
| Render readiness | Pass | `/actuator/health/readiness` returned `UP` |
| Registration input/API validation | Pass (non-mutating) | Mobile number accepted on mobile (390 px) and laptop (1440 px); test registration POST intercepted; empty payload returned HTTP 400 / `VALIDATION_ERROR` |
| Admin API unauthenticated access | Pass | `GET /api/v1/admin/users` returned HTTP 401 |
| Existing Supabase user with stale profile mapping | Implemented; runtime integration pending | Confirmed Supabase login now repairs a uniquely matching active passenger-only profile whose auth UUID is missing or stale, preserving its profile ID and role grants. Regression coverage is added but needs a PostgreSQL-backed CI run |
| Session revocation enforcement | Implemented; runtime integration pending | Filter checks server-side session state; automated DB-backed assertion is present but not run against hosted auth config |
| Password storage | Implementation verified by code inspection | Supabase Auth is the credential owner; RailConnect profile table does not store raw passwords or its own password hashes |

## Pending checks

| Check | Status / reason |
|---|---|
| Successful registration and login | Not run against production | Signup was not submitted: the registration flow requires explicit passenger acceptance of the terms and privacy notice, which cannot be accepted on the user's behalf. The form was cleared; no Auth user/profile was created and no confirmation email was sent |
| Supabase Auth backend configuration | Not independently verified | Auth secrets are configured in Render, not present in the local `.env`; Render health does not prove Auth signup/login credentials work |
| Passenger-to-admin authorization with a signed-in passenger | Code/test path exists; live check pending | Only unauthenticated `401` was checked in production; passenger `403` needs a test account |
| Authenticated role grants, admin staff provisioning, and audit-log persistence | Not run end-to-end | Requires a test Super Administrator and Supabase service-role access |
| Password recovery | Safely unavailable, documented | Login page states no approved email/SMS delivery exists and no reset message will be sent |
| MFA | Unavailable, documented | MFA API returns an explicit not-implemented response; UI discloses the limitation |
| Flyway history and RBAC seed | Pass, read-only | Supabase `railconnect` records V1–V4 as successful; identity/audit/session tables are present and 7 seeded roles were counted. This task did not apply migrations |
| Docker Compose | Fails in latest CI run; local Docker unavailable | Compose configuration validation passed, but the backend image build failed because Dockerfile `COPY` paths did not match its module build context. Paths were corrected locally; a CI rerun is still required |
| GitHub Actions | Not all passing | [Frontend checks #13 passed](https://github.com/chaithrakamala30-sys/IRCTC/actions/runs/42432058264). [Backend checks #11 failed](https://github.com/chaithrakamala30-sys/IRCTC/actions/runs/42440125963): `IdentityFlowIT` had one failure and one error because its assertions used the encoded `RCSESSION` cookie value as the raw database session ID. Tests now look up the internal ID; rerun pending. [Infrastructure checks #6 failed](https://github.com/chaithrakamala30-sys/IRCTC/actions/runs/42432058265) on the Dockerfile context mismatch; corrected locally, rerun pending |
| No paid services | No new paid service or infrastructure was added by this verification | Provider account billing/usage was not inspected |

## Integration test coverage prepared

`IdentityFlowIT` is enabled only when `RAILCONNECT_DB_URL` points to PostgreSQL. It covers CSRF-protected registration, generic duplicate acknowledgement, case-normalized login, linking a stale passenger profile mapping after confirmed Auth login, cookie-backed identity, passenger denial on admin routes, opaque public session IDs, session revocation, rejection of a revoked cookie, and sign-out followed by two successful sign-ins. Local clean Maven `verify` passed compilation, all 12 unit tests, and packaging; its 9 PostgreSQL-dependent integration tests were skipped because no local database URL was configured. The latest CI run exposed two assertions treating the encoded browser cookie as the internal database session ID; those assertions now resolve the internal ID from PostgreSQL and await a PostgreSQL-backed CI rerun.

## Next acceptance gate

For full sign-off, have the passenger complete registration and email confirmation (including their own terms/privacy acknowledgement), then run sign-in → profile → session listing/revocation → passenger-admin denial using an authorized test session. Verify admin role assignment and audit persistence with an approved test Super Administrator. The live test account, if created, must be removed by an authorized operator in Supabase Dashboard; no account-deletion route or local service-role access is configured. Do not rerun or reapply V1–V4; they are already recorded as successful on Supabase. Recovery and MFA remain documented limitations, not implemented features. Do not begin Phase 3 until authenticated flows and the remaining policy gate are explicitly accepted.
