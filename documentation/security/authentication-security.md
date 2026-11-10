# Authentication Security & Session Management

## 1. Password Storage & Cryptographic Hashing
- Algorithm: **Argon2id** (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`).
- Cost parameters conform to OWASP Password Storage Cheat Sheet.
- Plaintext passwords are never logged, stored, or echoed across error logs.

## 2. Session Security
- **Session ID Rotation**: Session identifiers are rotated upon successful authentication via `changeSessionId()` to neutralize Session Fixation attacks.
- **Cookie Flags**:
  - `HttpOnly`: Session cookies cannot be accessed via JavaScript `document.cookie`.
  - `SameSite=Lax`: Protects against Cross-Site Request Forgery (CSRF).
  - `Secure`: Transmitted solely over HTTPS in production.
- **Session Revocation**: User sessions tracked in `user_sessions` and revocable immediately upon logout or password reset.

