# Zero-cost deployment preparation

## Decision

Phase 1 does not deploy or provision any service. The infrastructure policy remains exactly ₹0. No automatic upgrade, paid subscription, paid APIs, or production deployment is configured.

Phase 0's official-source audit (checked 2026-11-05) found that free hosting/database tiers can sleep, pause, expire, lack backups, or have strict storage/bandwidth limits. They are acceptable only for local development or synthetic demonstration after an updated terms/cost review—not for customer data, live inventory, or payment records.

## Static frontend options

Cloudflare Pages Free is a candidate for static UI delivery subject to current file/build/Workers limits and Next.js export compatibility. Only exported static assets are planned. Custom domains and any features outside current free allowances may cost money; no domain purchase is included.

## Backend / database

No always-on free managed Java backend and durable PostgreSQL combination has been approved. Render Free services sleep and have ephemeral local files; its free PostgreSQL expires and has no backups. Supabase Free may pause inactive projects and is constrained; it is only a synthetic prototype candidate. Verify current account, region, usage, backup and billing terms before use.

The backend is portable as a Docker image. A future host/database must be assessed for free eligibility, connection/egress limits, restart behavior, durability, backups, secret handling, TLS, and no automatic charges before approval.

## Cache and telemetry

Redis is optional and disabled by default; Upstash free quota is not approved as production infrastructure. Prometheus registry/Actuator are local/self-hostable foundations, but hosting collectors consumes resources. No external monitoring/logging SaaS is configured.

## Deployment gate

Before any service provisioning:

1. Recheck official provider documentation and account billing configuration.
2. Estimate maximum monthly compute, storage, egress, builds, and operations.
3. Confirm no credit card/auto-upgrade is needed; if not possible, do not proceed without explicit approval.
4. Use synthetic data only and test service sleep/restore.
5. Confirm the selected host can run static export/backend and supports required TLS/database connection behavior.
6. Document quotas, stop conditions, data export, and deletion/restore consequences.
7. Keep production off until independent backups, recovery tests, security/legal approvals, support ownership, and acceptable continuity exist.

No free tier should be described as production-ready solely because its listed monthly price is zero.
