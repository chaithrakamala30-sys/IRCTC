# Security Assessment & Vulnerability Review

## 1. Baseline Review
- **Framework**: OWASP Top 10 (2021) & OWASP ASVS v4.0.3.
- **Tested Scope**:
  - `apps/passenger-web`: Routes, components, API client, push workers.
  - `apps/admin-portal`: Maker-checker console, financial reconciliation, staff profiles.
  - `backend/railconnect-core`: Spring Boot controllers, JDBC client queries, security filters.
  - `database`: PostgreSQL migrations `V1` to `V20`.

## 2. Assessment Results
- **SQL Injection**: None identified. All dynamic queries use Spring `JdbcClient.param()` with bind variables.
- **Cross-Site Scripting (XSS)**: None identified. Next.js automatic JSX escaping prevents DOM injection. Strict CSP header added.
- **Insecure Direct Object Reference (IDOR)**: Addressed. All passenger endpoints enforce `user_id = principal.userId()`.
- **Cross-Site Request Forgery (CSRF)**: Mitigated using Spring Security `CookieCsrfTokenRepository.withHttpOnlyFalse()` with Lax SameSite cookies.
- **Credentials & Secrets**: No production secrets or private keys committed to Git. Demo banking URLs clearly marked as non-monetary.

