# Security foundation

## Implemented in Phase 1

- `.env` and secret/key ignore rules; examples use placeholders only.
- No production credentials, paid services, automatic upgrades, or deployment secrets.
- Spring Security stateless defaults; HTTP Basic and form login disabled; protected future API paths require authentication.
- Exact-origin CORS allowlist, no wildcard and no credentialed CORS.
- Security headers: content type options, frame deny, referrer policy, CSP for API docs.
- Uniform JSON 401/403 responses, sanitized API exception responses, no server stack traces in client responses.
- Request ID allowlist/length checks, generated correlation ID, response header and MDC cleanup.
- Structured logs with request ID; no request payload/header logging.
- Public system info intentionally excludes JVM, host, environment secrets, paths, and infrastructure topology.
- Optional Redis health is observed but not part of readiness.
- Compose ports are loopback-only; backend and static web runtime containers are non-root where practical.
- Nginx request body limit and Tomcat form/header limits; future production ingress must enforce a total request-body limit for chunked and fixed-length bodies.
- API filter rejects declared bodies above 1 MiB and counts streamed request bytes to reject oversized chunked bodies; size is configurable with `RAILCONNECT_MAX_REQUEST_BODY_BYTES`.
- Npm/Maven/GitHub Actions Dependabot update configuration and CI vulnerability audit hook.

## Not yet implemented

Phase 1 does not implement user authentication, sessions, MFA, RBAC, production secrets manager, database encryption/backups, rate limiting, booking/payment APIs, abuse protection, or audit domain. These controls are required before real passenger data or live operations.

## Operational guidance

- Keep local test DB credentials separate from hosted environments.
- Use TLS-required database URLs outside local Compose.
- Recheck provider free-tier pause, quota, region, backup, connection, and account billing terms before provisioning.
- Review CORS origins, CSP, auth, proxy body limits, and OpenAPI exposure before public deployment.
- Do not enable a live payment provider or external railway API in this phase.
- No service or account should auto-upgrade; if ₹0 cannot meet availability/recovery needs, stop and request a budget decision instead of weakening controls.
