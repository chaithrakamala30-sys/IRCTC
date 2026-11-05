# UI navigation, design system, and role model

This defines information architecture only. Phase 0 does not create UI pages or decorative mockups. All UI state is a view of API-authoritative state; no client-only booking, availability, fare, permission, or payment decision is valid.

## Device-specific webpage requirement

The passenger experience has two explicit webpage routes rather than only resizing one page:

- `/mobile` is the mobile-browser experience: compact header, one-column content, large touch targets, and bottom-oriented primary navigation.
- `/laptop` is the laptop/desktop-browser experience: full navigation, wider timetable/search layout, and desktop content hierarchy.
- `/` detects a coarse viewport breakpoint in the browser and routes to the corresponding page. It also provides visible manual links so the user can switch experience or recover if automatic routing is unavailable.
- Both routes use the same shared components, API client, design tokens, accessibility semantics, and business rules. Device-specific presentation must not fork booking logic or security behavior.
- Route selection is based on viewport size, not user-agent sniffing. Users may open either route on any browser/device. The site must not repeatedly force a user who intentionally switches routes back to the automatic choice.
- Tablet and resized-window behavior must be tested at the breakpoint; responsive CSS still protects layout within each dedicated webpage.

## Passenger application

### Global shell

- Header: RailConnect wordmark (not IRCTC/Indian Railways marks), primary navigation, language/accessibility controls, account menu.
- Persistent travel advisory/status region for operational notices with source/time and dismissibility; urgent safety notices cannot be hidden by default.
- Main content landmark, breadcrumbs on deep pages, footer with help, terms/privacy, and accessibility contact.
- Signed-out navigation: Home, Find trains, Help, Sign in / Create account.
- Signed-in navigation: Find trains, My bookings, Profile & security, Help, Sign out.

### Page map and primary journey

1. **Home** — origin, destination, journey date, passenger count, class; recent searches only if privacy settings permit.
2. **Train search** — filters for date/class/departure window/train type; station-code and accessible autocomplete; validation summary.
3. **Search results** — schedule, route summary, class, fare quote range/known fare, availability freshness, status; do not imply seats are guaranteed.
4. **Train details** — run date, stop times/timezone, class/coach metadata, operational status and data source.
5. **Availability** — exact from/to stops, class/quota and `asOf`; clearly states estimate until an inventory hold commits.
6. **Passenger information** — data-minimized passenger details, berth preference only when rule supports it, contact and privacy notice.
7. **Fare review / checkout** — immutable quote amount/currency/rule version/expiry, cancellation terms, simulated-vs-live payment disclosure.
8. **Payment result** — pending/failed/succeeded from API state; do not infer from browser redirect.
9. **Booking confirmation** — only after committed confirmation; booking reference/PNR, passengers, seats or RAC/waitlist status, ticket and support.
10. **Ticket** — authenticated access, accessible text/print/PDF; cache headers private/no-store.
11. **PNR enquiry** — high-entropy reference plus approved verification step; minimal result and strict anti-enumeration response.
12. **Cancellation** — eligible segments/passengers and versioned refund estimate; explicit confirmation, outcome and refund tracking.
13. **Refund status** — pending/processed/failed/unknown with last verification time and support path.
14. **My bookings** — current/history filters; no PNR exposure in public URLs beyond required secure flow.
15. **Profile and security** — contact verification, sessions, MFA/recovery, privacy preferences, data request workflows.
16. **Support** — help articles, case submission/tracking, secure case context and privacy-minimized attachments (attachments disabled until storage/security approved).

## Administration application

Separate navigation and session context from passenger application. Shared identity does not imply shared permissions.

