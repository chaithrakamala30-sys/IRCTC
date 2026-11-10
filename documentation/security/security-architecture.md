# Security Architecture & Trust Boundaries

## 1. Scope & Framework
RailConnect implements a zero-trust multi-portal security architecture adhering to **OWASP Top 10 (2021)** and **OWASP ASVS v4.0.3 Level 2** controls.

## 2. Trust Boundaries
- **Public Zone**: Unauthenticated passengers exploring train schedules, reading fares, or accessing login/register interfaces.
- **Passenger Zone**: Authenticated passengers with session cookies. Access strictly limited to own profile, bookings, tickets, and complaints (`customer_id = principal.userId()`).
- **Staff / Operational Zone**: Provisioned personnel restricted by department and granular role permissions (`CUSTOMER_SUPPORT`, `OCC_CONTROLLER`, etc.).
- **Executive & Administrative Zone**: Privileged management (`SUPER_ADMIN`, `RAILWAY_ADMIN`, `FINANCE_ADMIN`) protected by Maker-Checker four-eye verification and immutable audit logging.
- **External Integration Zone**: Dhruva Bank demonstration gateway for payments and refund confirmations via signed payloads.

## 3. Defense-in-Depth Measures
- Strict HTTPS enforcement in production with HSTS (`max-age=31536000`).
- Content Security Policy (CSP) blocking unauthorized script injection and framing.
- SameSite Lax, HttpOnly, and Secure cookie flags for session tokens and CSRF prevention.
