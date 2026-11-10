# Phase 15: Support Security, Privacy & Compliance

## Security Guarantees
1. **Ownership Enforcement**: Passenger endpoints check `customer_id = principal.userId()`. Unauthorized access returns HTTP 403.
2. **Confidential Note Isolation**: Database query for passenger conversation history enforces `AND is_internal_note = false`. Internal notes never leak over passenger APIs.
3. **Audit Immutability**: All status transitions, staff assignments, and escalation events record timestamps and acting operator user IDs.
4. **Attachment Safety**: File uploads validated for approved MIME types (PNG, JPG, PDF) with max size caps (5MB).
5. **PII Masking**: Payment identifiers and phone numbers are logged in compliance with data privacy mandates.
