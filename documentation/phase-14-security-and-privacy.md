# Phase 14: Security, Access Control & Privacy Specification

## 1. Data Minimization in Push Payloads
To protect passenger confidentiality and meet regulatory privacy requirements:
- No passwords, MPINs, auth tokens, or bank account numbers are ever included in notification payloads.
- Push messages contain sanitized summaries and a secure `deepLink` pointing to authenticated pages.

## 2. Access Control & Authorization
- Every passenger endpoint verifies user ownership: a user can never read, modify, or delete another user's notifications.
- Admin statistics and templates require `SUPER_ADMIN` or `RAILWAY_ADMIN` role permissions.
- Operational announcements require authorized staff roles and strictly validate train run scopes.
