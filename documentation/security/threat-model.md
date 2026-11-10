# Threat Model & STRIDE Assessment

## 1. STRIDE Analysis

| STRIDE Threat | Target Asset | Vulnerability / Threat Vector | Implemented RailConnect Defense |
| :--- | :--- | :--- | :--- |
| **Spoofing** | Passenger & Staff Identities | Credential stuffing, session hijacking | Argon2id password hashing, session rotation upon login, secure cookies. |
| **Tampering** | Bookings, Fares & System Flags | Direct parameter tampering, self-approval | Maker-Checker independent review, database constraints, server-side price calculation. |
| **Repudiation** | Financial actions & Permissions | Denying having executed refunds or role changes | Immutable `authentication_events` and `security_audit_logs` tracking user IDs and timestamps. |
| **Information Disclosure** | Passenger PII & Support Notes | IDOR on booking/ticket endpoints, internal staff notes | Strict ownership queries (`user_id = principal.userId()`), `is_internal_note = false` filter. |
| **Denial of Service** | Reservation holds & Login | Automated seat locking, brute-force login | Redis TTL hold expiration (10 mins), rate-limiting login throttles. |
| **Elevation of Privilege** | Administrative portals | Role manipulation via API requests | Default-deny `@PreAuthorize` authorization, server-side role validation. |

