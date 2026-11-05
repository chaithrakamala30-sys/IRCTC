# Database setup

## Local PostgreSQL

Docker Compose provides PostgreSQL 16 with a named persistent volume and health check. Set local-only credentials in ignored `.env`; there are no database credentials in source code or committed environment files. Compose publishes the database only to `127.0.0.1:54329`.

The application reads:

| Variable | Purpose |
|---|---|
| `RAILCONNECT_DB_URL` | JDBC URL for direct local backend runs |
| `RAILCONNECT_DB_USERNAME` | Local or hosted DB username |
| `RAILCONNECT_DB_PASSWORD` | Local or hosted DB password |
| `RAILCONNECT_DB_POOL_SIZE` | Hikari maximum pool; default 5 |
| `RAILCONNECT_DB_MIN_IDLE` | Minimum idle connections; default 0 |
| `RAILCONNECT_DB_CONNECTION_TIMEOUT_MS` | Connection acquisition timeout |
| `RAILCONNECT_DB_VALIDATION_TIMEOUT_MS` | Connection validation timeout |
| `RAILCONNECT_DB_MAX_LIFETIME_MS` | Connection maximum lifetime |

Defaults favor a small development instance. Tune pool size to the provider's direct/pooler connection budget; do not multiply a fixed pool limit across many instances.

## Supabase PostgreSQL

The backend uses standard PostgreSQL JDBC and can target Supabase through environment variables; source is unchanged. Set the project host/database/username/password from the provider dashboard, using the provider's TLS-required connection string. Never commit or paste the credential URL in issue trackers/logs. Prefer a connection method compatible with Flyway and session-based JDBC behavior; transaction poolers can have prepared-statement/session limitations.

Before any real customer data, recheck current free-plan storage, pause behavior, region/residency, connection limits, backups, restore, and service terms. The Phase 0 audit treats Supabase Free as a synthetic prototype candidate, not an approved production database.

## Flyway and migration conventions

- Migrations are in `backend/railconnect-core/src/main/resources/db/migration`.
- `V1__create_application_metadata.sql` is intentionally minimal and is not the railway domain schema.
- The default application schema is `railconnect`; Flyway creates it if needed. Keep application tables isolated from Supabase's shared `public` schema.
- Hibernate schema generation is `validate`; Flyway owns schema changes.
- Add forward-only, reviewed migrations with explicit constraints/indexes and tests. Never put secrets or personal data in migration fixtures.
- Do not use automatic destructive schema updates or manually modify `flyway_schema_history`.

## Verify connection and migration

With local PostgreSQL running and backend variables set:

```powershell
cd backend/railconnect-core
mvn verify
```

The integration test executes `SELECT 1`, checks successful Flyway history, and confirms the minimal metadata table. A normal app start also runs Flyway. No local test result should be claimed without running this against PostgreSQL.

### Supabase verification status

The configured Supabase endpoint accepted a TLS PostgreSQL connection and identified itself as PostgreSQL 17.11. An initial integration attempt did not apply V1: Flyway reported that `public` was non-empty but had no Flyway schema history, then stopped. No migration was applied in that attempt. To avoid baselining or modifying `public`, the application was configured to create and use a dedicated `railconnect` schema. The subsequent `mvn verify` succeeded: V1 was applied there and the database migration/API integration tests passed. Do not enable automatic baselining or alter existing schema objects to bypass Flyway safeguards.

## Pooling and transactions

Hikari is bounded (default maximum 5, idle 0, short acquisition timeout) to support constrained local/free-tier instances. Use transaction boundaries in backend services; do not hold DB connections during provider/network calls. Future inventory correctness must remain in PostgreSQL constraints/transactions, never Redis.

## Backup and recovery

Local Compose volume persistence is not a backup. For a local snapshot, use `pg_dump` to an access-restricted local path and test `pg_restore` into a disposable database. Do not commit dumps.

Free hosted tiers may not provide adequate backups, point-in-time recovery, or retention. A separate encrypted and tested backup strategy is mandatory before real reservations/payments. No backup service is enabled in Phase 1 because cost and provider have not been approved.
