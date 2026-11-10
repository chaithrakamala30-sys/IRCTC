# Admin Role and Permission Matrix

| Permission Code | Description | SUPER_ADMIN | RAILWAY_ADMIN | FINANCE_ADMIN | CUSTOMER_SUPPORT |
| :--- | :--- | :---: | :---: | :---: | :---: |
| `admin:dashboard:view` | View executive metrics and platform KPIs | Yes | Yes | Yes | No |
| `admin:users:manage` | Search, suspend, and restore passenger accounts | Yes | Yes | No | Read-only |
| `admin:staff:manage` | Create staff accounts and assign departments | Yes | No | No | No |
| `admin:roles:manage` | Grant and revoke granular permissions | Yes | No | No | No |
| `admin:approvals:create` | Submit maker requests for sensitive changes | Yes | Yes | Yes | Yes |
| `admin:approvals:review` | Review and approve/reject maker-checker requests | Yes | Yes | Yes | No |
| `admin:finance:view` | Inspect transaction ledgers and financial volumes | Yes | No | Yes | No |
| `admin:finance:reconcile` | Execute and review Dhruva Bank reconciliation | Yes | No | Yes | No |
| `admin:reports:export` | Download CSV analytics and transaction dumps | Yes | Yes | Yes | No |
| `admin:audit:view` | Audit log inspection and security monitoring | Yes | Yes | No | No |
| `admin:config:manage` | Modify system configurations and feature flags | Yes | No | No | No |

