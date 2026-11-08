# Train-search performance and operational limits

## Implemented controls

- Station autocomplete is limited to 20 suggestions and queries of 2–80 characters.
- Search validates IDs, date horizon, filters, sort key, page number, and page size before querying.
- Page size is limited to 100 and page number to 10,000.
- Read-only service transactions use a five-second timeout.
- The result page loads schedule weekdays and coach classes in bounded batch queries rather than per-result lookups.
- V6 adds a partial active-station code index, a GIN search-vector index, a published-schedule lookup index, and a route-stop station/version index.

## Not measured

No PostgreSQL query plan, realistic data-volume benchmark, concurrency/load test, p95 latency, or cache hit-rate has been measured. V6 has not been applied locally. Do not claim production capacity or a latency objective from the limits above; collect `EXPLAIN (ANALYZE, BUFFERS)` and representative load evidence against an isolated, appropriately sized PostgreSQL environment before capacity decisions.

## Scaling posture

PostgreSQL remains authoritative, and every search performs a count query plus a result query. The current implementation does not add Redis, an external search index, paid service, or unbounded background work. Revisit query plans and indexes before increasing the horizon or page-size limits.
