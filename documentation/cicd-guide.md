# CI/CD guide

## Workflows

- `frontend.yml`: npm install from lockfile, ESLint, TypeScript check, static builds, and npm dependency audit.
- `backend.yml`: Java 17 build/tests and `mvn verify` with an ephemeral PostgreSQL 16 service; the integration test checks DB connection and Flyway migration.
- `infrastructure.yml`: validates Compose config from placeholder-only `.env.example` and builds backend plus three frontend images. It does not push or deploy images.
- `dependabot.yml`: weekly npm, Maven, and GitHub Actions dependency update proposals.

Workflows run on relevant pull requests and pushes to `main`. GitHub Actions is a user-provided CI service; private repository included minutes/usage vary by account. Monitor usage and do not enable paid overages or auto-upgrade.

## Permissions and secrets

Workflows use read-only repository permissions and no production credentials. Database CI credentials are disposable values for the isolated ephemeral test service only. Do not use untrusted PR code with secrets or grant write permissions to build jobs.

## Run equivalent checks locally

```powershell
npm ci
npm run lint
npm run typecheck
npm run build
```

```powershell
cd backend/railconnect-core
mvn test
mvn verify
```

`mvn verify` needs a reachable PostgreSQL instance; set `RAILCONNECT_DB_URL`, username, and password to local test values. Docker/Compose checks need Docker installed.

## Deployment

There is deliberately no deployment workflow. No automatic provisioning, paid services, paid upgrades, or production deploy happens from a branch push.
