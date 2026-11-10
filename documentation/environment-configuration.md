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
| `RAILCONNECT_SUPABASE_URL` | Yes for authentication | Supabase project URL, for example `https://<project-ref>.supabase.co`. |
| `RAILCONNECT_SUPABASE_ANON_KEY` | Yes for authentication | Legacy anon/public API key used only by the backend to call Supabase Auth. |
| `RAILCONNECT_SUPABASE_SERVICE_ROLE_KEY` | Yes for staff provisioning and password updates | Supabase service-role key. Store only in Render/backend secrets; never expose it to Vercel or the browser. |
| `RAILCONNECT_PAY_DHRUVA_BANK_ENABLED` | No | Defaults false. Enables the documented Dhruva Bank demo merchant API adapter; requires backend-only `RAILCONNECT_PAY_DHRUVA_BANK_API_SECRET`. |
| `RAILCONNECT_PAY_DHRUVA_BANK_BASE_URL` | No | Defaults to the documented `https://dhruva-bank.vercel.app`; local development may use `http://localhost:3000`. |
| `RAILCONNECT_PAY_DHRUVA_BANK_API_SECRET` | When payments enabled | Backend-only Dhruva merchant API secret sent as a Bearer credential. Never set in Vercel or `NEXT_PUBLIC_*`. |
| `RAILCONNECT_PAY_PASSENGER_ORIGIN` | No | Trusted passenger website origin used to construct fixed checkout return URLs; defaults to `https://irctc-rho.vercel.app`. |
| `RAILCONNECT_PAY_REQUESTS_PER_MINUTE` | No | Per-passenger payment initiation and refresh limit; defaults to 6 requests per minute. |
| `RAILCONNECT_DB_SCHEMA` | No | Application schema for datasource, Hibernate, and Flyway; defaults to the isolated `railconnect` schema. |
| `RAILCONNECT_COMPOSE_DB_URL` | Compose | Container-network URL, normally `jdbc:postgresql://db:5432/railconnect`; use provider URL only when deliberately targeting a remote DB. |
| `RAILCONNECT_DB_NAME` | Compose | Local PostgreSQL database name; default placeholder value is `railconnect`. |
| `RAILCONNECT_DB_POOL_SIZE` | No | Default 5; lower for constrained providers and scale carefully. |
| `RAILCONNECT_MAX_REQUEST_BODY_BYTES` | No | API request-body limit; default 1 MiB. |
| `RAILCONNECT_SEARCH_MAX_DAYS` | No | Passenger train-search horizon; defaults to 365 days and accepts 1–730. |
| `RAILCONNECT_CORS_ALLOWED_ORIGINS` | No | Exact browser-facing app origins; comma-separated, no wildcard. Replace with exact site origins per environment. |
| `PORT` | No | Backend listener; default 8080. |
| `RAILCONNECT_OPENAPI_ENABLED` | No | Defaults true for local development; set false where public docs are not desired. |
| `RAILCONNECT_REDIS_ENABLED` | No | Defaults false; enable only if a Redis service is intentionally configured. |
| `RAILCONNECT_REDIS_HOST` / `RAILCONNECT_REDIS_PORT` | Redis only | Service endpoint and port; defaults target local Redis. |
| `RAILCONNECT_REDIS_USERNAME` / `RAILCONNECT_REDIS_PASSWORD` | Redis only | Provide securely; never commit live values. |
| `RAILCONNECT_REDIS_SSL` | Redis only | Set true for TLS endpoints such as Upstash. |
| `RAILCONNECT_REDIS_TIMEOUT_MS` / `RAILCONNECT_REDIS_CONNECT_TIMEOUT_MS` | No | Bounded command/connect timeouts. |
| `NEXT_PUBLIC_API_BASE_URL` | When frontend calls API | Public build-time URL, no secret; default local API URL in example. |
| `POSTGRES_PORT` | Compose | Loopback host port, default 54329. |

## Vercel frontend and hosted API

Vercel hosts the Next.js frontend; Supabase hosts PostgreSQL. Supabase does not run this repository's Spring Boot REST API. A registration page deployed on Vercel therefore also needs a separately running, publicly reachable Spring Boot API.

The passenger, admin, and operations apps proxy `/api/*` requests through their Vercel deployment to Render. This keeps browser API calls on the frontend site so the browser can send the CSRF and session cookies without relying on third-party cookies. Vercel builds for these apps must use the Next.js server output; static export remains enabled for local Docker builds.

The passenger, admin, and operations apps default their API proxy to `https://irctc-wb68.onrender.com`, so Vercel builds do not fail when no override is set. Set `RAILCONNECT_API_ORIGIN` only when the backend uses a different HTTPS origin:

| Variable | Value |
|---|---|
| `RAILCONNECT_API_ORIGIN` | Optional. The public backend HTTPS origin, with no path or trailing route. |
| `NEXT_PUBLIC_API_BASE_URL` | Optional. The origin of that specific Vercel project, with no trailing slash. If omitted, the shared client uses same-origin `/api/*` URLs. |

Set the following in Render's backend environment:

- `RAILCONNECT_SUPABASE_URL` from Supabase **Project Settings → API**.
- `RAILCONNECT_SUPABASE_ANON_KEY` from Supabase **Project API keys**.
- `RAILCONNECT_SUPABASE_SERVICE_ROLE_KEY` from Supabase **Project API keys**; this privileged key must never be set in Vercel or exposed to the browser.
- `RAILCONNECT_CORS_ALLOWED_ORIGINS` to the exact browser-facing Vercel origins, comma-separated.
- `RAILCONNECT_SESSION_COOKIE_SECURE=true`.

Keep the Supabase database variables in Render's secret settings; do not put database credentials or the service-role key in Vercel frontend variables. In Supabase **Authentication → URL Configuration**, set the Site URL to the passenger site and allow the deployed passenger/admin redirect origins. Enable email confirmation if users must verify their addresses before signing in.

Supabase Auth owns passwords and email verification. The backend creates corresponding rows in `railconnect.users` using the same Supabase Auth UUID and stores profile and role data there; password hashes are not stored in the application table.

The website's generic network error can mean the Vercel proxy is not deployed/configured, the API is offline/unreachable, or the API returned an invalid response. Check the browser Network panel for `/api/v1/auth/csrf` and verify the backend's health endpoint before retrying registration. The API client uses same-origin paths by default to keep session cookies first-party; do not hardcode a direct cross-site Render URL in the browser client.

## Supabase switch

Set `RAILCONNECT_DB_URL`, username, and password in the chosen environment. For Compose, set `RAILCONNECT_COMPOSE_DB_URL` to the same TLS-required JDBC endpoint, because the application runs in a container. The application defaults to a dedicated `railconnect` schema rather than the shared `public` schema; Flyway creates it if needed. Do not embed credentials in URLs when separately provided username/password can be used. Verify the selected connection mode and connection limits before use.

No source-file changes are required to switch environments.

## Missing configuration

The backend intentionally does not define fallback database credentials. Missing DB username/password causes startup/configuration failure. This prevents accidental use of insecure defaults. Redis credentials may remain empty only while Redis is disabled.
