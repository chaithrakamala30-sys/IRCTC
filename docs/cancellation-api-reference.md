# Cancellation API Reference

---

### Passenger Endpoints

- `GET /api/v1/bookings/{bookingId}/cancellation-eligibility`
  - Returns whether booking is eligible, hours before departure, and passenger list.

- `POST /api/v1/bookings/{bookingId}/cancellation-quote`
  - Calculates transparent fee breakdown without executing cancellation.

- `POST /api/v1/bookings/{bookingId}/cancel`
  - Commits cancellation, releases seats, dispatches refund, and returns `CAN-...` reference.

- `GET /api/v1/cancellations/{cancellationId}`
  - Returns cancellation details and receipt.

- `GET /api/v1/refunds/{refundId}`
  - Returns refund order status and history.

---

### Administration Endpoints

- `GET /api/v1/admin/cancellations/{cancellationId}`
  - Staff audit review.

- `GET /api/v1/admin/refunds/{refundId}`
  - Staff refund inspection.

- `POST /api/v1/admin/refunds/{refundId}/reconcile`
  - Triggers authoritative bank verification and reconciles stranded refunds.

