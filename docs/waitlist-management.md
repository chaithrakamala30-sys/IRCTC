# Waitlist Management Architecture

RailConnect manages waitlisted reservations across train runs, classes, and quotas with full FIFO integrity and real-time status tracking.

---

## 1. Waitlist Entry Properties

Each waitlisted passenger record in `waitlist_entries` stores:
- `booking_id` & `booking_passenger_id`
- `train_run_id`
- `coach_class_code`
- `quota_code`
- `waitlist_category` (`GNWL`, `RLWL`, `PQWL`, etc.)
- `initial_position` & `current_position`
- `queue_sequence`
- `status`: `WAITLISTED`, `PROMOTED_RAC`, `PROMOTED_CONFIRMED`, `CANCELLED`, `EXPIRED`

---

## 2. Dynamic Position Updates

When prior passengers cancel or are promoted, waitlist positions advance automatically. Passengers can check their live status and position history at any time via `GET /api/v1/bookings/{bookingId}/reservation-status`.

