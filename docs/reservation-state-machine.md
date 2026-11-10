# Reservation State Machine

This document defines the authoritative states and valid transitions for passengers, bookings, and departure charts in RailConnect.

---

## 1. Passenger Reservation States

```mermaid
stateDiagram-v2
    [*] --> WAITLISTED: Waitlist Capacity Available
    [*] --> RAC: RAC Capacity Available
    [*] --> CONFIRMED: Berths Available

    WAITLISTED --> RAC: Waitlist Promotion
    WAITLISTED --> CONFIRMED: Direct Full Berth Promotion
    WAITLISTED --> CANCELLED: Passenger Cancelled
    WAITLISTED --> EXPIRED: Chart Prepared without Promotion

    RAC --> CONFIRMED: Cancellation Promotion
    RAC --> CANCELLED: Passenger Cancelled

    CONFIRMED --> CANCELLED: Passenger Cancelled
```

---

## 2. Transition Guard Matrix

| From State | Allowed To States | Trigger Event |
| :--- | :--- | :--- |
| `WAITLISTED` | `RAC`, `CONFIRMED`, `CANCELLED`, `EXPIRED` | Prior cancellation or chart finalization |
| `RAC` | `CONFIRMED`, `CANCELLED` | Prior cancellation |
| `CONFIRMED` | `CANCELLED` | Passenger cancellation |
| `CANCELLED` | *None* | Terminal state |
| `EXPIRED` | *None* | Terminal state |
