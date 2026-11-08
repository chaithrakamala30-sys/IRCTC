# Fare-engine performance notes

The quote path reuses Phase 4's indexed journey lookup and obtains its route-derived distance from the backend. Fare rules use a composite selection index over status, effective dates, train, category, class, and priority. Rule components, slabs, and passenger adjustments are fetched in bounded queries; repeated passengers in the same category share category lookup work. Admin options and rule lists are bounded to 500 records.

The fare calculation itself is linear in passenger and configured component counts, with stable sorting by component order/code. User-configurable lists are bounded to 100 slabs, 100 components, and 100 category adjustments per version; quote requests are bounded to 20 passengers. Decimal calculations are deterministic but have not been load-tested.

No latency, throughput, PostgreSQL plan, or production-scale capacity claim is made. Docker and a PostgreSQL URL were unavailable locally. Before production, run PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` on representative data, measure quote selection and route lookup, test concurrent publication, and define an approved latency target.
