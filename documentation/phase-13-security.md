# Phase 13 Security, Access Controls & Station-Level Scoping

This document specifies the security policies, permissions, station-level authorizations, and audit safeguards implemented in RailConnect's Railway Operations & Control Centre.

---

## 1. Role-Based Access Control (RBAC)

The following granular operations permissions are defined and enforced at both API and service layers:

| Permission Code | Description | Authorized Roles |
| :--- | :--- | :--- |
| `operations:control` | Full operations dashboard and network-wide monitoring | `SUPER_ADMIN`, `OPERATIONS_MANAGER`, `RAILWAY_ADMIN` |
| `operations:station:update` | Record arrival, departure, and passing updates | `SUPER_ADMIN`, `OPERATIONS_MANAGER`, `STATION_MANAGER`, `RAILWAY_ADMIN` |
| `operations:platform:manage` | Manage station platforms and assign track slots | `SUPER_ADMIN`, `OPERATIONS_MANAGER`, `STATION_MANAGER`, `RAILWAY_ADMIN` |
| `operations:disruption:manage` | Report delays, temporary halts, diversions, cancellations | `SUPER_ADMIN`, `OPERATIONS_MANAGER`, `RAILWAY_ADMIN` |
| `operations:incident:manage` | Create, escalate, and resolve operational incidents | `SUPER_ADMIN`, `OPERATIONS_MANAGER`, `STATION_MANAGER`, `RAILWAY_ADMIN` |
| `operations:announcement:publish`| Publish station and train bulletins | `SUPER_ADMIN`, `OPERATIONS_MANAGER`, `STATION_MANAGER`, `RAILWAY_ADMIN` |
| `operations:tracking:read` | Read internal tracking and route progress telemetry | Public / All Authenticated Roles |

---

## 2. Station-Level Scoping

- **Station Managers & Staff**: Restricted to reporting operational updates and managing platforms for their assigned `station_id` (captured in `station_platforms` and `station_operational_updates`).
- **Cross-Station Protection**: Operations Controllers and Super Admins hold global scope, while Station Managers can only assign platforms or author station notices for stations within their jurisdiction.

---

## 3. Sensitive Data Redaction for Public Passenger Tracking

Public passenger tracking endpoints (`GET /api/v1/tracking/trains/{trainNumber}`) strictly omit:
1. Operational crew and driver notes (`assigned_crew_notes`).
2. Internal incident logs and investigative details (`resolution_notes`, internal priority rankings).
3. Staff user IDs and reported audit identifiers.
4. Concurrency lock identifiers and intermediate signal communication remarks.

---

## 4. Concurrency & Overlap Protection

- **Platform Assignments**: Validated within database transactions using `SELECT ... FOR UPDATE` on `platform_assignments` to prevent two trains from occupying the same platform slot simultaneously.
- **Station Operational Updates**: Enforced via database unique constraint on `(train_run_id, station_id, event_type)` to eliminate duplicate race conditions.
