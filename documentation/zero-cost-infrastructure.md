# Zero-cost infrastructure audit

**Policy:** target recurring infrastructure spend is exactly ₹0. No automatic upgrades, paid services, paid APIs, or paid monitoring. This audit is a point-in-time check of official provider pages on **2026-11-05**; provider terms, quotas, regions, eligibility, and billing behavior can change. Recheck immediately before account creation, configuration, or deployment. This is not a guarantee of ₹0 under all traffic or usage.

At Phase 1 verification, Cloudflare Pages limits were fetched again successfully. A fresh fetch of the Render, Supabase, and Upstash pages was blocked by the research tool's network policy; the figures below remain the official-source snapshots checked during Phase 0 on the same date, not a second independent confirmation. No provider account or service was provisioned.

## Service inventory and limits

| Service | Proposed use | Verified free allowance / limitations (checked 2026-11-05) | Data / sleep / cost risk | Decision |
|---|---|---|---|---|
| Cloudflare Pages | Static Next.js assets only | Free: 500 builds/month; 1 concurrent build; build timeout 20 minutes; up to 20,000 files/site; 25 MiB max asset; 100 custom domains/project. Pages Functions count against Workers quotas. | Avoid Pages Functions and runtime features unless their quota is separately verified. A custom domain is separately registered and may cost money. No assumption that arbitrary Next.js SSR runs unchanged. | Candidate for static synthetic demo; verify static export compatibility and bandwidth/service terms. |
| Render Free Web Service | Temporary Spring Boot preview | Free service sleeps after 15 min without inbound traffic; next request may take about one minute to start. 750 free instance hours/workspace/month; ephemeral filesystem; service can restart; bandwidth/build quotas apply. If bandwidth/build quota exhausted and payment method exists, supplementary charges may apply; without one service/build may be suspended/disabled. | Not always-on; local files lost on restart/sleep/redeploy. Not suitable for commercial reservations or critical state. | Prototype only; disable paid billing and monitor included usage. |
| Render Free PostgreSQL | Avoid for durable data; optional short-lived test only | One active free DB/workspace; 1 GB; expires 30 days after creation, then 14-day grace before deletion; no backups; no managed connection pool; maintenance/restarts can interrupt. | Expiry/deletion and no backups are unacceptable for real booking/payment records. | Do not use for persistent real user data. |
| Supabase Free PostgreSQL | Candidate synthetic prototype DB | $0; 500 MB DB, shared CPU/500 MB RAM; 5 GB egress + 5 GB cached egress; 1 GB file storage; 50,000 MAU; max two active projects; free projects pause after 1 week inactivity. | No production SLA implied. Free backup/restore guarantees must be separately verified; current fetched pricing summary lists daily backups only on paid plan. Limits and pause can interrupt app. | Candidate only for synthetic prototype; before personal data, verify region, pauses, backup, RPO/RTO, pooling and account terms. |
| Upstash Redis Free | Optional disposable cache | Free tier reports 250 MB and 500,000 commands/month; one DB on free account. Entering a card upgrades account/database to pay-as-you-go per current FAQ. | Limits/command counts can be exceeded; Redis is non-authoritative. Avoid storing holds/payment state. | Omit initially; if used, no card/auto-upgrade and monitor command/storage usage. |
| Cloudflare R2 Standard | Optional static/public objects, not booking records | 10 GB-month storage, 1M Class A ops/month, 10M Class B ops/month, egress free. Free allowance is Standard only; billable operations/storage beyond included usage; usage rounds to billing unit. | Infrequent Access has no free-tier benefit and retrieval/minimum-duration charges. Over-quota storage/operations can cost money; require usage alerts and manual stop procedure. | Prefer no object store at first; local dev or DB-backed small metadata. If R2 used, public immutable non-sensitive assets only. |
| GitHub / GitHub Actions | Source control and CI | GitHub states standard hosted runner use is free for public repositories; private repository included storage/minutes depend on account plan; additional usage may be billed. Self-hosted runners avoid hosted minutes but use user's own machine/resources. | CI can create charges if included limits are exceeded or billing is enabled. Third-party actions are supply-chain risk. | Use Actions within confirmed account quota; set usage controls; no production secrets in untrusted workflows. |
| PostgreSQL / Redis / Prometheus / Grafana / OpenTelemetry | Open-source local development/runtime software | Software is open source subject to each project’s license; no subscription required for local self-hosting. | Compute, disk, network, electricity, and maintenance are not automatically free when hosted. Licensing of desktop tooling and commercial use must be checked. | Use locally for development. Do not call a self-hosted production stack “zero cost” without documenting resource owner and availability. |
| Payment provider | Later authorized live checkout | No provider selected. Sandbox may be free, but availability/limits must be verified. Live transactions can incur provider charges. | Gateway fees, taxes, settlement, disputes, verification, or required account services can violate ₹0. | Internal simulation only until provider and fees are explicitly approved. |
| Email/SMS/monitoring/search/CDN | Not selected | No external paid or card-dependent service selected. | Free allowance, quotas and overages vary; SMS and email delivery are not presumed free. Hosted monitoring can add cost. | Use durable outbox and local logs in prototype; select none until independently verified. |

