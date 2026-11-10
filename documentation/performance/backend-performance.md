# Backend Performance & JVM Tuning

## 1. HikariCP Connection Pool Optimization
- `maximum-pool-size`: 15 (fits within free cloud PostgreSQL connection limits).
- `minimum-idle`: 5.
- `connection-timeout`: 30,000 ms.
- `idle-timeout`: 600,000 ms.
- `max-lifetime`: 1,800,000 ms.
- Prepared statement caching enabled for PostgreSQL JDBC driver.

## 2. JVM & Garbage Collection
- Memory limits tuned for container limits ($\le 512$ MB on free container hosting).
- G1 Garbage Collector configured with target pause times $< 50$ ms.
- Asynchronous non-blocking dispatch for Phase 14 passenger notification fan-out.

