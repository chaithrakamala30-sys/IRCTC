# Phase 15: Customer Support & Complaint Management System Architecture

## Overview
Phase 15 implements an enterprise-grade Customer Support & Complaint Management module for RailConnect. It provides real-time ticket tracking, staff dispatch, multi-tier escalation, strict SLA management, and dual-mode messaging (public passenger messages vs. confidential staff internal notes).

## Architecture Highlights
- **Layered RBAC**:
  - `PASSENGER`: Access strictly restricted to own tickets (`customer_id = principal.userId()`).
  - `CUSTOMER_SUPPORT`: Access to global support queue, assignment, internal notes, resolution, and escalation.
  - `SUPER_ADMIN` / `RAILWAY_ADMIN`: Full governance, team configuration, SLA monitoring, and override controls.
- **Data Persistence**:
  - Flyway migration `V19__create_customer_support_and_complaint_management.sql` provisioned tables for categories, teams, memberships, tickets, messages, attachments, status history, escalations, and feedback.
- **Confidential Internal Notes**:
  - Staff can exchange private operational notes attached to a ticket (`is_internal_note = true`), which are filtered out on passenger endpoints to guarantee internal discussions remain confidential.
- **Entity Linking**:
  - Support tickets can link directly to actual `booking_id`, `payment_id`, `ticket_id`, and `train_run_id` entities for instant context during staff triage.
