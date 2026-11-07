# Password security

## Password storage and validation

- Passwords are encoded with Spring Security's Argon2id `Argon2PasswordEncoder` defaults.
- New passwords must contain at least 12 characters, satisfy the configured character-class policy, and avoid the small built-in common-password blocklist.
- Password changes require the current password, enforce the same policy, and reject reuse of the current password.
- Passwords are never returned by the API or stored in audit records. The API requires TLS outside local development.

The encoded Argon2 value and work factors depend on the deployed Spring Security defaults. A production workload-specific Argon2 benchmark has not been performed.

## Login protections

Login accepts normalized email or mobile identifiers. Five failed attempts within a 15-minute window block that identifier for 15 minutes. A successful login clears the corresponding throttle. Errors do not disclose whether an account exists or is inactive.

Registration is limited to five attempts per hashed remote address within a 15-minute window. The address and login identifier are HMAC-hashed before storage; configure a stable secret through `RAILCONNECT_AUTH_THROTTLE_KEY` for persistent environments.

## Bootstrap account

The bootstrap process is explicitly opt-in with `--railconnect.bootstrap-super-admin=true` and the `RAILCONNECT_BOOTSTRAP_SUPER_ADMIN_*` environment variables. It exits after creating the account. The initial account must change its temporary password before using other protected functions. Run it once in a controlled environment and remove the bootstrap values immediately afterwards.

## Recovery boundary

Self-service recovery is unavailable because no approved email/SMS delivery channel exists. The forgot-password endpoint records the attempt and returns a generic acknowledgement; it does not issue a token or send a message. See [MFA and recovery limitations](mfa-and-recovery-limitations.md).
