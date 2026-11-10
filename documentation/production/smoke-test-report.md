# Full Production Smoke Test Report

## 1. Test Summary
Executed full end-to-end smoke verification using synthetic accounts and demo banking credentials across live deployed applications.

## 2. Test Execution & Status

| Flow | Scope | Result | Details |
| :--- | :--- | :---: | :--- |
| **Flow 1: Passenger Booking** | Search $\rightarrow$ Hold $\rightarrow$ Dhruva Bank $\rightarrow$ Confirm $\rightarrow$ Ticket | **PASSED** | PNR generated; QR code verification URL validated. |
| **Flow 2: Ticket Cancellation** | Cancel Booking $\rightarrow$ Refund Calculation $\rightarrow$ Settlement | **PASSED** | Cancellation fee deducted; seat returned to inventory. |
| **Flow 3: OCC Delay Update** | Train Run Update $\rightarrow$ Live Passenger Status $\rightarrow$ In-App Alert | **PASSED** | 30m delay reflected immediately across passenger views. |
| **Flow 4: Maker-Checker Governance**| Change Proposal $\rightarrow$ Self-Approval Attempt $\rightarrow$ Checker Approval | **PASSED** | Self-approval blocked; independent checker approval applied. |
| **Flow 5: Customer Support Lifecycle**| Ticket Raised $\rightarrow$ Private Note $\rightarrow$ Public Reply $\rightarrow$ CSAT 5★ | **PASSED** | Private note remained completely hidden from customer. |
