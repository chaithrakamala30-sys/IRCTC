# Production Monitoring, Logging & Observability

## 1. Zero-Cost Observability Stack
- **Spring Boot Actuator**: Provides real-time metrics for JVM memory, connection pools, and thread allocation via `/actuator/metrics`.
- **Structured JSON Logs**: Logs output with ISO 8601 timestamps, request correlation IDs (`X-Request-Id`), and log levels (`INFO`, `WARN`, `ERROR`).

## 2. Redaction & Privacy Safeguards
- PII (passwords, card CVVs, net banking MPINs, bearer tokens) are explicitly redacted before reaching stdout or disk.

