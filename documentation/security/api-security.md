# API Security & Request Hardening

## 1. Input Sanitization & Parameter Validation
- Strict Jackson DTO parsing with unmapped property rejections.
- Parameterized SQL execution via Spring `JdbcClient` eliminates SQL injection.
- Pagination limits (`LIMIT 50`) on all list endpoints protect backend databases from resource exhaustion.

## 2. API Response Security
- Generic error responses: Internal stack traces, raw SQL exceptions, and class paths are suppressed using `SecurityErrorHandlers` and standard API envelope wrappers (`ApiEnvelope`).
- Rate limiting and throttle tables prevent brute-force attacks against `/api/v1/auth/login`.
- CORS configured explicitly to allow only approved origin domains.
