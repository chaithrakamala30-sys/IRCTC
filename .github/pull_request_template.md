## Title:
feat(phase-14): implement passenger communication and notification system

---

### GitHub Pull Request Direct URL:
👉 **[Open PR on GitHub](https://github.com/chaithrakamala30-sys/IRCTC/compare/main...feature/phase-14-notifications?expand=1)**

---

## Pull Request Description:

### 📌 Summary of Changes
Implements **Phase 14 — Passenger Communication & Notification System** for RailConnect, establishing a centralized, multi-channel notification architecture covering in-app alerts, browser Web Push, and operational broadcasts, while strictly adhering to ₹0 infrastructure and zero PII exposure principles.

---

### 🚀 Key Capabilities Added:
1. **PostgreSQL Transactional Outbox Engine**:
   - `notification_outbox` table ensures atomic capture with core business events (`ON CONFLICT (idempotency_key) DO NOTHING`).
   - Resilient background worker processing with exponential backoff and dead-letter protection.
2. **Passenger In-App Notification Center (`/notifications`)**:
   - Live header notification bell with dynamic unread badge counter (`99+`) and polling refresh.
   - Comprehensive inbox supporting category filters (`BOOKING`, `PAYMENT`, `TICKET`, `RESERVATION`, `WAITLIST`, `RAC`, `TRAIN_OPERATION`, `PLATFORM_CHANGE`, `ANNOUNCEMENT`).
   - Single & batch mark-as-read, delete action, and deep links to journey details.
3. **Browser Web Push Integration (`public/sw-push.js`)**:
   - Explicit user opt-in flow with clear alert category explanations.
   - Background push notifications, vibration patterns, and deep-link click routing.
   - Device and token registry in `notification_devices` supporting multi-device delivery.
4. **Passenger Notification Preferences**:
   - Granular channel (In-App / Push) and category alert controls (`/notifications`).
   - Policy-enforced server-side guarantees for mandatory transactional alerts (booking, payment, cancellation).
5. **Portals Integration**:
   - **Admin Portal**: Delivery health metrics, dead-letter monitoring, template catalog viewer, and broadcast announcement tools.
   - **Operations Portal**: Operational advisories scoped to affected train runs and stations with real-time affected passenger count previews.
6. **Bug Fixes & Stability**:
   - Renamed duplicate `reservation_state_history` in `V16` to `passenger_reservation_state_history` to prevent Flyway startup conflicts.
   - Standardized `ApiEnvelope.success(data, RequestIds.current())` across notification controllers.
   - Fixed Mockito argument matchers and error code accessors in unit test suites.

---

### 📦 Database Migrations:
- **`V18__create_passenger_communication_and_notification_system.sql`**:
  - `notification_templates`
  - `notification_preferences`
  - `notification_devices`
  - `notification_outbox`
  - `notifications`
  - `notification_deliveries`
  - `notification_announcements`

---

### 🧪 Verification & Testing:
- [x] `@railconnect/passenger-web`: `npm run typecheck` & `npm run build` passed (exit code 0; 20 static pages exported).
- [x] `@railconnect/admin-portal`: `npm run build` passed (exit code 0; 4 static pages exported).
- [x] `@railconnect/operations-portal`: `npm run typecheck` passed (exit code 0).
- [x] Backend tests: `NotificationServiceTest`, `TrainOperationsControlServiceTest`, and `AdvancedReservationServiceTest` compile and pass.

---

### 🔒 Security, Privacy & Zero-Cost Compliance:
- [x] **Zero Cost**: Built entirely within free PostgreSQL and browser Web Push standards without paid SMS/Push/Email APIs.
- [x] **Data Minimization**: Push notification payloads contain zero credentials, MPINs, or complete passenger PII.
- [x] **User Isolation**: Strict ownership checks prevent cross-user notification access.
