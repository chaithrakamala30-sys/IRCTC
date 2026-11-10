# Backup, Restoration & Disaster Recovery Runbook

## 1. Backup Schedule & Strategy
- **PostgreSQL Snapshots**: Daily automated cloud database snapshots retained for 7 days.
- **Migration Redundancy**: Full schema re-creation possible at any time via versioned Flyway scripts `V1`–`V21`.

## 2. Restoration Testing
- Rehearsed restoration verified foreign key integrity, user identities, and booking history without data loss.
- **Target RPO**: $\le 24$ hours.
- **Target RTO**: $\le 30$ minutes.

