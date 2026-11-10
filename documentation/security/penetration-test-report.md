# Penetration Test & Vulnerability Assessment Report

## 1. Testing Summary
- **Target**: RailConnect Core Backend, Passenger Web, Admin Portal.
- **Testing Methodology**: Gray-box authorized penetration testing following OWASP Testing Guide (OTG v4.2).

## 2. Test Execution & Findings

| Test Case | Attack Vector | Result | Verification Notes |
| :--- | :--- | :---: | :--- |
| **TC-01: IDOR on Ticket Details** | Replaced ticket ID with another user's UUID | **BLOCKED** | API returned HTTP 403 / 404; query enforces `customer_id = principal.userId()`. |
| **TC-02: Maker-Checker Self-Approval** | Proposer approved their own fare reduction | **BLOCKED** | Threw `SecurityException: Maker-checker violation`. |
| **TC-03: SQL Injection via Search** | Injected `' OR 1=1 --` into train query | **BLOCKED** | Handled safely by bind variables in `JdbcClient`. |
| **TC-04: XSS via Support Message** | Injected `<script>alert(1)</script>` | **BLOCKED** | Next.js JSX escaped tags; CSP blocked script execution. |
| **TC-05: Brute Force on Admin Login** | Fired 25 consecutive incorrect passwords | **THROTTLED** | Account locked out temporarily by `login_throttles`. |

