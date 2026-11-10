# Authorization Matrix & Server-Side RBAC

## Default Deny Architecture
Spring Security configuration specifies `.anyRequest().authenticated()`. Public routes are limited to registration, login, and public schedule queries.

## Granular Role Allocations

| Role | Permitted Domain Actions |
| :--- | :--- |
| **PASSENGER** | View own bookings, own tickets, submit own complaints, submit post-resolution feedback. Cannot view admin queues. |
| **CUSTOMER_SUPPORT** | View support queues, respond publicly, post confidential internal notes, escalate tickets. Cannot approve refunds or modify fares. |
| **FINANCE_ADMIN** | Inspect demo payment ledger, execute daily Dhruva Bank reconciliation, export financial CSVs. |
| **RAILWAY_ADMIN** | Manage stations, trains, routes, inventory schedules, approve maker requests. |
| **SUPER_ADMIN** | Global governance, staff provisioning, system configuration overrides, security audit inspection. |
