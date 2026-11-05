# Architecture risk register

Ratings are initial qualitative planning estimates, not measured probabilities. Owners are roles to assign; all material risks must be reviewed at phase gates.

| ID | Risk | Likelihood / impact | Mitigation / trigger | Owner |
|---|---|---|---|---|
| R-01 | ₹0 hosting cannot meet commercial availability, backup, or recovery expectations | High / Critical | Keep free tier to synthetic demo; production gate requires independently tested DB backup, continuity and support. Trigger: provider pause/expiry/limit or any real transaction. | Product + SRE |
| R-02 | Seat double booking under concurrent requests | Medium / Critical | PostgreSQL exclusion constraint, deterministic locks, serializable/constraint retries, concurrency tests, integrity query; stop affected run on invariant breach. | Reservation engineering |
| R-03 | Incorrect or unverified railway rules create invalid bookings/refunds | High / Critical | Versioned approved rule registry, provenance, fail-closed missing rules; do not claim official policy. | Railway domain owner |
| R-04 | Inventory is fabricated, stale, or not owned/authorized | Medium / Critical | Admin-sourced audited import, effective date/checksum/approval; no public availability until owner accepts. | Operations |
| R-05 | Free database pauses, reaches storage/egress limit, or loses data | High / Critical | Prototype only; monitor quotas; independent backup/restore; disable new bookings when DB unavailable. | Data/SRE |
| R-06 | Payment callback lost, spoofed, duplicated, or delayed | Medium / Critical | Signed raw-body validation, event uniqueness, durable inbox, reconciliation, UNKNOWN state, no redirect-based confirmation. | Payments |
| R-07 | Payment succeeds after inventory hold expired | Medium / High | Keep ambiguous state; recheck inventory; explicit refund/recovery policy; reconcile and alert. | Payments + reservations |
| R-08 | Refund double execution or amount exceeds settlement | Medium / High | Idempotency, payment-row lock, immutable ledger, cumulative cap, finance reconciliation. | Finance engineering |
| R-09 | PII/credentials exposed through logs, support, or free third-party services | Medium / Critical | Data minimization, redaction tests, access controls, retention/legal review, no unapproved SaaS. | Security/privacy |
| R-10 | API abuse exhausts free quotas or blocks legitimate traffic | High / High | Per-run/IP/account controls, bounded work, admission control, usage alerts, graceful 429/503. | API/SRE |
| R-11 | Hosting sleeps/cold starts and booking UX times out | High / High | Label demo/best-effort; idempotent retry; never show success-shaped fallback; production needs always-available approved design. | Product/SRE |
| R-12 | Free provider terms/prices change, require card, or silently incur usage costs | Medium / High | Revalidate official terms before provision; disable auto-upgrade; no card where possible; manual monthly audit; no deployment if cost risk unclear. | Platform owner |
| R-13 | Unauthorized external railway integration violates terms or policy | Low / Critical | No integration without written authorization and vetted API spec; egress allowlist; legal review. | Product/legal |
| R-14 | Admin compromise or insider modifies fare/inventory/refund | Medium / Critical | MFA, least privilege, scope, dual approval, tamper-evident audit, step-up, access review. | Security + operations |
| R-15 | Incomplete privacy/compliance analysis blocks launch or harms passengers | Medium / Critical | Qualified India legal/privacy review, purpose/retention mapping, incident and grievance process before real data. | Privacy/legal |
| R-16 | Modular monolith becomes coupled or premature service split causes distributed inconsistency | Medium / Medium | Enforce module boundaries and architecture tests; split only on evidence with owned migration plan. | Architecture |
| R-17 | Free-tier database/hosting not located or configured to meet data obligations | Medium / High | Confirm region, subprocessors, transfer/security and contract terms before any personal data; synthetic data until approved. | Privacy/platform |
| R-18 | Backups exist but cannot restore consistent booking and payment state | Medium / Critical | Independent restore drills, provider event reconciliation, RPO/RTO evidence; block production if untested. | Data/SRE |
| R-19 | Accessibility defects exclude passengers or create booking errors | Medium / High | WCAG 2.2 AA target, manual keyboard/screen-reader tests, accessible error/status patterns. | UX/QA |
| R-20 | Peak traffic assumptions are wrong; capacity target is presented as guarantee | High / High | Treat loads as provisional test profiles; test realistic hot keys; publish measured envelope and limits. | Product/SRE |

## Stop-ship conditions

- Any confirmed overlapping active seat allocations or unexplained ledger/refund mismatch.
- Unknown provenance or authorization for routes/inventory/provider integrations.
- Real payment enabled without legal/compliance/provider review and reconciliation process.
- Real customer data on an unbacked/ephemeral/pause-prone free service without explicit, informed and technically viable acceptance.
- Automatic billing/upgrade enabled or cost cannot be constrained to ₹0.
- Critical security finding, missing admin audit, failed restore drill, or unclear incident ownership.

## Review cadence

Review at every phase exit, before new provider/service, before importing real data, before enabling live payment, and at least quarterly after launch. Record owner, status, evidence, residual risk acceptance, and review date.
