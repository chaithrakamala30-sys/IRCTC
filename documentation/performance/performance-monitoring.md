# Performance Monitoring & Observability

## 1. Metrics Collection
- **Spring Boot Actuator**: Exposes `/actuator/health` and `/actuator/metrics` for heap, garbage collection, and database connection statistics.
- **Structured Correlation**: Every request stamped with `X-Request-Id` and tracked across services.

## 2. Telemetry Privacy
- Telemetry logs explicitly filter out sensitive parameters, avoiding collection of passwords, session cookies, or passenger personal data.
