# Environment configuration

## Local file and secret rules

Copy `.env.example` to `.env` and set the blank local-only database username/password before starting Compose. `.env` is ignored by Git. Never put real passwords, Supabase URLs with credentials, tokens, private keys, payment credentials, or production endpoints in source, frontend variables, CI logs, or screenshots.

Only `.env.example` is committed; it contains placeholder values and non-secret local defaults. `NEXT_PUBLIC_*` values are compiled into static frontend files and are public by design.

## Variables

| Variable | Required | Default / notes |
|---|---:|---|
| `RAILCONNECT_DB_URL` | Yes for direct backend | Local JDBC URL; use hosted PostgreSQL JDBC URL for Supabase. |
| `RAILCONNECT_DB_USERNAME` | Yes | Supply via local environment/secret manager. |
| `RAILCONNECT_DB_PASSWORD` | Yes | Supply via local environment/secret manager. |
| `RAILCONNECT_DB_SCHEMA` | No | Application schema for datasource, Hibernate, and Flyway; defaults to the isolated `railconnect` schema. |
| `RAILCONNECT_COMPOSE_DB_URL` | Compose | Container-network URL, normally `jdbc:postgresql://db:5432/railconnect`; use provider URL only when deliberately targeting a remote DB. |
| `RAILCONNECT_DB_NAME` | Compose | Local PostgreSQL database name; default placeholder value is `railconnect`. |
| `RAILCONNECT_DB_POOL_SIZE` | No | Default 5; lower for constrained providers and scale carefully. |
| `RAILCONNECT_MAX_REQUEST_BODY_BYTES` | No | API request-body limit; default 1 MiB. |
| `RAILCONNECT_CORS_ALLOWED_ORIGINS` | No | Exact localhost app origins; comma-separated, no wildcard. Replace with exact site origins per environment. |
| `PORT` | No | Backend listener; default 8080. |
| `RAILCONNECT_OPENAPI_ENABLED` | No | Defaults true for local development; set false where public docs are not desired. |
| `RAILCONNECT_REDIS_ENABLED` | No | Defaults false; enable only if a Redis service is intentionally configured. |
| `RAILCONNECT_REDIS_HOST` / `RAILCONNECT_REDIS_PORT` | Redis only | Service endpoint and port; defaults target local Redis. |
| `RAILCONNECT_REDIS_USERNAME` / `RAILCONNECT_REDIS_PASSWORD` | Redis only | Provide securely; never commit live values. |
| `RAILCONNECT_REDIS_SSL` | Redis only | Set true for TLS endpoints such as Upstash. |
| `RAILCONNECT_REDIS_TIMEOUT_MS` / `RAILCONNECT_REDIS_CONNECT_TIMEOUT_MS` | No | Bounded command/connect timeouts. |
| `NEXT_PUBLIC_API_BASE_URL` | When frontend calls API | Public build-time URL, no secret; default local API URL in example. |
| `POSTGRES_PORT` | Compose | Loopback host port, default 54329. |

## Supabase switch

Set `RAILCONNECT_DB_URL`, username, and password in the chosen environment. For Compose, set `RAILCONNECT_COMPOSE_DB_URL` to the same TLS-required JDBC endpoint, because the application runs in a container. The application defaults to a dedicated `railconnect` schema rather than the shared `public` schema; Flyway creates it if needed. Do not embed credentials in URLs when separately provided username/password can be used. Verify the selected connection mode and connection limits before use.

No source-file changes are required to switch environments.

## Missing configuration

The backend intentionally does not define fallback database credentials. Missing DB username/password causes startup/configuration failure. This prevents accidental use of insecure defaults. Redis credentials may remain empty only while Redis is disabled.
