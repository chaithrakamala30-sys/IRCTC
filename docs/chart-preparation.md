# Departure Chart Preparation & Manifest Management

RailConnect departure charts represent immutable snapshots of train reservations at the time of operational chart preparation.

---

## 1. Chart Lifecycle

```mermaid
stateDiagram-v2
    [*] --> NOT_PREPARED
    NOT_PREPARED --> PREPARATION_PENDING: Window Opens
    PREPARATION_PENDING --> PREPARED: Run Promotions & Generate Snapshot
    PREPARED --> FINALIZED: Freeze by Operations Staff
    FINALIZED --> SUPERSEDED: Operational Correction Issued (New Version)
```

---

## 2. Operational Manifest Content

Each chart manifest record in `reservation_chart_passengers` contains:
- `pnr`: The passenger PNR.
- `passenger_name`: Passenger full name.
- `coach_number` & `berth_number` & `berth_type`: Allocated berth details.
- `coach_class_code`: Travel class.
- `reservation_status`: `CONFIRMED`, `RAC`, or `WAITLISTED`.
- `from_station_code` & `to_station_code`: Station boarding and destination codes.

