# Ticket API Reference

All JSON responses use the standard RailConnect API envelope. Passenger routes require an authenticated `PASSENGER` with `tickets:own:read`; ownership is always checked by the backend. Staff routes are independently guarded by ticket-specific permissions.

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/api/v1/tickets?page=0&size=20` | Passenger owner | Paginated ticket history |
| GET | `/api/v1/tickets/{ticketId}` | Passenger owner | Ticket details |
| GET | `/api/v1/tickets/pnr/{pnr}` | Passenger owner | Search own ticket by RailConnect PNR |
| GET | `/api/v1/bookings/{bookingId}/ticket` | Passenger owner | Read ticket for own booking |
| GET | `/api/v1/tickets/{ticketId}/download` | Passenger owner | Download generated PDF |
| GET | `/api/v1/ticket-verification/{token}` | Public, rate-limited | Verify an opaque signed QR |
| POST | `/api/v1/ticket-verification/pnr` | `tickets:operations:verify` | Staff PNR verification and limited allocation data |
| GET | `/api/v1/operations/tickets/verify/{token}` | `tickets:operations:verify` | Staff QR verification |
| GET | `/api/v1/admin/tickets?query=...` | `tickets:admin:read` | Search by PNR, ticket UUID, or booking reference |
| GET | `/api/v1/admin/tickets/{ticketId}` | `tickets:admin:read` | Authorized ticket details |
| GET | `/api/v1/admin/tickets/pnr/{pnr}` | `tickets:admin:read` | Authorized PNR lookup |
| GET | `/api/v1/admin/tickets/{ticketId}/diagnostics` | `tickets:admin:read` | Issuance attempts and immutable issue history |
| GET | `/api/v1/admin/tickets/issuance-failures` | `tickets:admin:read` | Pending/retrying/failed issuance jobs without a ticket yet |
| POST | `/api/v1/admin/tickets/issuance-failures/{bookingReference}/retry` | `tickets:admin:retry` | Audited retry of a previously failed issuance job |

`pnr` format is `RC-TK-` plus 16 characters in `[A-Z2-7]`. Passenger and staff searches return a non-disclosing `TICKET_NOT_FOUND` where no accessible record exists. Public token verification returns validity data only and never performs a public PNR lookup. Download responses are private and non-cacheable.

No ticket cancellation, refund, mutation, or arbitrary issuance API is provided.
