# Infrastructure and DevOps architecture

## Repository layout proposal

```text
/
  apps/
    passenger-web/             # /mobile and /laptop device-specific routes
    admin-portal/
    operations-portal/
  packages/
    ui/                         # shared accessible components and design tokens
  backend/
    railconnect-api/
  documentation/
  infrastructure/
    compose/
    deployment/
  .github/workflows/
```

Keep one backend deployable initially. Share types through a generated OpenAPI client, not a second hand-maintained API model. Phase 1 establishes the three app foundations and a shared UI package; the passenger application presents distinct mobile and laptop webpages with shared components and API contracts.

## Local development

- Java/Spring Boot and Node/Next.js toolchains pinned in repository manifests/toolchain files.
- Docker Compose may run PostgreSQL for local use; no production data or secrets in images. A native local PostgreSQL install is an alternative if Docker Desktop licensing or resource requirements are unsuitable.
- Flyway migrations run on a disposable local database; test fixtures use obviously synthetic stations/trains and cannot be loaded into hosted/prod profiles.
- Optional local Redis only for adapter tests; the app must run without it.
- Internal test payment provider is selected only by explicit local/test profile and simulates outcomes without collecting card data.
- `.env.example` contains names/placeholders only; real `.env` excluded from version control.

## Environments

| Environment | Data | External effects | Gate |
|---|---|---|---|
| Local | Synthetic | None; test provider only | Developer |
| CI | Ephemeral synthetic DB | No real provider credentials, no external railway APIs | Automated checks |
| Preview | Synthetic/non-sensitive only | Disabled or sandbox-only | Reviewed deploy |
| Staging | Approved masked/synthetic snapshot | Sandbox payment; no public inventory claim | Release owner |
| Production | Approved real data | Only authorized integrations | Separate explicit approval and funded/verified recovery plan |

Never copy production PII to lower environments. No live traffic deployment is authorized by Phase 0.

## CI/CD pipeline

On pull request: format/lint, typecheck, unit tests, PostgreSQL integration tests, migration validation, OpenAPI compatibility, secret/dependency scanning, container build, SBOM, and artifact retention within the account's free quota. Use least-privilege CI token and pin third-party actions to reviewed immutable references.

On protected-branch merge: build once; deploy the same immutable artifact to preview/staging after checks; run smoke tests and migration preflight. Production requires manual approval, verified backup, cost check, security gate, approved release notes, and rollback plan. Avoid automatic paid service creation and deploy-on-fork with secrets.

## Schema and application rollout

Run Flyway from a single controlled migration step. Migrations should be additive first, tolerate deployed app versions, backfill in bounded batches, then remove old structures in a separate release. For small free/demo services, maintenance mode and a tested backup may be safer than complex zero-downtime rollout. Database restore is not a routine rollback for a live system; use forward fix or compensating migration.

## Secrets and configuration

Separate dev/staging/prod credentials. Inject at runtime via provider secret store or protected deployment environment; never bake into Docker layers, frontend bundles, source maps, logs, or CI artifact. Verify secret masking limits. Rotate credentials periodically and after personnel/incident changes. Do not rely on provider free tiers to provide enterprise secret controls.

## Container and platform

Use multi-stage build, non-root runtime, minimal base image, pinned dependency versions, health checks, bounded memory/thread settings, and graceful shutdown. Persist files only in managed object storage or database; Render free web service filesystem is ephemeral and cannot contain uploads, SQLite, or authoritative state.

## Monitoring and supportability

Expose liveness/readiness and application metrics for DB connectivity, booking failures, inventory constraint conflicts, payment outcomes, refund aging, outbox lag, expired holds, and reconciliation exceptions. Emit structured redacted logs and correlation IDs. OpenTelemetry-compatible traces are optional; collector storage/export must remain local or within verified free usage. Self-hosted Prometheus/Grafana consume compute/storage and are not “free” if hosted.

Alert thresholds require owners and response channels; free tiers may lack alert delivery. At minimum maintain a manual dashboard/runbook and durable database health/integrity checks. Never send PII/secrets to third-party log/analytics services without review.

## Backup, restore, rollback

Backups must be encrypted, independent of the primary account, integrity-checked, and restored in a timed drill. Define RPO/RTO and retention with business/legal owners. A free-tier database with no backups or pause/expiry behavior is not acceptable for real booking/payment records. Maintain application artifact rollback, migration compatibility, and provider credential rollback/revocation instructions.

## Future Kubernetes architecture

Kubernetes is explicitly **not** the initial deployment target. A future reference deployment may use managed cluster, ingress/API gateway, multiple stateless app replicas, managed PostgreSQL, optional Redis/Kafka/OpenSearch, secrets manager, telemetry stack, and backup storage. Each adds paid control-plane/compute/storage/network cost and operational complexity. Revisit only with evidence, funding/explicit exception to ₹0, security review, capacity plan, and migration approval.
