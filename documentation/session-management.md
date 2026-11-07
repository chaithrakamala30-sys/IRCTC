# Session management

## Storage and cookie

Spring Session JDBC persists server-side sessions in `SPRING_SESSION` and `SPRING_SESSION_ATTRIBUTES`, created in the configured schema by Flyway V2. The browser receives the `RCSESSION` cookie with `HttpOnly`, `SameSite=Lax`, and a configurable `Secure` attribute. The cookie contains a session credential; never expose or log it.

The separate `XSRF-TOKEN` cookie is readable by browser code and must accompany unsafe methods in the `X-XSRF-TOKEN` header. CSRF is not disabled for authentication or logout.

## Lifetime and rotation

- Idle timeout: 30 minutes.
- Absolute authenticated-session lifetime: 8 hours by default (`RAILCONNECT_ABSOLUTE_SESSION_TIMEOUT`).
- Login rotates the session identifier to mitigate session fixation.
- The backend records a random public UUID for session-management UI; this is distinct from the internal session credential.
- Each authenticated request checks the server-side user-session row. Missing or revoked records invalidate the session and return `401 SESSION_REVOKED`.

Session revocation removes the Spring Session record and marks the application session revoked. Password changes, account suspension/disablement, and role changes revoke affected sessions. Users can inspect and revoke their own sessions; authorized administrators can revoke another user's session.

## Configuration and deployment caution

`RAILCONNECT_SESSION_COOKIE_SECURE=false` is a local HTTP development default only. Set it to `true` behind HTTPS before any deployment. The API's exact CORS origins must match the browser origins. `RAILCONNECT_AUTH_THROTTLE_KEY` must be a stable, secret, high-entropy value in any multi-instance or persistent environment; its development fallback is random per process and does not preserve throttle hashes across restarts.

The read-only Supabase check found Flyway V2 recorded as successful in the `railconnect` schema. That confirms the database migration history, but does not validate application startup or Spring Session runtime behavior. Do not rerun or reapply V2; validate the application flow against a disposable local PostgreSQL instance before relying on sessions.
