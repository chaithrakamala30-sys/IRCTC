# Local development guide

## Requirements

Node.js 22.12+ (or Node 24), npm, Java 17, Maven 3.9+, and Docker Engine with Docker Compose plugin. On Windows, run commands from PowerShell at the repository root unless noted.

## Start the full stack

```powershell
Copy-Item .env.example .env
```

Edit `.env` and replace the DB user/password placeholders with local-only values. Then:

```powershell
docker compose up --build
```

The Compose stack listens only on loopback:

- Passenger portal: `http://localhost:3001`
- Passenger mobile experience: `http://localhost:3001/mobile`
- Passenger laptop experience: `http://localhost:3001/laptop`
- Admin foundation: `http://localhost:3002`
- Operations foundation: `http://localhost:3003`
- API: `http://localhost:8080`
- PostgreSQL: `localhost:54329` (loopback only)

API and DB startup use health-based dependency ordering. PostgreSQL data is in the named `postgres-data` volume. `docker compose down` preserves the volume. Deleting the volume is destructive and must be intentional.

## Run a frontend outside Compose

```powershell
npm ci
npm run dev:passenger
```

To select another workspace, use `npm run dev:admin` or `npm run dev:operations`. Configure `NEXT_PUBLIC_API_BASE_URL` when the UI starts making API calls; the foundation currently makes no API requests.

## Run the backend outside Compose

Start the Compose database, then from `backend/railconnect-core` set connection values in the current PowerShell process:

```powershell
$env:RAILCONNECT_DB_URL = "jdbc:postgresql://localhost:54329/railconnect"
$env:RAILCONNECT_DB_USERNAME = "your-local-db-user"
$env:RAILCONNECT_DB_PASSWORD = "your-local-db-password"
mvn spring-boot:run
```

Flyway applies versioned migrations on startup. If credentials are missing or PostgreSQL is unavailable, startup fails explicitly; the API does not silently use an in-memory database.

## Optional Redis

Redis is disabled by default and is never used for booking or other authoritative behavior. To run the local cache profile, set `RAILCONNECT_REDIS_ENABLED=true` in `.env`, then start with:

```powershell
docker compose --profile cache up --build
```

An unavailable Redis instance is reported by the optional health component and is excluded from readiness. PostgreSQL readiness remains required. Do not put inventory, idempotency, payment, or reservation state in Redis.

## Commands

```powershell
npm run lint
npm run typecheck
npm test
npm run build
```

```powershell
cd backend/railconnect-core
mvn test
mvn verify
```

`mvn verify` runs PostgreSQL migration/connectivity integration checks when `RAILCONNECT_DB_URL` is set and reachable. GitHub Actions starts an ephemeral PostgreSQL service for this check.

## Troubleshooting

- `npm ci` fails: verify Node version, npm connectivity, and `package-lock.json`; do not install unpinned global packages.
- API DB connection fails: check Compose DB health, host/port, DB name, and matching local credentials; inspect configuration names, not secret values.
- API readiness is down: check `http://localhost:8080/actuator/health` from the local machine and container logs for DB connectivity.
- Port conflict: update a local port mapping in `docker-compose.yml`; do not expose database ports outside loopback.
- Frontend root shows device chooser briefly: this is the accessible route fallback before client viewport detection completes; direct `/mobile` and `/laptop` links always work.

Do not use production PII, live payment credentials, or external railway APIs in local development.
