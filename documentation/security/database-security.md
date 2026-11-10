# Database Security & Data Protection

## 1. Access Controls & Connections
- PostgreSQL access is gated behind managed credentials with SSL connections (`sslmode=require`).
- Direct public access to the production database is prohibited; only backend services can establish connection pools.

## 2. Integrity Constraints & Audit Trails
- Critical business integrity is enforced at the database schema level via foreign keys and check constraints (`chk_no_self_approval`, `chk_approval_status`).
- Auditing: All status changes, staff assignments, and administrative actions are logged in immutable append-only tables (`support_ticket_status_history`, `security_audit_logs`).
