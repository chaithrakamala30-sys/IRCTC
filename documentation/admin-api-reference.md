# Admin Control Centre API Reference

All administrative endpoints are secured with Spring Security `@PreAuthorize` and return data wrapped in `ApiEnvelope.success(data, correlationId)`.

## Endpoints

### 1. Executive Dashboard
- `GET /api/v1/admin/dashboard`: Returns platform, finance, and operational KPIs.

### 2. User & Passenger Administration
- `GET /api/v1/admin/users?query={q}&status={s}`: Search passenger accounts.
- `POST /api/v1/admin/users/{userId}/status`: Update passenger account status (`ACTIVE`, `SUSPENDED`, `DISABLED`) with mandatory audit reason.

### 3. Staff & Departments
- `GET /api/v1/admin/staff/departments`: List staff departments.
- `GET /api/v1/admin/staff`: List staff profiles and department assignments.
- `POST /api/v1/admin/staff`: Provision new staff account and profile.

### 4. Maker-Checker Approvals
- `GET /api/v1/admin/approvals?status={status}`: List approval proposals.
- `POST /api/v1/admin/approvals`: Create change proposal (Maker).
- `POST /api/v1/admin/approvals/{requestId}/review`: Review proposal (Checker: approve/reject).

### 5. Finance & Dhruva Bank Reconciliation
- `GET /api/v1/admin/finance/reconciliation`: List reconciliation runs.
- `POST /api/v1/admin/finance/reconciliation/run`: Execute daily reconciliation run.

### 6. Reports & CSV Export
- `GET /api/v1/admin/reports/export?reportType={BOOKINGS|FINANCE|SUPPORT|PASSENGERS}`: Stream downloadable CSV file.

### 7. System Configurations
- `GET /api/v1/admin/configurations`: Fetch operational flags and thresholds.
- `POST /api/v1/admin/configurations/{key}`: Update system parameter with audit justification.
