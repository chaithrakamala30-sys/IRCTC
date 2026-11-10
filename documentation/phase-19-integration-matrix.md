# Phase 19: System Integration Matrix

## 1. Overview
Maps the end-to-end integration topology across all 14 core modules, defining endpoints, data flows, and verified invariants.

## 2. Comprehensive Module Matrix

| Source Module | Target Module | Protocol & Contract | Verified Invariants |
| :--- | :--- | :--- | :--- |
| **Identity Service** | All Portals | HTTP Session / Cookie | Argon2id hashing, session ID rotation, default-deny RBAC. |
| **Master Data** | Passenger & Admin | REST `/api/v1/public/*` | Dynamic routes, stations, schedule exceptions, zero unbounded scans. |
| **Fare Engine** | Booking Engine | REST `/api/v1/public/fares/quotes` | Immutable fare quote snapshots, distance slabs, discount governance. |
| **Seat Inventory** | Reservation Engine | PostgreSQL Transactions | Segment-level berth allocation, zero overselling, 10m hold lock TTL. |
| **Booking Engine** | Payment Service | REST `/api/v1/payments` | Booking state transitions (`PENDING_PAYMENT` $\rightarrow$ `CONFIRMED`), unique PNR. |
| **Payment Service** | Dhruva Bank Gateway | Signed Webhooks | Timestamp & Idempotency-Key validation, non-monetary demo balances. |
| **Refund Engine** | Dhruva Bank Refund API | REST Merchant Refund | Original payment validation, refund caps, reconciliation state updates. |
| **Operations (OCC)** | Notification Service | Event Outbox Dispatch | Train delays and platform changes fan-out to affected ticket owners. |
| **Support Desk** | Identity & Booking | REST `/api/v1/support/*` | Ticket lifecycle tracking, strict confidential internal note filtering. |
| **Admin Control** | Governance & Finance | REST `/api/v1/admin/*` | Four-eye maker-checker enforcement, daily reconciliation against Dhruva Bank. |

