# Phase 15: Support Ticket Lifecycle & State Machine

## Lifecycle States
1. **OPEN / SUBMITTED**: Ticket created by passenger or agent on customer behalf. SLA response timer starts.
2. **ASSIGNED**: Ticket allocated to a support representative or specialized queue (Ticketing, Refunds, Operations).
3. **IN_PROGRESS**: Active investigation under review by staff.
4. **WAITING_FOR_CUSTOMER**: Staff responded and requested further documents/clarifications.
5. **WAITING_FOR_INTERNAL_TEAM**: Awaiting operations/commercial desk verification.
6. **ESCALATED**: Ticket escalated to Tier 2 / Shift Incharge / Commercial Admin due to SLA breach or customer request.
7. **RESOLVED**: Resolution summary provided by support representative. Passenger notified.
8. **CLOSED**: Passenger confirms resolution or 5-day auto-closure window elapses without dispute.
9. **REOPENED**: Reopened by passenger within 72 hours of resolution if issue persists.

## Audit Trails
Every status change generates an entry in `support_ticket_status_history` recording `from_status`, `to_status`, `changed_by_user_id`, `reason`, and the exact timestamp.
