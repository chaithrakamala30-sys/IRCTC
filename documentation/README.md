# RailConnect architecture documentation

This package contains the Phase 0 architecture baseline and Phase 1 setup guides for an independent, administratively managed railway reservation platform. The repository was empty when first inspected; Phase 1 has since added local application foundations, with no train inventory or railway-provider integration.

## Reading order

1. [Project overview](00-project-overview.md)
2. [Functional requirements](01-functional-requirements.md) and [non-functional requirements](02-non-functional-requirements.md)
3. [System architecture](03-system-architecture.md), [domain model](04-domain-model.md), and [database design](05-database-design.md)
4. [API architecture](06-api-architecture.md), [reservation engine](07-reservation-engine-design.md), and [payment architecture](08-payment-architecture.md)
5. [Security threat model](09-security-threat-model.md), [scalability strategy](10-scalability-strategy.md), [infrastructure and DevOps](11-infrastructure-and-devops.md), [testing strategy](12-testing-strategy.md), [deployment strategy](13-deployment-strategy.md), and [UI / roles](16-ui-navigation-and-roles.md)
6. [Implementation roadmap](14-implementation-roadmap.md), [risk register](15-risk-register.md), and [zero-cost infrastructure audit](zero-cost-infrastructure.md)
7. Phase 1: [infrastructure overview](phase-1-infrastructure.md), [local development](local-development-guide.md), [database](database-setup.md), [backend](backend-setup.md), [frontend](frontend-setup.md), [Docker](docker-guide.md), [environment](environment-configuration.md), [security](security-foundation.md), [CI](cicd-guide.md), and [zero-cost deployment](zero-cost-deployment.md)
8. Phase 2 identity: [overview](phase-2-identity-overview.md), [authentication architecture](authentication-architecture.md), [session management](session-management.md), [password security](password-security.md), [RBAC matrix](rbac-matrix.md), [MFA and recovery limitations](mfa-and-recovery-limitations.md), [admin access control](admin-access-control.md), [security audit logging](security-audit-logging.md), [API reference](identity-api-reference.md), and [testing report](phase-2-testing-report.md)
9. Phase 3 railway master data: [overview, schema, API, permissions, and validation status](phase-3-master-data.md)
10. Phase 4 passenger train search: [feature scope](phase-4-train-search.md), [architecture](train-search-architecture.md), [search algorithm](search-algorithm.md), [overnight journeys](overnight-journey-calculation.md), [schedule versions](schedule-version-selection.md), [station autocomplete](station-autocomplete.md), [API reference](search-api.md), [performance](search-performance.md), [caching](search-caching.md), and [testing report](phase-4-testing-report.md)
11. [Mermaid diagrams](diagrams/)

## Baseline and change control

- Phase 0 and Phase 1 are complete. Phase 2 identity/RBAC implementation is present; some database-backed verification remains pending. Phase 3 master-data work and Phase 4 passenger timetable search are tracked in their own guides; Phase 4's PostgreSQL-backed validation remains pending.
- Indian Railways / IRCTC policies and interfaces are not assumed or reproduced. Any future integration requires documented authorization and verified specifications.
- Inventory is owned and administered by RailConnect. No real inventory is available until an authorized administrator loads and validates it.
- The infrastructure budget target is ₹0. A free-tier limit is not a service-level agreement; providers may change terms, pause services, or suspend exhausted accounts.
- All provider limits and prices are snapshots checked against official documentation on **2026-11-05**. Recheck before provisioning; never enable paid upgrades or services without explicit approval.
- Decisions labelled **approval required** are deliberately unresolved rather than silently assumed.

## Phase 0 completion and later approved work

The requested architecture, domain, data-integrity approach, reservation/payment lifecycles, API catalogue, security controls, cost caveats, tests, and twenty-phase roadmap are documented. Phase 1 foundations did not include working booking, operational actions, live payment, production deployment, or real data. Phase 2 adds identity; Phase 3 adds master data; Phase 4 adds read-only timetable search. Booking, availability, and payments remain out of scope. This does not constitute a security review, capacity certification, legal opinion, or production-readiness approval.