## Source links (official pages)

- Cloudflare Pages limits: <https://developers.cloudflare.com/pages/platform/limits/>
- Cloudflare R2 pricing: <https://developers.cloudflare.com/r2/pricing/>
- Render free instances: <https://render.com/docs/free>
- Supabase pricing: <https://supabase.com/pricing>
- Upstash Redis pricing FAQ: <https://upstash.com/pricing/redis>
- GitHub Actions billing/usage: <https://docs.github.com/en/actions/concepts/billing-and-usage>
- GitHub plans: <https://github.com/pricing>

The fetched Render documentation explicitly warns that free instances are not for production applications. Provider-provided wording and allowances must be rechecked at use time.

## Alternatives

- **Static UI:** Cloudflare Pages free static hosting is the preferred checked candidate. GitHub Pages or Render static sites may be considered only after checking current account eligibility, limits, and terms; do not infer free backend hosting from static hosting.
- **Backend:** local Spring Boot for development; Render free only for non-critical preview. A self-hosted machine is an alternative only if the owner accepts hardware, power, network, security, uptime, and public-address costs/risks. No always-on free managed Java backend has been approved.
- **Database:** local PostgreSQL for development; Supabase free is a synthetic-demo candidate; Render free Postgres expires and has no backups. No free provider is approved for real bookings.
- **Cache:** omit; if locally useful, self-host Redis. Upstash is optional only within the stated free cap and no billing activation.
- **Object files:** local development storage; avoid user uploads initially. R2 is optional for non-sensitive static assets only under measured free allowance.
- **Search/messaging:** PostgreSQL indexes/full-text and transactional outbox; do not add OpenSearch/Kafka at zero budget without a verified no-cost plan.
- **Monitoring:** local structured logs, health endpoints, and open-source metrics. A remotely hosted monitoring SaaS is not approved by virtue of a free trial.

These are candidates rather than endorsements; revalidate official current terms before adoption.

## Hidden-cost and overage checklist

- Account asks for a payment card or enables billing/automatic upgrade.
- Usage-based egress, build minutes, storage, operations, logs, images, database compute, or file retrieval exceeds free allowance.
- Custom domain registration/renewal, DNS add-ons, taxes, currency conversion, support plan, or identity verification incurs a fee.
- Live payment provider fees, refunds, disputes, chargebacks, settlement fees, or compliance tooling.
- Email/SMS/OTP, AI, maps/geocoding, analytics, error reporting, malware scanning, or external data APIs.
- Data transfer between providers/regions, backup export, restore, or large CI artifacts.
- Developer-owned device, electricity, and internet are outside managed-tier pricing but still real resources; disclose if “₹0” is intended to mean total cost of ownership.
- Free-tier limits and eligibility may change; a dashboard quota is not a contractual SLA.

## Manual cost-control procedure

1. Before every service change, capture the official pricing/limits page, account plan, region, data residency, expected monthly usage, and projected maximum spend.
2. Do not enter a payment method unless owner explicitly authorizes it. Turn off auto-upgrade / overage billing; if that cannot be guaranteed, do not deploy.
3. Keep production credentials and real PII out of free preview tiers. Use synthetic data with visible demo labels.
4. Configure provider usage notifications and inspect dashboard usage monthly and before load tests. Do not rely solely on provider email alerts.
5. Set app-side quotas for requests, DB size, log retention, build frequency, object operations, and background retries.
6. On approaching a limit, stop optional workloads and block new nonessential writes; never degrade reservation integrity or make a paid upgrade.
7. If a limit is exceeded or billing becomes uncertain, disable deployment and preserve state/exports using approved procedures. Do not automatically switch providers or erase data.
8. Re-audit when provider, account plan, app traffic, storage, CI, or payment behavior changes.

## ₹0 production feasibility decision

Current free-tier evidence does **not** justify a claim of continuous, durable, commercial-grade reservation service at ₹0. Render free sleeps and its free database expires without backups; Supabase free can pause and is resource-limited; free CDN/Redis/object allowances can be exceeded. The safe Phase 0 decision is therefore:

- ₹0 is acceptable for local development and synthetic prototypes.
- No real inventory, real payments, or personal customer data on the candidate free deployment.
- If future business requirements require availability, recovery, support, or throughput beyond free limits, block production until the owner explicitly revises the budget or accepts a documented alternative risk. Do not silently weaken integrity or enable paid services.
