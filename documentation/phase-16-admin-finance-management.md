# Phase 16: Administration, Finance & Management Control Centre Architecture

## 1. Overview
Phase 16 builds the executive management, staff administration, finance settlement, and maker-checker governance layer for RailConnect. It links platform operations, reservation engines, and customer support with Dhruva Bank demonstration settlement.

## 2. Key Architecture Pillars
- **Executive Administration Dashboard**: Consolidated real-time metrics spanning passengers, active bookings, RAC/Waitlist status, Dhruva Bank demo transaction volume, train run delays, disruptions, and open support tickets.
- **Granular Staff Administration**: Provisioning staff profiles linked to six distinct departments (`ADMINISTRATION`, `FINANCE`, `RESERVATIONS`, `CUSTOMER_SUPPORT`, `OPERATIONS`, `SECURITY`).
- **Maker-Checker Approval Engine**: Four-eye principle implementation requiring independent checker reviews for fare adjustments, policy changes, staff privilege elevation, high-value refunds, and runtime configurations. Self-approval is blocked at the database and application levels.
- **Finance & Reconciliation Centre**: Automated reconciliation comparing RailConnect transactions with Dhruva Bank demonstration ledger records, discrepancy tracking (`MATCHED`, `AMOUNT_MISMATCH`, `STATUS_MISMATCH`), and CSV export.
- **Auditing & System Configuration**: Append-only security audit log and permission-controlled feature flags (`MAINTENANCE_MODE_ENABLED`, `PASSENGER_REGISTRATION_ENABLED`, `BOOKING_SERVICE_ENABLED`, etc.).

