# Workload Model & Traffic Distribution

## 1. Workload Categorization
- **Browse & Search Traffic (70%)**: Station queries, schedule lookups, fare checks.
- **Reservation Traffic (15%)**: Inventory holds, seat assignments, booking confirmations.
- **Payment & Webhook Traffic (10%)**: Dhruva Bank demonstration callbacks and refund settlements.
- **Administration & Operational Traffic (5%)**: Dashboard metrics, OCC updates, support triage.

## 2. Concurrency vs. Virtual Users
- **Virtual Users (VUs)**: Simulated clients in k6 scripts executing requests with modeled think time (1–2 seconds).
- **Concurrent Active Requests**: Actual simultaneous connections hitting the Spring Boot application and HikariCP connection pool ($\le 15$ concurrent transactions).

