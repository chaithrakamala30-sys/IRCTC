# MFA and recovery limitations

## Current behavior

MFA is **not implemented**. Authenticator enrollment, code verification, disablement, and recovery-code API paths return `501 MFA_NOT_AVAILABLE`. The V2 schema contains `mfa_credentials` and `recovery_codes` tables as future groundwork only; no TOTP secret encryption, provisioning URI, verifier, recovery-code generator, or key rotation is active.

Password reset/recovery is also unavailable. The public forgot-password endpoint returns a generic response and does not identify accounts, mint reset tokens, or claim to send an email/SMS. The reset-token table is unused.

## Why these features are deferred

There is no approved delivery channel for one-time links/codes. The implementation therefore avoids false delivery claims and does not provide a weak fallback such as disclosing account existence. Staff account provisioning uses a temporary password and forces a password change at first sign-in instead.

## Before enabling

An approved follow-up must define the delivery provider and costs, user verification and enrollment UX, encrypted secret storage and key custody, rate limits, recovery and revocation policy, staff MFA requirements, audit events, and end-to-end tests. Do not infer readiness from the presence of schema tables or API paths.
