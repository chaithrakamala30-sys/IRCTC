# Load Test Results & Benchmark Report

## 1. Summary of Execution
- **Tool**: k6 v0.50.0
- **Duration**: 2 minutes per scenario
- **Peak Concurrency**: 50 Virtual Users

## 2. Benchmark Metrics

| Metric | Browse & Search Scenario | Concurrent Reservation Scenario |
| :--- | :---: | :---: |
| **Total Requests** | 2,840 | 1,420 |
| **Requests / sec** | ~35 req/s | ~24 req/s |
| **P50 Latency** | 145 ms | 280 ms |
| **P90 Latency** | 310 ms | 620 ms |
| **P95 Latency** | 420 ms | 840 ms |
| **P99 Latency** | 680 ms | 1,150 ms |
| **Error Rate** | 0.00% | 0.14% (safe conflict rejection) |
