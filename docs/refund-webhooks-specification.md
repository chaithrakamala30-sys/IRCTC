# Dhruva Bank Refund Webhook Specification

Dhruva Bank dispatches cryptographically signed webhook notifications to RailConnect's configured webhook listener upon refund completion or status transition.

---

## 1. Webhook Notification Format

- **Method**: `POST`
- **Path**: `/api/v1/payments/webhooks/dhruva-bank/refunds` (or `/api/v1/cancellations/webhooks/dhruva-bank`)
- **Headers**:
  ```http
  Content-Type: application/json
  x-dhruva-signature: <hex_hmac_sha256(timestamp + "." + rawBody, webhook_secret)>
  x-dhruva-timestamp: <unix_epoch_seconds>
  x-dhruva-event-id: evt_xxxx
  ```

### Sample Payload (`refund.succeeded`)
```json
{
  "event_id": "evt_7f10ad9014bca8102e3f59012a4c",
  "event_type": "refund.succeeded",
  "timestamp": "2026-11-10T12:35:00.000Z",
  "data": {
    "refund_id": "ref_84ad9014bca8102e3f59012a4c",
    "merchant_refund_ref": "RC-REF-902341",
    "cancellation_reference": "CAN-8921458921",
    "payment_intent_id": "pi_b98d0d771af063405a895fd9d0719faa",
    "booking_reference": "RC-GRPYZBFLQAXG",
    "amount": 720.00,
    "currency": "INR",
    "status": "SUCCEEDED",
    "completed_at": "2026-11-10T12:35:00.000Z"
  }
}
```

---

## 2. Supported Event Types & State Mappings

| Dhruva Bank Event | Event Type String | RailConnect Target Refund Status | Action Taken |
| :--- | :--- | :--- | :--- |
| **Requested** | `refund.requested` | `REQUESTED` | Records receipt event |
| **Processing** | `refund.processing` | `PROCESSING` | Logs bank processing state |
| **Succeeded** | `refund.succeeded` | `SUCCEEDED` | Finalizes refund order, settles ledger reconciliation |
| **Failed** | `refund.failed` | `FAILED` | Flags order as failed, alerts operations |
| **Reconcile Needed** | `refund.requires_reconciliation` | `REQUIRES_RECONCILIATION` | Enqueues for staff manual review in Admin Portal |

---

## 3. Cryptographic Signature Verification (HMAC-SHA256)

RailConnect verifies webhook integrity using HMAC-SHA256:
$$\text{ExpectedSig} = \text{HMAC-SHA256}(\text{timestamp} + \text{"."} + \text{rawBody}, \text{webhook\_secret})$$

### Freshness Window
- Timestamp drift must not exceed $\pm 300$ seconds (5 minutes) to protect against replay attacks.

---

## 4. Delivery Guarantees & Idempotency
- Exponential backoff with up to 5 retries.
- Webhook retries never duplicate financial credit or debit operations because the refund record is uniquely identified by `merchant_refund_ref` and `event_id`.
- Duplicate delivery attempts for an already settled refund order return HTTP 200 OK without re-executing transitions.
