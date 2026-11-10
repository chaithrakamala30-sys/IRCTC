# Free-Tier Infrastructure Limitations & Operational Constraints

## 1. Provider Tier Audit & Constraints

| Component | Provider / Tier | Quota & Limitations | Operational Impact & Mitigation |
| :--- | :--- | :--- | :--- |
| **Backend API** | Render Free Web Service | Spins down after 15m of inactivity; 512MB RAM cap. | Cold-start latency of ~30–45s on first request. Keep-alive ping jobs prevent idle spin-down. |
| **Frontend Websites** | Vercel Hobby Tier | 100 GB bandwidth / month; fair-use request limits. | More than sufficient for development, staging, and limited pilot demonstrations. |
| **PostgreSQL Database** | Cloud PostgreSQL Free | 20 max connections; 500 MB storage cap. | HikariCP pool strictly capped at 15 connections to prevent connection pool exhaustion. |
| **Payment Gateway** | Dhruva Bank (Vercel) | Demonstration educational platform. | Non-monetary balances; real payment processing is completely disabled. |

## 2. Zero-Cost Policy Enforcement
No credit card has been linked, no paid subscriptions activated, and no automatic paid scaling rules enabled.
