# Redis & Multi-Layer Caching Strategy

## 1. Role of Redis / Upstash
- **Optional Performance Accelerator**: Used for high-volume, low-volatility public queries (station autocomplete, schedule snapshots).
- **Graceful Fallback**: If Redis is offline or reaches free-tier limits, backend services failover directly to PostgreSQL without throwing runtime exceptions.

## 2. Cached Entities & TTLs
- Station Autocomplete (`stations:query:{prefix}`): TTL 1 hour.
- Static Train Schedule (`train:schedule:{number}`): TTL 24 hours.
- Authoritative seat booking and payments are **never cached**; they always transact directly against PostgreSQL.

