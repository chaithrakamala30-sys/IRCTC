# Administrator access control

## Initial administrator

The first Super Administrator is created only by the opt-in bootstrap runner after V2 has seeded roles and permissions. Required identity/password values are supplied through environment variables; the runner exits after creation. The initial password must be changed before accessing other protected API functions. There is no default or committed administrator credential.

## Admin identity console

The separate admin portal provides a sign-in surface and identity-management screens. Backend permissions, not client-side route visibility, govern access to:

- Paginated account search and account details.
- Staff account provisioning with temporary passwords and role grants.
- Account status changes and role replacement.
- User session listing and revocation.
- Authentication-event and security-audit review.
- Role and permission catalogue viewing.

Provisioning, role changes, status changes, and administrator-initiated session revocation require reauthentication within five minutes. Sensitive operations are audited. The API prevents removal or deactivation of the final active Super Administrator.

## Scope limitations

Phase 2 establishes staff identity and coarse permissions only. It does not implement approval workflows, railway catalogue operations, station operations, reservation adjustments, financial actions, or customer-support access to passenger bookings. Resource scopes are represented in the database, but future operational APIs must enforce them individually. MFA is not available; do not treat this portal as production-ready for privileged operations.