- **Overview:** bookings, disruptions, inventory exceptions, payment reconciliation, system health, security events; each panel labels source/freshness.
- **Railway catalogue:** stations; trains; routes/stops; schedule versions; classes; coach/seat templates.
- **Operations:** train runs/status; delays; disruptions; cancellations; station operations; passenger announcements.
- **Inventory:** run inventory imports; dry-run validation; quota/class availability; holds; allocation integrity; waitlist/RAC queue (only when policy approved).
- **Commercial rules:** fare versions; quota rules; berth preferences; cancellation/refund rules; approval and effective dates.
- **Reservations:** scoped lookup, reservation lifecycle, PNR/ticket access, exception handling; no direct state editing.
- **Finance:** payment attempts, settlement/reconciliation, refunds, approval queue, ledger audit.
- **People and access:** user search with minimized fields; staff/role/scope assignment; MFA status; sessions; access-review history.
- **Audit and reports:** immutable event search, operational reports, data freshness/retention, controlled export.
- **System configuration:** feature flags, provider health, environment and quota status; secrets are never displayed.

Configuration changes use draft -> validation preview -> review/approval -> publication -> audit. Sensitive operations (publish fare/inventory, refund, issue operational cancellation, modify privileged role) require reason, step-up authentication, and—above approved threshold—second-person approval. Rollback is a new compensating version, never deletion of published history.

## Operations workspace

Provide a role-filtered operational view of active train runs: status and last-updated time, expected/actual timetable, disruptions, affected stations/passengers, unresolved work, announcement state, and integrity warnings. It shares the admin API but should focus on triage and audited actions rather than exposing broad configuration.

## Role and permission matrix

Permissions are server-defined and scope-bound. Matrix is a starting least-privilege model; exact business scopes/approval thresholds require owner approval.

| Capability | Super admin | Railway admin | Station manager | Reservation officer | Finance admin | Support officer |
|---|---:|---:|---:|---:|---:|---:|
| Manage platform roles/permissions | Yes, audited/step-up | No | No | No | No | No |
| Manage stations/routes/schedules | Yes | Yes, approval | Scoped read | Read | Read | Read |
| Publish train/run inventory | Yes, dual approval | Yes, dual approval | Scoped proposal | No | No | No |
| Record station/run disruption | Yes | Yes | Scoped | No | No | No |
| View reservation details | Scoped/audited | Scoped | Scoped | Scoped | Minimum for reconciliation | Minimum for case |
| Change booking state directly | No (break-glass only) | No | No | No | No | No |
| Approve/cancel operational workflow | Yes, audited | Yes, scoped | Scoped, propose | Scoped, permitted actions | No | No |
| View payment/ledger | Yes, audited | No | No | Minimal | Yes | No |
| Initiate/approve refund | No self-approval | No | No | No | Initiate/approve by separation rule | No |
| Manage rule versions | Yes, approve | Draft/publish by approval | No | Read | Refund-rule review | Read |
| Audit/report export | Yes | Scoped | Scoped | Scoped | Finance scoped | Case scoped |

No human role receives direct DB write privileges for operational actions. Break-glass requires a time-limited grant, reason, alert, and independent review.

## Design system and accessibility

- Visual language: railway timetable clarity, route/station wayfinding, calm navy/blue base, restrained signal/status colors, readable typography, strong date/time and availability hierarchy. Do not copy official railway branding or use unlicensed marks.
- Components: accessible station autocomplete, date picker, passenger form, train result row/card, stop timeline, availability status, fare breakdown, state badge, confirmation panel, cancellation confirmation, accessible table, modal/dialog, toast/live-region notification, skeleton/loading and explicit empty/error states.
- Color is never the only status indicator; pair with text/icon and semantic state.
- Target WCAG 2.2 AA: keyboard and focus order, visible focus, labels/instructions, error summary and inline association, contrast, zoom/reflow, reduced motion, screen-reader announcements for async status, large touch targets.
- Use consistent date and currency presentation, with locale/timezone shown when ambiguity is possible. Avoid policy wording that implies official IRCTC/Indian Railways affiliation.
- Do not display fake availability, fake PNR, fake payment, or fabricated operational messages outside explicitly labelled UI tests.
