# RAC (Reservation Against Cancellation) Management

RailConnect's RAC engine grants passengers a confirmed travel authorization without a full dedicated berth, placing them in an prioritized FIFO promotion queue for berths released upon cancellation.

---

## 1. RAC Mechanics

1. **Reservation Reference & PNR**:
   RAC passengers receive a valid booking reference and PNR. Their state is distinctly recorded as `RAC` with an assigned position (`#1`, `#2`, ...).

2. **Sequential Queue**:
   The RAC queue maintains an authoritative order based on `queue_sequence` generated transactionally in `reservation_queues`.

3. **Promotion Priority**:
   When any confirmed passenger cancels, the lowest sequential RAC passenger is automatically promoted:
   $$\text{RAC} \longrightarrow \text{CONFIRMED}$$
   The passenger's status transitions to `PROMOTED_CONFIRMED`, their position becomes 0, and a dedicated berth is assigned.
