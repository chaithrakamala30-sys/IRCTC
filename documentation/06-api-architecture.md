# REST API architecture

## Conventions

- Base path `/api/v1`; JSON over HTTPS. Publish OpenAPI 3.x as a build artifact and review breaking changes before changing a version.
- Use opaque UUIDs for resource IDs and a cryptographically random PNR/reference separate from internal keys.
- Authenticate browser requests with secure, HttpOnly, SameSite cookies and CSRF protection if cookie-based sessions are selected. Do not put long-lived bearer tokens in local storage.
- Authorize every resource and operation server-side. Admin scope includes station/region where applicable.
- Booking, cancellation, payment initiation, refunds, and admin mutations require idempotency keys; read requests generally do not.
- Use cursor pagination, bounded page size, explicit sort allowlists, request-size limits, timeouts, and per-identity/IP rate limits. Rate-limit values are configurable and must be load-tested.
- All timestamps are RFC 3339 UTC; service/journey dates are ISO date interpreted in station/project timezone. Amounts are integer minor units with currency code.
- Use opaque error `traceId`; never include SQL, stack traces, secrets, or personal data in error details.

## Standard error shape

```json
{
  "type": "https://railconnect.example/problems/inventory-conflict",
  "title": "Requested inventory is no longer available",
  "status": 409,
  "detail": "Refresh availability and submit a new request.",
  "instance": "/api/v1/bookings",
  "code": "INVENTORY_CONFLICT",
  "traceId": "opaque-correlation-id"
}
```

