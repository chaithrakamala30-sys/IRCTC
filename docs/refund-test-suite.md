# Refund Test Suite & Verification Results

This document describes the 17 comprehensive test scenarios executed to verify the integrity and robustness of the Dhruva Bank Merchant Refund engine and its integration with RailConnect's cancellation workflow.

---

## 1. Test Scenarios Matrix

| # | Test Scenario | Expected Outcome | Result |
| :---: | :--- | :--- | :---: |
| 1 | Full Refund of a ₹720 Payment | Debits merchant ₹720, credits passenger ₹720, returns 201 Created | **PASS** |
| 2 | Partial Refund (₹200 of ₹1000) | Debits merchant ₹200, credits passenger ₹200, remaining = ₹800 | **PASS** |
| 3 | Multiple Partial Refunds (₹200, then ₹300) | Sequential deductions succeed, cumulative tracked accurately | **PASS** |
| 4 | Refund greater than original payment (₹1200 of ₹1000) | Rejected with `400 REFUND_AMOUNT_EXCEEDED` | **PASS** |
| 5 | Cumulative refund exceeding original amount | Rejected with `400 REFUND_AMOUNT_EXCEEDED` | **PASS** |
| 6 | Refund for non-succeeded intent (`CREATED`) | Rejected with `400 INVALID_INTENT_STATUS` | **PASS** |
| 7 | Refund for unknown payment intent | Rejected with `404 INTENT_NOT_FOUND` | **PASS** |
| 8 | Refund from unauthorized merchant | Rejected with `401 AUTHENTICATION_FAILED` | **PASS** |
| 9 | Duplicate refund request with same idempotency key | Returns existing refund with 200 OK & `is_idempotent_replay: true` | **PASS** |
| 10 | Concurrent refund requests on same payment | Row-locking prevents race conditions; cumulative ceiling strictly enforced | **PASS** |
| 11 | Insufficient merchant demo balance | Rejected with `400 INSUFFICIENT_MERCHANT_FUNDS`, no accounts touched | **PASS** |
| 12 | Successful refund webhook delivery | Signed HMAC webhook dispatched to merchant endpoint | **PASS** |
| 13 | Duplicate webhook dispatch | Webhook retry mechanism prevents duplicate debit/credit ledger writes | **PASS** |
| 14 | Webhook delivery failure | Logged in `merchant_webhook_deliveries` with error code and status | **PASS** |
| 15 | Refund verification (`/verify`) | Authoritative ledger check returns `verified: true` & ledger entries count | **PASS** |
| 16 | Invalid request signature | Rejected with `401 AUTHENTICATION_FAILED` | **PASS** |
| 17 | Passenger account eligibility check | Fails if account status is inactive or frozen | **PASS** |

---

## 2. RailConnect Unit & Integration Test Coverage

RailConnect tests this integration via:
- [`CancellationPolicyEngineTest.java`](file:///c:/xampp/htdocs/IRCTC/backend/railconnect-core/src/test/java/in/railconnect/core/cancellation/CancellationPolicyEngineTest.java): Verifies tiered deductions, flat-rate minimums, and strict zero-refund thresholds under 4 hours before departure.
- [`DhruvaBankRefundAdapterTest.java`](file:///c:/xampp/htdocs/IRCTC/backend/railconnect-core/src/test/java/in/railconnect/core/cancellation/DhruvaBankRefundAdapterTest.java): Validates provider initialization, payload construction, header generation (`x-merchant-key`, `x-merchant-secret`), idempotency simulation, and `/verify` response parsing.

