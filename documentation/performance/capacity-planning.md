# Capacity Planning & Scalability Modeling

## 1. Workload Scenarios
- **Free-Tier Staging**: Supports up to 50 concurrent active users and ~35 req/s sustained without incurring paid service charges.
- **Production Baseline (1-2 Container Instances)**: Supports up to 250 concurrent users and ~120 req/s.
- **High-Demand Tatkal / Rush Spike**: Requires horizontal scaling across 4-6 container replicas and dedicated database connection pooling (e.g. PgBouncer).

## 2. Bottleneck Analysis
- Initial bottleneck under extreme load is database connection pool exhaustion ($\ge 60$ concurrent booking transactions).
- Handled safely via HTTP 429 / 503 backpressure and client-side retry-after prompts.
