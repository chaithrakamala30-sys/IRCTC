# Security audit logging

## Records

Phase 2 stores security audit records in `security_audit_logs` and authentication events in `authentication_events`. Records carry a UUID, event type, success/failure outcome, correlation ID, timestamp, and available actor/target identity references. Authentication events can also be associated with an account and a hashed identifier/network address. Indexes support time-ordered and per-user review.

Audited actions include registration, login outcomes, logout, profile/password changes, session revocation, staff provisioning, account status changes, and role changes. Administrator APIs provide paginated security-audit and per-user authentication-event views behind the corresponding permissions.

## Data minimization

Raw passwords and session cookie values must never be written to logs or audit metadata. Identifier and remote-address values are hashed with a keyed hash before authentication/throttle persistence. The stable `RAILCONNECT_AUTH_THROTTLE_KEY` must be protected like a secret; do not log it or store it in source control.

The audit schema is an application-level record, not a tamper-proof external ledger. Retention, archival, alerting, privileged audit export, and database administrator separation are not configured in Phase 2. Establish and test those controls before production use.
