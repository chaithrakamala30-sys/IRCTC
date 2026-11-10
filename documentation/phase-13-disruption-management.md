# Disruption & Incident Management (Phase 13)

RailConnect classifies operational exceptions into disruptions and incidents with passenger impact assessment.

---

## 1. Disruption Types & Rules

- `DELAY`: Operational delay propagated along downstream route.
- `TEMPORARY_HALT`: Train held at an intermediate signal or siding.
- `DIVERSION`: Route diversion bypassing specific scheduled stops.
- `PLATFORM_SHIFT`: Platform change at station.
- `TRAIN_CANCELLED`: Complete service cancellation (triggers existing Phase 11A refund workflow).

---

## 2. Incident Lifecycle

$$\text{OPEN} \longrightarrow \text{ACKNOWLEDGED} \longrightarrow \text{INVESTIGATING} \longrightarrow \text{ESCALATED} \longrightarrow \text{RESOLVED} \longrightarrow \text{CLOSED}$$
