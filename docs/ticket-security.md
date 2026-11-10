# Ticket Security Controls

- **Issuance gate:** only independently verified payment success, final booking confirmation, and confirmed allocations for all booking passengers permit issuance. Browser redirects are not trusted.
- **Idempotency:** unique booking-to-ticket and PNR constraints, locked issuance jobs, immutable PNR triggers, and replay checks prevent duplicate tickets and PNR changes.
- **Owner authorization:** every passenger ticket, PNR search, booking-ticket lookup, and PDF route scopes by the authenticated owner. Staff access uses dedicated ticket permissions.
- **Public QR privacy:** signed Ed25519 tokens contain only a random handle and expiry. Public responses omit names, berths, contact information, internal IDs, and payment references.
- **Forgery, expiry, revocation:** token signature, key ID, expiry, persisted handle, revocation timestamp, and current ticket status are checked. Invalid tokens do not reveal ticket existence.
- **Abuse controls:** public verification rate-limits by a SHA-256 hash of the requester address. Manual PNR verification requires an operational authority; passenger PNR lookup is owner-scoped.
- **Audit:** staff lookups, passenger reads, public/operations verification, PDF attempts, issuance, and retries are recorded with request correlation IDs. Issue history is append-only.
- **PDF:** server records are authoritative; downloads are owner-authorized and non-cacheable. No bank credentials, secrets, internal IDs, or private signing material are included.
- **Secrets:** signing keys are injected through deployment configuration, not committed, persisted in the database, or exposed to browser code.

The RailConnect PNR and ticket are independent demo records, not official Indian Railways or IRCTC records. Cancellation and refund handling are deliberately excluded from this phase.

## Key setup

Create Ed25519 PKCS#8 private and X.509 public DER values in a controlled environment and inject their base64-encoded values into the deployment secret manager. For example, with OpenSSL:

```sh
openssl genpkey -algorithm ED25519 -out ticket-signing-private.pem
openssl pkcs8 -topk8 -nocrypt -in ticket-signing-private.pem -outform DER | base64 -w0
openssl pkey -in ticket-signing-private.pem -pubout -outform DER | base64 -w0
```

The first base64 output is `RAILCONNECT_TICKET_SIGNING_PRIVATE_KEY`; the second is `RAILCONNECT_TICKET_VERIFICATION_PUBLIC_KEY`. Set a non-secret `RAILCONNECT_TICKET_SIGNING_KEY_ID`. Never print these outputs into shared logs or commit them. The current implementation is configured for one active key pair; coordinate rotation with token expiry.
