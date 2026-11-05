# Docker and Compose guide

## Local stack

`docker-compose.yml` runs:

- PostgreSQL 16 with a persistent named volume and startup health check.
- Spring Boot backend, built from a multi-stage Java 17 Dockerfile and run as a non-root user.
- Passenger, admin, and operations static web containers, built from one multi-stage Next.js Dockerfile and served by an unprivileged Nginx image.
- Redis 7 under the optional `cache` profile only.

All mapped ports bind to loopback; the DB is not exposed publicly. Production deployment is not configured.

## Start and stop

```powershell
Copy-Item .env.example .env
# Replace local-only placeholders in .env
docker compose up --build
docker compose down
```

Compose starts PostgreSQL before the backend and waits on health checks. Frontend containers use static files and do not require backend state to serve pages. `docker compose down` keeps the PostgreSQL named volume.

## Optional Redis profile

Set `RAILCONNECT_REDIS_ENABLED=true`, then:

```powershell
docker compose --profile cache up --build
```

Redis has no published host port and is not used as an inventory source. If disabled/unavailable, it cannot change application correctness.

## Build images individually

```powershell
docker build -f infrastructure/docker/backend.Dockerfile backend/railconnect-core
docker build -f infrastructure/docker/frontend.Dockerfile `
  --build-arg APP_WORKSPACE=@railconnect/passenger-web `
  --build-arg APP_PATH=apps/passenger-web .
```

Use matching workspace/path values for `admin-portal` and `operations-portal`.

## Security and operations notes

- `.dockerignore` excludes local environment files, dependencies, and build outputs.
- Backend container runs as non-root; frontend uses the unprivileged Nginx image.
- Database credentials are injected through Compose environment; never use production secrets locally or bake secrets into layers.
- Nginx serves static routes, sets baseline security headers, disables server tokens, and limits client body size.
- Named DB volume is local persistence, not backup. Avoid `docker compose down --volumes` unless intentionally deleting local state.
- Image tags should be refreshed/reviewed for security updates; pinning digests can be introduced with a controlled update process.
