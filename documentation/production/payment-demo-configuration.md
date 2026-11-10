# Dhruva Bank Demonstration Payment Configuration

## 1. Gateway Credentials & Endpoints
- **Base URL**: `https://dhruva-bank.vercel.app`
- **Checkout Endpoint**: `https://dhruva-bank.vercel.app/checkout`
- **Merchant ID**: `RAILCONNECT_DEMO_MERCHANT`
- **Callback Listener**: `/api/v1/payments/callbacks/dhruva-bank`

## 2. Safety & Verification Standards
- Server-to-server webhook verification using signed payloads.
- All requests stamped with unique `Idempotency-Key` headers.
- Prominent banners remind passengers that all transactions are simulated demo payments. Real money is never transferred.
