# RailConnect — project overview

## Purpose

RailConnect is an independent railway reservation and management platform for an initial catalogue of trains and inventory owned and controlled by RailConnect administrators. It is intended to evolve into a reliable commercial product for India, with INR pricing and Asia/Kolkata as the default operating timezone.

This is not an IRCTC clone, an official Indian Railways service, or an authorized provider integration. Do not use official names, logos, policy claims, inventory, or APIs without permission. Railway rules and fares must be entered as approved, versioned configuration with provenance.

## Phase 0 and Phase 1 status

The repository was initially empty and Phase 0 established the architecture documentation. The user has now explicitly approved Phase 1 foundation work. This does not authorize booking behavior, real/simulated train inventory, live payments, third-party railway integration, or deployment to a paid service. Stop after Phase 1 and wait for explicit approval before Phase 2.

## Product scope

### Initial product capabilities

- Passenger discovery of RailConnect-managed stations, train schedules, fares, and availability.
- Authenticated passenger profiles, journey booking, ticket / PNR lookup, cancellation, refund status, and support.
- Administrative management of stations, routes, schedules, trains, coaches, seats, inventory, fares, users, permissions, and audit history.
- Operational management of train status, disruptions, cancellations, and passenger announcements.
- Payment-provider abstraction; test payments are explicitly simulated and never represented as real financial transactions.

### Explicitly not promised

- Official railway inventory or policy parity.
- Live payment processing until a provider, its fees, compliance duties, and operational procedures are approved.
- Commercial production availability on free hosting tiers.
- Capacity for millions of simultaneous successful bookings without measured infrastructure and load-test evidence.

## Architecture decisions at a glance

| Area | Phase 0 decision |
|---|---|
| Application shape | Modular monolith with strict module boundaries; split services only when justified by evidence. |
| Passenger/admin UI | Next.js, React, TypeScript, Tailwind; separate passenger mobile and laptop routes, plus separate admin and operations app foundations. Shared UI components keep behavior consistent. Statically delivered where feasible, with all authoritative decisions on the API. |
| Backend | Java + Spring Boot, Spring Security, REST, OpenAPI; Spring Data JPA for ordinary persistence and explicit SQL where locking / constraints require it. |
| Database | PostgreSQL as the source of truth; Flyway migrations; database constraints and transactions protect reservations and money. |
| Cache / search / messaging | Optional Redis only for disposable acceleration; PostgreSQL indexes/full-text initially; transactional outbox and PostgreSQL-backed jobs before Kafka. |
| Infrastructure | Prefer free tiers for development/demo only, subject to current limits. ₹0 is a strict policy; no paid upgrade, automatic billing, or card-dependent plan activation. |
| Payments | Provider interface + labelled test provider; no raw card data, CVV, or claims that test payment is real. |
| Consistency | Strong consistency for seat allocation, reservation state, payment ledger, refund accounting, and admin changes; eventual consistency for search projections, notifications, and analytics. |
| Operations | Health endpoints, structured logs, audit records, metrics, backups, restore drills, and manual cost controls. |

Rationale and alternatives are in [system architecture](03-system-architecture.md), [zero-cost infrastructure](zero-cost-infrastructure.md), and [risk register](15-risk-register.md).

## Actors and trust boundaries

- **Passenger:** owns their account and may view/manage only their own bookings.
- **Support officer:** handles permitted cases; access to personal data is limited and audited.
- **Reservation officer / station manager:** performs explicitly scoped operational actions.
- **Railway administrator:** manages approved operational configuration.
- **Finance administrator:** reconciles payments and issues authorized refunds; sensitive actions require separation of duties.
- **Super administrator:** manages platform-level permissions and emergency controls; all actions audited.
- **External payment provider:** untrusted callback source; messages must be authenticated, verified, replay-safe, and reconciled.
- **Hosting / database provider:** infrastructure dependency; never a source of business authorization.

## Success measures for later phases

Success is measured against [non-functional requirements](02-non-functional-requirements.md), transaction invariants in [database design](05-database-design.md), and explicit release gates in [implementation roadmap](14-implementation-roadmap.md). Performance goals are provisional until hardware, traffic, and load tests are available.

## Outstanding decisions requiring approval

1. Legal entity, product ownership, customer terms, privacy notice, retention schedule, and applicable compliance review.
2. Initial operating regions, stations, routes, train/run data source, and responsible data approvers.
3. Whether a public demo can use strictly synthetic and visibly labelled data.
4. Account recovery and MFA policy, identity verification scope, and support verification procedure.
5. Payment provider, actual transaction charges, settlement model, refund authority, and launch prerequisites.
6. Whether persistent customer records are acceptable on a free hosted database; provider free tiers do not offer production-grade continuity, backup guarantees, or adequate evidence of durability.
7. Deployment provider selection after a fresh limits/cost review; free-tier availability can change.

## Phase 1 status

Phase 1 was approved and established repository structure, build/test automation, local PostgreSQL/Docker configuration, module boundaries, Flyway baseline, logging, health checks, and dependency/security scanning. Its passenger foundation has distinct `/mobile` and `/laptop` routes with a device-aware root selector; shared presentation components do not duplicate business logic. It did not create live inventory or take payments. See the [roadmap](14-implementation-roadmap.md) and [Phase 1 report](phase-1-infrastructure.md).
