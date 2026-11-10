# Disaster Recovery & Service Continuity Report

## 1. Scenario Testing

| Fault Condition | Injected Event | Recovery Outcome |
| :--- | :--- | :--- |
| **Backend Service Restart** | JVM terminated mid-hold | Active holds recovered or expired safely via TTL; zero corruption. |
| **Database Network Blip** | Connection pool timeout | HikariCP reconnects automatically; pending queries retry or return clean 503. |
| **Delayed Payment Callback** | Webhook arrives after redirect | Webhook processed idempotently; booking transitions safely to `CONFIRMED`. |
| **Redis Outage** | Cache node unavailable | Backend falls back seamlessly to PostgreSQL queries without crashing. |

## 2. Recovery Objectives
- **RPO (Recovery Point Objective)**: $\le 24$ hours (nightly snapshots).
- **RTO (Recovery Time Objective)**: $\le 30$ minutes (automated CI/CD rebuild and redeploy).
