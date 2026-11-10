# Phase 15: Customer Support API Reference

## Passenger Endpoints
- `GET /api/v1/support/categories`: Fetch active complaint categories.
- `POST /api/v1/support/tickets`: Create new ticket (links optional booking, payment, PNR, train run).
- `GET /api/v1/support/tickets`: List authenticated user's tickets.
- `GET /api/v1/support/tickets/{ticketId}`: Fetch ticket details and public conversation history.
- `POST /api/v1/support/tickets/{ticketId}/messages`: Send passenger reply.
- `POST /api/v1/support/tickets/{ticketId}/feedback`: Submit CSAT satisfaction score (1-5) and review.

## Staff & Admin Endpoints
- `GET /api/v1/support/staff/tickets`: Staff workspace queue with filter tabs (`ALL`, `MY_ASSIGNED`, `UNASSIGNED`, `ESCALATED`, `HIGH_CRITICAL`).
- `POST /api/v1/support/staff/tickets/{ticketId}/messages`: Post staff response (`isInternalNote: false` for passenger, `true` for confidential staff note).
- `POST /api/v1/support/staff/tickets/{ticketId}/assign`: Assign ticket to an agent or team.
- `POST /api/v1/support/staff/tickets/{ticketId}/status`: Update ticket lifecycle status.
- `POST /api/v1/support/staff/tickets/{ticketId}/escalate`: Escalate ticket to a higher tier with rationale.
- `GET /api/v1/support/admin/dashboard`: Metrics on open tickets, breach counts, resolved volumes, and average CSAT score.
