# Train Tracking Engine Specification (Phase 13)

RailConnect's internal train tracking system provides passenger and operational visibility solely derived from validated station staff reports.

---

## 1. Tracking Model

- **No Synthetic Coordinates**: RailConnect does not draw interpolated GPS points between stations.
- **Route Stop Status**:
  - `COMPLETED`: Station arrival and departure confirmed by staff.
  - `CURRENT`: Train currently berthed or arrived at station.
  - `UPCOMING`: Scheduled downstream stations.
  - `SKIPPED`: Stations bypassed due to authorized route diversions.

---

## 2. Delay Calculation

$$\text{Delay}_{\text{minutes}} = \max\left(0, \frac{\text{ReportedActualTimestamp} - \text{ScheduledTimestamp}}{60,000}\right)$$

If $\text{Delay} > 15 \text{ minutes}$, the operational status shifts automatically from `RUNNING` to `DELAYED`.
