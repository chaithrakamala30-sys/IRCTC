# Backend Production Deployment Runbook

## 1. Application Packaging & Containerization
- **Runtime**: Java 21 LTS, Spring Boot 3.
- **Port**: 8080.
- **Build Tool**: Gradle (`./gradlew bootJar`).
- **Container**: Multi-stage Dockerfile running as non-root user `railconnect`.

## 2. Health & Readiness Verification
- Public Health: `GET /actuator/health` returns `{"status":"UP"}`.
- Detailed Health: `GET /api/v1/health` verifies database connectivity and connection pool status without exposing credentials.
- Graceful Shutdown: Enabled with 30s timeout allowing in-flight reservation transactions to conclude cleanly.
