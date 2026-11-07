# Identity API reference

All responses use the standard JSON API envelope. Browser clients must first call `GET /api/v1/auth/csrf`; unsafe requests include the returned token as `X-XSRF-TOKEN` and send cookies with credentials. Authentication uses the `RCSESSION` cookie. Unless stated otherwise, endpoints are under `/api/v1`.

## Authentication and passenger account

| Method and path | Access | Behavior |
|---|---|---|
| `GET /auth/csrf` | Public | Issue/read CSRF token |
| `POST /auth/register` | Public + CSRF | Register passenger; uniform acknowledgement for new, duplicate, or throttled requests |
| `POST /auth/login` | Public + CSRF | Authenticate email/mobile and password; sets session cookie |
| `POST /auth/logout` | Authenticated + CSRF | Revoke current session |
| `GET /auth/me` | Authenticated | Read current identity |
| `GET /users/me` | Authenticated | Read own profile |
| `PATCH /users/me` | Authenticated + CSRF | Update allowed own profile fields |
| `GET /auth/sessions` | Authenticated | List own active sessions; public UUIDs only |
| `DELETE /auth/sessions/{sessionId}` | Authenticated + CSRF | Revoke own session by public UUID |
| `POST /auth/password/change` | Authenticated + CSRF | Change password; reauthentication is required afterwards |
| `POST /auth/password/forgot` | Public + CSRF | Generic acknowledgement; no reset link/message is issued |

## MFA placeholders

| Method and path | Access | Current result |
|---|---|---|
| `POST /auth/mfa/enroll` | Authenticated + CSRF | `501 MFA_NOT_AVAILABLE` |
| `POST /auth/mfa/verify` | Authenticated + CSRF | `501 MFA_NOT_AVAILABLE` |
| `POST /auth/mfa/disable` | Authenticated + CSRF | `501 MFA_NOT_AVAILABLE` |
| `POST /auth/mfa/recovery` | Authenticated + CSRF | `501 MFA_NOT_AVAILABLE` |

## Administrator identity

All paths below start with `/admin`. The named permission is enforced by the API; sensitive mutations also require recent authentication.

| Method and path | Required permission |
|---|---|
| `GET /users`, `GET /users/{userId}` | `identity:users:read` |
| `POST /users` | `identity:users:create` |
| `PATCH /users/{userId}/status` | `identity:users:status` |
| `PUT /users/{userId}/roles` | `identity:users:roles` |
| `DELETE /users/{userId}/sessions/{sessionId}` | `identity:users:status` |
| `GET /users/{userId}/authentication-events` | `identity:users:read` |
| `GET /users/{userId}/sessions` | `identity:users:read` |
| `GET /roles`, `GET /permissions` | `identity:roles:read` |
| `GET /security/audit-logs` | `security:audit:read` |

Collection endpoints support pagination with a maximum page size of 100. Request and response DTOs in the backend remain the source of truth for field-level validation. This reference does not imply that future railway-domain APIs are implemented.
