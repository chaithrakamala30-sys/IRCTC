# Role and permission matrix

Permissions are stored as explicit `role_permissions` rows. V2 seeds identity permissions; V5 appends master-data permissions. Server-side method security checks permission codes on protected admin routes.

| Role | Seeded permissions |
|---|---|
| `PASSENGER` | `identity:self:read`, `identity:self:update`, `identity:self:password`, `identity:self:sessions` |
| `SUPER_ADMIN` | All Phase 2 and Phase 3 permissions |
| `RAILWAY_ADMIN` | Phase 2 grants plus `operations:catalogue:manage`, `identity:roles:read`, and the Phase 3 master-data permissions below |
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
| `operations:catalogue:manage` | Legacy catalogue permission; Phase 3 APIs use narrower permissions |
| `operations:station:read` / `operations:station:manage` | Legacy scoped station permissions; they do not grant Phase 3 master-data access |
| `operations:master-data:read` | Read railway master-data records and dashboard |
| `operations:stations:manage` | Create/update stations, verify station codes, and change station status |
| `operations:trains:manage` | Manage train definitions and category/type records |
| `operations:routes:manage` | Create route versions |
| `operations:schedules:manage` | Create schedule versions and exceptions |
| `operations:composition:manage` | Manage coach classes/types, configurations, layouts, coach units, and compositions |
| `operations:master-data:publish` | Publish routes, schedules, coach layouts, and compositions |
| `operations:master-data:audit:read` | Read master-data audit history |
| `reservations:manage` | Intended future reservation operations |
| `finance:reconciliation:read` | Intended future reconciliation view |
| `support:bookings:investigate` | Intended future support investigation |

## Scope and limitations

`user_roles` supports `GLOBAL` and resource-specific scopes. Phase 3 master-data endpoints currently rely on the explicit global `RAILWAY_ADMIN`/`SUPER_ADMIN` grants and do not apply station-specific resource scopes. `STATION_MANAGER` receives no Phase 3 global write permissions by default. Any future scoped station APIs must add and test resource-scope enforcement rather than relying on these permission codes alone.

Role replacement and account status changes revoke the target's active sessions. The service protects the last active Super Administrator against accidental deactivation or removal of the Super Administrator role.
