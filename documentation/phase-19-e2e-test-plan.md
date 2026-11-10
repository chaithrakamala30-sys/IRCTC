# Phase 19: End-to-End Test Plan

## 1. Test Objective
Validate full user journeys across Passenger, Administration, and Operations portals using deterministic synthetic data.

## 2. Test Scenarios
1. **Passenger Journey**:
   - Register account $\rightarrow$ Login $\rightarrow$ Search train $\rightarrow$ Hold berth $\rightarrow$ Dhruva Bank demo checkout $\rightarrow$ PNR generation $\rightarrow$ Ticket download $\rightarrow$ QR code verification.
2. **Cancellation & Refund Flow**:
   - Select confirmed booking $\rightarrow$ Request cancellation $\rightarrow$ Calculate refund according to cancellation rules $\rightarrow$ Submit to Dhruva Bank $\rightarrow$ Confirm refund settlement.
3. **Operations & Notification Flow**:
   - OCC reports 45-minute delay on active train run $\rightarrow$ Event outbox records disruption $\rightarrow$ In-app notification delivered to ticket holders.
4. **Admin Maker-Checker & Governance Flow**:
   - Maker proposes fare adjustment $\rightarrow$ Checker approves proposal $\rightarrow$ Proposer attempting self-approval is rejected.

