# Phase 15: SLA Tracking & Multi-Tier Escalation

## SLA Configuration
- **Response SLA**: Configured per category (e.g. 1 hour for Payment/Refund disputes, 4 hours for General Enquiries).
- **Resolution SLA**: Configured per category (e.g. 12 hours for Payment Issues, 24 hours for Booking & Ticket problems, 48 hours for general matters).
- **Priority Multipliers**: High / Critical tickets reduce target resolution windows by 50%.

## Escalation Rules
- **Tier 1 (Support Agent)**: Initial intake, triage, document request, and common dispute resolution.
- **Tier 2 (Senior Support / Shift Lead)**: Complex refund reconciliations, PNR sync failures, or tickets exceeding first response SLA.
- **Tier 3 (Super Admin / Railway Commercial Admin)**: Persistent disputes, legal/regulatory escalations, or repeated complaints.
- Escalation history is preserved with escalation reason, source tier, and target tier.
