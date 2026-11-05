# RailConnect

RailConnect is an independent railway reservation and management platform foundation for India. It is not an official IRCTC or Indian Railways service. The approved Phase 1 foundation includes passenger, administration, and railway operations applications, a Spring Boot API, and local development infrastructure.

> **Phase 1 foundation only:** train search, inventory, reservations, payment, operational feeds, and staff actions are not enabled. The passenger pages contain no train availability or fake booking flow. No live railway integration or paid infrastructure is configured.

## Technology

- **Passenger / admin / operations:** Next.js 15, React 19, TypeScript strict mode, Tailwind CSS, shared accessible UI package.
- **Backend:** Java 25, Spring Boot 3.5, Spring Security, Spring Web/Validation, Spring Data JPA, Flyway, PostgreSQL, Actuator, springdoc OpenAPI.
- **Local infrastructure:** Docker Compose, PostgreSQL 16, optional Redis 7.
- **CI:** GitHub Actions for frontend, backend/PostgreSQL migration tests, and Docker/Compose validation.

## Repository structure

```text
apps/
  passenger-web/       # /mobile and /laptop webpages; shared components and logic
  admin-portal/        # separate administration app foundation
  operations-portal/   # separate railway operations app foundation
backend/
  railconnect-core/    # Spring Boot modular-monolith API foundation
packages/
  ui/                  # shared accessible components and design tokens
infrastructure/
  docker/              # multi-stage container builds and static web server
documentation/         # Phase 0 and Phase 1 architecture / setup guides
.github/workflows/     # validation only; no deployment workflow
docker-compose.yml
```

## Prerequisites

- Node.js 22.12+ and npm.
- Java 25 LTS.
- Maven 3.9+ to run the backend outside containers.
- Docker Engine + Docker Compose plugin for one-command local services.
- Git is recommended; the provided workspace did not contain a Git repository.

Docker was not installed in the inspected environment, so container and local database validation must be run on a machine with Docker. Frontend validation can run with Node/npm.

## Local setup

1. Copy `.env.example` to `.env`.
2. Replace the local-only database user/password placeholders. Do not reuse these for hosted services.
3. Start the local stack:

   ```powershell
   docker compose up --build
   ```

4. Open:
   - Passenger device selector: <http://localhost:3001>
   - Explicit mobile page: <http://localhost:3001/mobile>
   - Explicit laptop page: <http://localhost:3001/laptop>
   - Administration foundation: <http://localhost:3002>
   - Operations foundation: <http://localhost:3003>
   - API health/readiness/liveness: <http://localhost:8080/api/v1/health>
   - OpenAPI UI: <http://localhost:8080/swagger-ui.html>

The root passenger page selects `/mobile` at widths up to 760 px and `/laptop` above that width. The user can always open either route manually. Routes share components and logic; layout differs by device. No user-agent sniffing or repeated redirection is used.

Stop the stack with `Ctrl+C`, then run `docker compose down`. The named PostgreSQL volume persists across normal shutdowns. **Do not** run `docker compose down --volumes` unless you intend to delete local database data.

## Frontend development and checks

```powershell
npm ci
npm run dev:passenger
npm run dev:admin
npm run dev:operations
```

Run each app in a separate terminal. The development scripts use ports 3001–3003, matching the Compose static sites.

```powershell
npm run lint
npm run typecheck
npm test
npm run build
```

See [frontend setup](documentation/frontend-setup.md).

## Backend development and tests

Start PostgreSQL with Compose, then run in a separate terminal:

```powershell
cd backend/railconnect-core
$env:RAILCONNECT_DB_URL = "jdbc:postgresql://localhost:54329/railconnect"
$env:RAILCONNECT_DB_USERNAME = "your-local-db-user"
$env:RAILCONNECT_DB_PASSWORD = "your-local-db-password"
mvn spring-boot:run
```

Run unit tests without a database:

```powershell
mvn test
```

With PostgreSQL running, run migration and connectivity integration tests:

```powershell
mvn verify
```

See [backend setup](documentation/backend-setup.md) and [database setup](documentation/database-setup.md).

## Environment and zero-cost policy

Configuration is environment-driven. `.env.example` contains placeholders only; `.env` is ignored by Git. Supabase can be selected by supplying its JDBC URL, username, and password through environment variables; source files need not change.

The infrastructure budget target is **₹0**. There is no auto-deploy workflow and no paid upgrade configuration. Free tiers may sleep, pause, expire, or exceed quotas; the current assessment and explicit limitations are in [zero-cost deployment](documentation/zero-cost-deployment.md). Do not use free-tier prototype services for real passenger data, booking inventory, or money.

## Documentation

- [Phase 0 architecture index](documentation/README.md)
- [Phase 1 infrastructure overview](documentation/phase-1-infrastructure.md)
- [Local development](documentation/local-development-guide.md)
- [Security foundation](documentation/security-foundation.md)
- [CI/CD checks](documentation/cicd-guide.md)

**Phase 2 has not started and requires explicit approval.**
