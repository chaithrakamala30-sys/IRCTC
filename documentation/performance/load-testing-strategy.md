# Load Testing Strategy

## 1. Tool Selection
- **k6 (v0.50+)**: Open-source, developer-centric, low-resource load generator executed locally or via container.
- Zero-cost execution: No cloud test runner fees, no third-party subscription overhead.

## 2. Test Execution Tiers
1. **Smoke Test** (`smoke-test.js`): 10 VUs for 15s verifying core endpoint health.
2. **Browse & Search** (`browse-and-search.js`): 25 to 50 VUs exercising read-heavy public endpoints.
3. **High-Concurrency Reservation** (`concurrent-booking.js`): 20 to 40 VUs competing for seat inventory and fare quotes.
4. **Stress & Spike**: Ramp-up past nominal limits to observe graceful degradation.
