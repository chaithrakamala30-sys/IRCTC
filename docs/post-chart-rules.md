# Post-Chart Rules & Operational Corrections

This document governs the procedures and rules applicable after a departure chart has been prepared or finalized.

---

## 1. Post-Chart Cancellation Rules

- Cancellations after chart preparation are subject to late-cancellation policies (often non-refundable under 4 hours before scheduled departure).
- When cancellations occur post-chart, the freed berth is marked in `chart_correction_history` and becomes available for conductor reallocation on board.

---

## 2. Authorized Chart Corrections

- Conductor or station manager berth reassignments (e.g., coach defect, medical accommodation) require explicit authorization.
- Every correction writes an audit row to `chart_correction_history` and increments the chart version (`v1` $\rightarrow$ `v2`), preserving historical manifests immutably.
