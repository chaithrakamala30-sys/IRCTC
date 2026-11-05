# Security threat model

## Scope and trust boundaries

Assets include credentials/sessions, personal and travel data, admin permissions, seat inventory, booking references, payment intents, provider callbacks, financial ledger, rule versions, audit history, and secrets. Entry points are browser/API, import jobs, administrator workflows, payment webhooks, CI/CD, and provider consoles. Assumed threats include unauthenticated internet actors, compromised passenger accounts, malicious insiders, compromised dependencies, provider outages, and accidental configuration.

Use STRIDE at module and endpoint design time; use OWASP ASVS (target level and verification scope to be approved) and current OWASP Top 10 as control checklists, not certification claims.

## Threats and mitigations

| Threat | Primary controls | Detection / response |
|---|---|---|
| Credential stuffing / takeover | Argon2id; breached-password screening where available; MFA, especially staff; login throttles, generic responses, secure recovery | Auth anomaly metrics, session revocation, incident playbook |
| Session theft / CSRF | TLS, Secure/HttpOnly/SameSite cookies, CSRF token/origin checks, short session lifetime, rotation/revocation | Unusual session/device events, revoke token family |
| Automated booking abuse / denial of inventory | Per-account/IP/run limits, bot controls, bounded request horizon, queue/backpressure, idempotency | Booking velocity, lock wait, inventory hold metrics; throttle/disable feature |
| Broken access control / IDOR | Server-side object ownership and role/scope checks on every operation; deny by default | Authorization tests, audit sensitive reads and mutations |
| SQL injection | Parameterized SQL/JPA, allowlisted sort/filter, least-privilege DB role | SAST/DAST, query error monitoring; rotate credentials if exploited |
| XSS / content injection | Output encoding, safe templates, strict CSP, no unsafe HTML, sanitize only when rich content is required | CSP reports with PII-safe logging |
| SSRF | No arbitrary URL fetch; strict provider host allowlist, DNS/IP validation, egress restrictions; block metadata/private ranges | Egress anomaly alerts; isolate/disable connector |
| Payment replay / spoofed webhook | Signature verification on raw bytes, signed timestamp, key rotation, unique provider-event ID, amount/currency/reference validation | Quarantine invalid/duplicate/mismatched events; finance reconciliation |
| Double booking / race | PostgreSQL exclusion constraint + row locks + transaction + deterministic retry | Integrity checker, invariant test, high-severity alert and freeze affected run |
| Privilege escalation / insider action | Granular RBAC and scopes, MFA/step-up, separation of duties, approval for sensitive config/refund | Tamper-evident audit, review alerts, emergency role revocation |
| Sensitive data exposure | Data minimization, field-level response allowlists, encryption in transit/at rest where supported, redacted logs, retention controls | Secret/PII scanning, access logs, incident response |
| Dependency / supply-chain compromise | Locked dependencies, SBOM, signed/reviewed build artifacts where feasible, provenance, automated vulnerability scanning, no unreviewed runtime downloads | CI alerts, patch SLA, rollback and credential rotation |
| Denial of service / resource exhaustion | Body/time limits, quotas, DB pool limits, bounded pagination, rate limits, circuit breakers, async backpressure | Saturation alerts, temporary feature shedding |
| Provider account compromise / outage | MFA, least-privilege provider account, independent backups, portable data, no auto-upgrade, explicit spend cap/alerts | Provider audit review; outage communication and recovery |

## Identity, authorization, and session design

Use Argon2id with current OWASP-recommended parameters benchmarked on actual runtime. Store passwords only as salted hashes; MFA recovery secrets hashed/encrypted appropriately. Prefer server-side session records with random opaque cookies, rotation on authentication/privilege change, logout revocation, CSRF protection, secure cookie flags, and idle/absolute expiry. Refresh tokens, if used, rotate one-time with reuse detection.

RBAC maps user roles to permissions; a separate scope relation constrains station/region/resource. Roles: passenger, support officer, reservation officer, station manager, finance administrator, railway administrator, super administrator. No shared accounts. Finance refund approver should differ from requester for material thresholds; emergency break-glass is time-limited, reasoned, alerted, and reviewed.

## Data protection and India compliance

Before production, obtain qualified Indian legal/privacy review of the Digital Personal Data Protection Act, 2023 and rules/notifications applicable at that time, consumer obligations, retention/deletion, cross-border processing, grievance handling, breach notification, and any sector/payment duties. This document is not legal advice and does not determine applicability. Inventory what personal data is necessary, purpose and lawful basis, processors, residency/transfer, retention, access/correction/erasure process, and incident contact.

Keep passenger PII separate from high-volume analytical events where practical; minimize age/identity fields to approved booking need. Avoid storing identity documents unless legally/operationally necessary and specifically approved. Mask PII in support/reporting by default.

## Secrets, logging, and audit

Use per-environment secret injection; never commit secrets, expose them to frontend builds, or print them. Rotate DB/provider keys and revoke on incident. Free-tier secret storage/security features must be checked; local `.env` files remain untracked and access-restricted. Structured logs contain correlation ID, module, result, latency, and redacted identifiers only. Audit actor, action, target, reason, before/after diff with sensitive-field redaction, approver, timestamp, and request trace. Restrict audit mutation and export.

## Secure development and incident response

Require peer review, unit/integration authorization tests, dependency/SAST scans, secret scanning, migration review, and release checklist. Define severity triage, containment, credential revocation, forensic preservation, passenger/provider/legal notification decision, recovery validation, and post-incident actions before live data. Practice tabletop events: account takeover, exposed credential, double allocation, forged callback, provider data breach, and deletion/restore failure.
