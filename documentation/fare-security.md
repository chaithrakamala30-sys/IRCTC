# Fare engine security and privacy

## Authorization and maker-checker

All admin endpoints require their fine-grained fare permission in the backend. The UI only improves discoverability; it is not the security control. Authors and every draft editor are recorded by user ID and cannot approve or publish that same version. Submitted and approved rule content, audit history, and quote snapshots have database mutation guards.

## Passenger-facing data

Public quote input accepts identifiers and category/age values, never money or rule IDs. Journey, distance, active class, passenger category, and rule selection are resolved server-side. The response exposes only published master-data labels, an itemized price, quote state/times, and a version number—not the internal rule-version UUID. Ages are validated and are not persisted in the quote snapshot.

Anonymous quote lookup uses a cryptographically random UUID and returns a uniform 404 for unknown IDs; authenticated quotes are owner-checked. There is no public quote-list endpoint. The UUID is a bearer lookup capability and should not be shared publicly.

## Remaining controls and limits

CSRF protection remains enabled on quote POSTs; the web API client obtains the CSRF token. Request validation caps a quote at 20 passengers and standard request-body limits remain in force. No dedicated per-IP quote rate limiter or automatic expired-quote purge is implemented. Configure edge protections and approve a retention policy before production exposure. Do not log request bodies or ages.

This document records design controls, not a formal penetration test, privacy/legal review, or certification.
