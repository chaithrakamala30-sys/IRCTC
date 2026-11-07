# Phase 2 — Identity and access control

## Status and boundary

Phase 2 implements passenger identity, browser sessions, initial role-based access control (RBAC), staff account management, and security audit records. Phase 2 does **not** make the railway booking product operational. There is no train inventory, search, reservation, payment, notification delivery, or railway-system integration.

The backend includes a database-backed integration test, but it has not been run. Docker is unavailable in the current environment, V2 has not been applied to PostgreSQL, and no Supabase migration was attempted. Unit and frontend validation results are recorded in the [testing report](phase-2-testing-report.md). Do not begin Phase 3 until the outstanding validation and policy decisions are closed.

## User experiences

The passenger frontend exposes two separate page sets:

- Laptop experience: `/laptop`, `/laptop/login`, `/laptop/register`, `/laptop/account`.
- Mobile experience: `/mobile`, `/mobile/login`, `/mobile/register`, `/mobile/account`.

The root page selects a device experience by viewport width, and users can switch using the visible navigation links. Both experiences use the same backend identity and session APIs; they are not separate accounts or security boundaries. The admin console is a separate application and is protected by backend authorization, not by hiding frontend routes.

## Implemented capabilities

- Passenger registration with normalized email/mobile values, password policy checks, consent-version storage, duplicate-response uniformity, and registration throttling.
- Email-or-mobile login with generic credential errors and identifier-based throttling.
- Cookie-backed persistent sessions, CSRF protection, session rotation on login, idle and absolute timeouts, current-session listing, and revocation.
- Passenger profile name updates and password changes; email/mobile ownership verification is not implemented.
- Seeded passenger, super-admin, railway-admin, station-manager, reservation-officer, finance-admin, and customer-support roles, with permissions and optional resource scopes in the schema.
- Super-Administrator account provisioning, status and role changes, session revocation, authentication-event review, and audit-log review.
- One-time Super Administrator bootstrap guarded by a command-line property.

## Explicitly unavailable

- Email or SMS verification/delivery.
- Self-service password reset or recovery delivery.
- TOTP/MFA enrollment, verification, disablement, or recovery-code use. The API returns an explicit not-implemented response; the schema tables are groundwork only.
- Railway-specific permission checks and scope enforcement for future domain APIs that do not yet exist.
- Production deployment, externally hosted services, and any paid infrastructure.

See the [authentication architecture](authentication-architecture.md), [RBAC matrix](rbac-matrix.md), and [API reference](identity-api-reference.md) for implementation details.