Use `application/problem+json` (RFC 9457 style). Stable codes include `VALIDATION_ERROR` (400), `UNAUTHENTICATED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `IDEMPOTENCY_CONFLICT` (409), `INVENTORY_CONFLICT` (409), `RULE_VERSION_UNAVAILABLE` (422), `RATE_LIMITED` (429, `Retry-After`), `DEPENDENCY_UNAVAILABLE` (503), and `INTERNAL_ERROR` (500). Do not report a timeout as a successful booking.

## Endpoint catalogue

`P` = authenticated passenger; `A` = role/scope-authorized staff; `Webhook` = provider signature verification plus replay protection; `Public` = unauthenticated with strict abuse limits.

| Method / path | Purpose and access | Important contract / validation | Idempotency / limits |
|---|---|---|---|
| `POST /auth/register` | Create account; Public | Email/phone verification flow; normalized unique identifier; no account-enumeration response | Account creation key; strict IP/identifier throttle |
| `POST /auth/login` | Start session; Public | Credential + MFA challenge; generic failure response | Login throttling, bot/credential-stuffing controls |
| `POST /auth/logout` | Revoke current session; P | CSRF protection for cookie session | Idempotent; per account |
| `POST /auth/refresh` | Rotate session/refresh token; P | Detect reuse and revoke token family | Single-use rotation; strict |
| `POST /auth/mfa/challenge`, `POST /auth/mfa/verify` | MFA lifecycle; P/A | Step-up for privileged actions; recovery codes hashed | One-time challenges; strict |
| `GET /me`, `PATCH /me` | Read/update profile; P | Field allowlist, optimistic version, privacy controls | Small per-user limit |
| `GET /stations?query=&cursor=` | Search approved stations; Public | Return code, display names, timezone, status | Cacheable; bounded query/page |
| `GET /trains/search?from=&to=&date=&class=` | Search approved schedule; Public | Stop order, run date, class, time zone, freshness | Abuse limit; bounded horizon/results |
| `GET /trains/{trainId}/runs/{runId}` | Train/run detail; Public | Published route and run status | Cacheable with version/ETag |
| `GET /trains/{trainId}/schedule?date=` | View effective schedule; Public | Approved schedule version, operating days, effective range | Bounded date horizon |
| `GET /runs/{runId}/availability?fromStop=&toStop=&class=&quota=` | Segment availability; Public | Availability count/status and `asOf`; no guarantee until hold commits | Strong per-IP/user/run throttle; never cache as authoritative |
| `GET /runs/{runId}/inventory?fromStop=&toStop=&class=` | Scoped inventory summary; A | No passenger identity; never exposes unrestricted seat allocation details | Staff scope and audit; strict |
| `POST /fare-quotes` | Calculate fare; Public/P | `{runId, fromStopId, toStopId, classId, quotaId, passengerCount}`; response includes amount, currency, ruleVersion, expiresAt | Request throttle; quote is immutable |
| `POST /bookings` | Create hold/reservation/payment intent; P | `{quoteId, passengers:[...]}`; server revalidates quote, permissions, policy, and inventory | Required `Idempotency-Key`; strict account/run limit |
| `GET /bookings/{id}` | View own reservation; P | Ownership enforced; redact unnecessary passenger PII | Per account |
| `GET /bookings/{id}/ticket` | Retrieve issued ticket; P | Only for eligible state; short-lived download link if needed | Per account; no public PNR enumeration |
| `GET /pnr/{pnr}` | PNR status; Public or verified access per approval | Return minimal status; resistant to enumeration | High entropy PNR + challenge/owner verification; strict |
| `POST /bookings/{id}/cancellation-requests` | Cancel eligible booking; P | Reason, segment/passenger scope, rule version, expected version | Required idempotency; per reservation |
| `GET /refunds/{id}` | Refund status; P or Finance A | Owner/finance scope check; provider status/freshness | Per identity |
| `GET /me/notifications` | List passenger notifications; P | Cursor, status, minimized content; only recipient's records | Per account |
| `PATCH /me/notification-preferences` | Update notification channels/preferences; P | Allowlisted channels and consent state; no effect on legally required notices | Idempotent; per account |
| `POST /support/cases` | Create support case; P | Category, booking reference, minimized details; safe text handling | Idempotency; per-account abuse limit |
| `GET /support/cases/{id}` | Read own support case; P | Ownership enforced; no internal staff notes | Per account |
| `POST /admin/support/cases/{id}/actions` | Record scoped support action; Support A | Reason code, action allowlist; cannot directly mutate reservation/payment ledger | Idempotency; staff audit |
| `POST /payments/{id}/confirm` | Client return/status refresh; P | Never trust client assertion; query provider or return pending | Idempotency; strict |
| `POST /webhooks/payments/{provider}` | Provider callback; Webhook | Verify signature/timestamp/body; unique event ID; persist before acknowledge | Provider-specific; replay and body-size limits |
| `GET /admin/stations`, `POST /admin/stations`, `PATCH /admin/stations/{id}` | Station CRUD; A | Versioned changes, approval state, provenance | Idempotency for writes; admin audit |
| `POST /admin/trains`, `POST /admin/routes`, `POST /admin/schedules` | Manage catalogue; A | Referential and effective-date validation; preview before publish | Approval gate for publish; audit |
| `POST /admin/runs/{id}/inventory/imports` | Load run inventory; A | Schema validation, duplicate detection, dry run, atomic publish | Idempotency + import checksum; bounded file |
| `POST /admin/rules/{domain}/versions` | Create rule version; A | No mutation of published version; effective range and approver | Approval and audit |
| `POST /admin/runs/{id}/disruptions` | Record operational disruption; A | Reason, scope, effective time, impacted inventory/bookings | Idempotent; mandatory audit |
| `GET /admin/reservations`, `GET /admin/payments/reconciliation` | Operational/finance views; scoped A | Minimized PII; cursor, filtering allowlist, freshness | Per role; export limits |
| `POST /admin/refunds/{id}/approve` | Approve refund; Finance A | Separation of duties, amount ceiling, policy evaluation | Idempotency + step-up auth |
| `GET /admin/audit-events` | Audit search; A | Scope filters, immutable results, access itself audited | Strict page/export limits |
| `GET /admin/reports/{report}` | Reporting; A | Aggregated/pseudonymized data; freshness and timezone | Per role; bounded date range |
| `GET /health/live`, `GET /health/ready` | Platform health; Public/private by deployment | Liveness minimal; readiness checks critical dependencies; no secret/config disclosure | Exclude from broad health details |

Exact field schemas and domain errors belong in the OpenAPI contract. The schemas above are illustrative and must be refined with validation rules, PII classification, and policy owners before implementation.

## Booking request example

```http
POST /api/v1/bookings
Idempotency-Key: 6af44b50-...
Content-Type: application/json

{"quoteId":"opaque-quote-id","passengers":[{"name":"...","ageBand":"...","berthPreference":"..."}]}
```

The API response distinguishes `HELD` / `PAYMENT_PENDING`, confirmed, RAC, waitlisted, expired, and rejected outcomes. It returns no final ticket/PNR until the relevant state transition commits. The server stores a canonical payload hash with the key; identical retries return the original resource, mismatched key reuse returns 409.

## API evolution and operational contract

Add backward-compatible optional response fields; do not silently change enum meaning. Deprecate with telemetry and a documented sunset; introduce `/v2` for incompatible contract changes. Use OpenAPI-generated clients only after schema review. Track request IDs across app/log/outbox/provider event while excluding PII. Define `Retry-After` for throttles and typed retryability in internal errors.
