# Password security

## Password storage and validation

Supabase Auth is the credential store and verifies email/password sign-ins. The application stores profile and authorization data in `railconnect.users`; `auth_user_id` links a profile to its Supabase Auth identity, and `password_hash` is nullable. New accounts do not store a local password hash.

On the first login for a legacy profile that has no linked Supabase identity, the backend verifies the submitted password against the existing Argon2 hash. If it matches, the backend creates the Supabase Auth identity, stores its ID in `auth_user_id`, and clears the legacy hash. If Supabase requires email confirmation, the account remains linked but login is refused until confirmation. A mismatch never creates or links an Auth identity.

New passwords must contain at least 12 characters, satisfy the configured character-class policy, and avoid the built-in common-password blocklist before they are sent to Supabase Auth. Password changes verify the current password through Supabase Auth and update the Supabase credential through the server-only service-role key.

## Login protections

Login accepts normalized email addresses only. Five failed attempts within a 15-minute window block that identifier for 15 minutes. A successful login clears the corresponding throttle. Errors do not disclose whether an account exists or is inactive.

Registration is limited to five attempts per hashed remote address within a 15-minute window. The address and login identifier are HMAC-hashed before storage; configure a stable secret through `RAILCONNECT_AUTH_THROTTLE_KEY` for persistent environments.

## Bootstrap account

The bootstrap process is explicitly opt-in with `--railconnect.bootstrap-super-admin=true` and the `RAILCONNECT_BOOTSTRAP_SUPER_ADMIN_*` environment variables. It creates the staff identity in Supabase Auth and the corresponding profile/role in the application database, then exits. The initial account must change its temporary password before using other protected functions. Run it once in a controlled environment and remove the bootstrap values immediately afterwards.

## Recovery boundary

Self-service recovery is unavailable until a delivery and redirect flow is configured in Supabase Auth. See [MFA and recovery limitations](mfa-and-recovery-limitations.md).
