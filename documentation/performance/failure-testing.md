# Failure & Degraded-Mode Testing

## 1. Simulated Fault Scenarios

| Fault Injected | Observed System Behavior | Integrity Status |
| :--- | :--- | :---: |
| **Redis Cache Outage** | Backend falls back to PostgreSQL queries automatically. Latency increases by ~40ms; zero errors. | **Preserved** |
| **Dhruva Bank Timeout** | Booking remains pending; hold locks kept until 10m TTL expires. Zero duplicate charges. | **Preserved** |
| **Database Lock Contention** | Concurrent reservation requests for same berth trigger immediate safe conflict rejection. | **Zero Overselling** |
| **High API Traffic Spike** | Requests queued gracefully up to connection pool capacity; excess rejected with 503. | **Protected** |

