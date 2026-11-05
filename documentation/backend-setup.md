# Backend setup

## Module

`backend/railconnect-core` is the single Spring Boot modular-monolith foundation, built for Java 17. Dependencies establish web/API, stateless Spring Security defaults, Bean Validation, Spring Data JPA, PostgreSQL, Flyway, optional Redis, Actuator, Prometheus metrics, OpenAPI, and Spring tests.

No reservation/domain behavior is implemented. Application startup requires PostgreSQL credentials and a reachable PostgreSQL database; it fails explicitly rather than switching to an in-memory fallback.

## Run

Start local PostgreSQL, set the three required `RAILCONNECT_DB_*` variables, then run `mvn spring-boot:run`. See [local development](local-development-guide.md).

## API endpoints

- `GET /api/v1/health`
- `GET /api/v1/health/readiness`
- `GET /api/v1/health/liveness`
- `GET /api/v1/system/info`
- `/swagger-ui.html` and `/v3/api-docs`

Liveness does not depend on PostgreSQL. Readiness includes PostgreSQL and returns HTTP 503 when unavailable. Health details and internal runtime information are not exposed by the public API.

## Error contract

Client errors use:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      { "field": "name", "message": "must not be blank" }
    ]
  },
  "timestamp": "2026-11-05T00:00:00Z",
  "requestId": "correlation-id"
}
```

The request ID is accepted only when it matches a bounded safe-character pattern; otherwise a UUID is generated. It is returned in `X-Request-Id` and included in structured logs. Unexpected server errors are logged with the request ID and return a sanitized 500 response.

Request bodies are limited to 1 MiB by default, including streamed/chunked bodies. Configure the limit with `RAILCONNECT_MAX_REQUEST_BODY_BYTES`; over-limit requests return HTTP 413.

## Security foundations

The API is stateless with no default form/basic login. Health, minimal system info, and OpenAPI are public; other routes require authentication. Authentication flows are not yet implemented. CORS uses exact configured origins, no credentialed cross-origin cookies. Security headers and uniform JSON 401/403 responses are configured.

## OpenAPI

Local Swagger UI is available at `/swagger-ui.html`. Public exposure can be disabled with `RAILCONNECT_OPENAPI_ENABLED=false`. The API contains only Phase 1 endpoints; later endpoint schemas must be reviewed and versioned.

## Redis

Redis is disabled by default and optional. TLS, username/password, connect timeout, and command timeout are configurable for Upstash or compatible Redis. The optional health indicator reports connection state but is excluded from readiness. No Phase 1 critical behavior depends on it.

## Metrics and logs

Actuator provides liveness/readiness/health/info and Prometheus metric foundations. Structured console logging includes correlation ID; request/response bodies and authorization headers are not logged. Public endpoints do not expose JVM/host details.
