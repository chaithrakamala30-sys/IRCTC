# Deployment strategy

## Phase 0 status

No deployment has been created or authorized. Phase 1 provides local application foundations only. Deployment must remain stopped until a separate deployment approval, fresh provider review, and cost/security gate.

## Local and preview stages

1. Developer runs frontend/backend and local PostgreSQL with synthetic data.
2. CI compiles/tests and builds immutable frontend/backend artifacts with no production credentials.
3. Preview deploy uses only synthetic data and sandbox/test payment; disable real notifications and provider integrations.
4. Staging uses approved non-sensitive data, production-like configuration, explicit sandbox and backup/restore drills.
5. Production only after legal, security, payment, operations, data provenance, cost, recovery, and owner approvals.

## Candidate free deployment (prototype only)

- Static Next.js export on Cloudflare Pages; confirm exact Next.js features and adapter/build compatibility before choosing. Avoid requiring SSR, server actions, or persistent edge state unless the selected runtime supports them and remains within free limits.
- Spring Boot Docker web service on Render free tier only for disposable preview/demo, not for bookings requiring continuous availability. Free service sleeps after inactivity and has ephemeral filesystem; monthly instance-hour and bandwidth/build quotas apply.
- Supabase free PostgreSQL may support synthetic prototype data after confirming region, connectivity, pause behavior, storage, backups, and connection limits. Do not use free DB for real booking or financial records absent owner acceptance of unacceptable durability risk.
- Optional Upstash Redis is non-authoritative; skip unless a measured need exists and use is safely within quotas.
- No Kafka, Kubernetes, OpenSearch, paid monitoring, external email/SMS, or paid storage in the free baseline.

These are candidates, not a commitment or production recommendation. Provider details and official sources are in [zero-cost infrastructure](zero-cost-infrastructure.md).

## Deployment safety gates

- Fresh official pricing/limits review and projected ₹0 usage proof, including data transfer and CI.
- No payment method/auto-upgrade unless explicitly approved; inspect each account’s billing and auto-upgrade settings.
- Synthetic data only for free-tier demos; clear labels that inventory and payments are simulated.
- Database migration, backup/restore evidence, least-privilege secrets, health checks, and rollback.
- Load/concurrency tests on target constraints; no capacity claims from local-only tests.
- Disable deployment if a provider requests upgrade/payment, quota is exceeded, data persistence is unclear, or service pauses.
- Feature flags default off for live payments, external railway connections, and production inventory.

## Database release and rollback

Run one migration job with a lock, record schema checksum, and keep app/schema compatibility for the deployment window. For demo systems, maintenance mode may be appropriate. If a release corrupts state, stop new booking commands, preserve forensic evidence, restore from independently verified backup only under incident plan, and reconcile provider events before reopening. Never silently roll back a payment ledger or delete reservations.

## Cutover and operational readiness

Before any real traffic: named incident/on-call owner, escalation channel, support/refund process, privacy contact, rate/cost alert, monitoring data retention, tested backup, provider status runbook, reconciliation job, security incident plan, and explicit RPO/RTO. Free-tier sleeping, suspension, or lack of support makes this gate unmet for commercial production.

## Rollback triggers

Disable bookings immediately on any confirmed overlap invariant breach, unexplained ledger mismatch, unauthorized administrative action, DB restore uncertainty, invalid provider signature acceptance, or financial state corruption. Preserve reads where safe. Reopen only after root cause, data reconciliation, independent review, and auditable approval.
