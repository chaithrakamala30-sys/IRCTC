# Production Incident Response & Maintenance Playbook

## 1. Incident Severity & Escalation
- **SEV-1 (Critical Outage)**: Booking engine failure, database crash, or active security exploit.
- **SEV-2 (Degraded Performance)**: High latency on search, delayed Dhruva Bank callbacks.
- **SEV-3 (Minor)**: Cosmetic UI issue, non-critical telemetry failure.

## 2. Emergency Maintenance Mode
- **Activation**: Execute `POST /api/v1/admin/configurations/MAINTENANCE_MODE_ENABLED` with `configValue: "true"`.
- **Effect**: Halts new booking attempts gracefully; active in-flight transactions are allowed to conclude; displays user-friendly maintenance banner.

