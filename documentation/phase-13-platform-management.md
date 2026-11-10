# Station Operations & Platform Management (Phase 13)

This document describes station platform allocations, occupancy states, and concurrency conflict protection.

---

## 1. Platform Slot Invariants

Every platform assignment enforces that two trains cannot occupy the same physical platform at the same station simultaneously:

$$\text{Range}_1 \cap \text{Range}_2 = \emptyset$$

Where:
$$\text{Range} = [\text{scheduled\_arrival}, \text{scheduled\_departure}]$$

If an overlapping slot is detected, the database marks the assignment with `conflict_flag = true` and `assignment_status = 'CONFLICT'`.

