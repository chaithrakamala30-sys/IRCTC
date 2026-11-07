# Role and permission matrix

Permissions are stored as explicit `role_permissions` rows. The migration seeds the Phase 2 catalogue; server-side method security checks permission codes on protected admin routes.

| Role | Seeded permissions |
|---|---|
| `PASSENGER` | `identity:self:read`, `identity:self:update`, `identity:self:password`, `identity:self:sessions` |
| `SUPER_ADMIN` | All Phase 2 permissions |
| `RAILWAY_ADMIN` | `operations:catalogue:manage`, `identity:roles:read` |
| `STATION_MANAGER` | `operations:station:read`, `operations:station:manage` |
| `RESERVATION_OFFICER` | `reservations:manage` |
| `FINANCE_ADMIN` | `finance:reconciliation:read` |
| `CUSTOMER_SUPPORT` | `support:bookings:investigate` |

## Permission catalogue

| Permission | Intended access |
|---|---|
| `identity:self:read` | Read own profile |
| `identity:self:update` | Update allowed own profile fields |
| `identity:self:password` | Change own password |
| `identity:self:sessions` | List/revoke own sessions |
| `identity:users:read` | Read identity account records |
| `identity:users:create` | Provision staff accounts |
| `identity:users:status` | Change account status and revoke account sessions |
| `identity:users:roles` | Assign identity roles |
| `identity:roles:read` | Read role and permission catalogue |
| `security:audit:read` | Read security audit records |
| `operations:catalogue:manage` | Intended future catalogue management |
| `operations:station:read` / `operations:station:manage` | Intended future scoped station operations |
| `reservations:manage` | Intended future reservation operations |
| `finance:reconciliation:read` | Intended future reconciliation view |
| `support:bookings:investigate` | Intended future support investigation |

## Scope and limitations

`user_roles` supports `GLOBAL` and resource-specific scopes. Phase 2 admin identity routes require permission codes; the non-identity railway permissions are only a catalogue for future phases and do not authorize any nonexistent domain endpoints. Resource scope checks must be implemented and tested alongside each future domain API.

Role replacement and account status changes revoke the target's active sessions. The service protects the last active Super Administrator against accidental deactivation or removal of the Super Administrator role.
