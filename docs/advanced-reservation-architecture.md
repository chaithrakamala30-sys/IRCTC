# Advanced Reservation Architecture (Phase 12)

RailConnect's Advanced Reservation Architecture provides a rule-driven platform for quota allocation, Reservation Against Cancellation (RAC), Waiting Lists (WL), cancellation-driven automatic promotion, and operational departure chart preparation.

---

## 1. Core Principles

1. **Physical Capacity Bounds**:
   The sum of all quota berth allocations for any class and train run must never exceed the train's actual physical capacity:
   $$\sum_{q \in \text{Quotas}} \text{AllocatedBerths}(q, \text{class}) \le \text{PhysicalCapacity}(\text{class})$$

2. **Sequential FIFO Queuing**:
   Both RAC and Waitlist queues assign monotonically increasing, gap-free transactional sequences per `(train_run_id, coach_class_code, quota_code)`.

3. **Atomic Multi-Stage Promotion**:
   When inventory is released (e.g. from cancellations), promotions cascade atomically:
   $$\text{Berth Released} \longrightarrow \text{RAC Pax promoted to Confirmed Berth} \longrightarrow \text{WL Pax promoted to RAC}$$

4. **Departure Chart Immutability**:
   Finalized charts are frozen snapshots. Operational corrections generate new chart versions and preserve full audit history.

---

## 2. Sequence Diagrams

### 2.1 Cancellation-Driven Promotion Flow

```mermaid
sequenceDiagram
    autonumber
    actor Passenger
    participant CS as CancellationService
    participant DB as PostgreSQL (Inventory & Allocations)
    participant PS as AdvancedReservationService
    participant PaxWL as Waitlisted / RAC Passenger

    Passenger->>CS: Submit Ticket Cancellation
    CS->>DB: Lock allocations & Mark status CANCELLED (FOR UPDATE)
    CS->>DB: Release inventory_segment_claims
    CS->>PS: processPromotionsForTrainRun(trainRunId, cancellationId)
    PS->>DB: Query eligible RAC entries (FIFO order, FOR UPDATE SKIP LOCKED)
    DB-->>PS: Return next RAC passenger
    PS->>DB: Assign freed confirmed berth to RAC passenger
    PS->>DB: Update RAC status -> PROMOTED_CONFIRMED
    PS->>DB: Query eligible Waitlist entries (FIFO order, FOR UPDATE SKIP LOCKED)
    DB-->>PS: Return next Waitlisted passenger
    PS->>DB: Update Waitlist status -> PROMOTED_RAC
    PS-->>PaxWL: Live reservation status update / notification
```

### 2.2 Chart Preparation & Operational Manifest Flow

```mermaid
sequenceDiagram
    autonumber
    actor Ops as Operations Staff
    participant OpsP as Operations Portal
    participant API as AdvancedReservationController
    participant RS as AdvancedReservationService
    participant DB as PostgreSQL

    Ops->>OpsP: Click "Prepare Chart" for Train Run
    OpsP->>API: POST /api/v1/operations/train-runs/{runId}/prepare-chart
    API->>RS: prepareChart(trainRunId, operatorId)
    RS->>RS: Run final promotion sweep
    RS->>DB: Generate frozen reservation_charts snapshot & passenger manifest
    RS-->>OpsP: Return ChartView (v1)
    Ops->>OpsP: Click "Finalize & Freeze Chart"
    OpsP->>API: POST /api/v1/operations/charts/{chartId}/finalize
    API->>RS: finalizeChart(chartId, operatorId)
    RS->>DB: Set chart_status = 'FINALIZED'
    Ops->>OpsP: Authorize Post-Chart Correction
    OpsP->>API: POST /api/v1/operations/charts/{chartId}/corrections
    API->>RS: authorizeCorrection(...)
    RS->>DB: Record chart_correction_history row & increment chart version (v2)
    RS-->>OpsP: Return updated ChartView (v2)
```
