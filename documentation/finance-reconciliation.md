# Finance & Dhruva Bank Reconciliation Specification

## Overview
Reconciles RailConnect internal payment and refund entries against Dhruva Bank demonstration gateway records.

## Dhruva Bank Demo Integration Context
- Endpoint: `https://dhruva-bank.vercel.app`
- GitHub: `https://github.com/dhanush10607-lang/Dhruva-bank.git`
- **Demonstration Notice**: All account balances, transactions, and refunds on Dhruva Bank are simulated demonstration values and do not represent real-world currency or banking reserves.

## Reconciliation Statuses
- `MATCHED`: Internal transaction amount and status correspond exactly to gateway response.
- `AMOUNT_MISMATCH`: Payment or refund amounts diverge.
- `STATUS_MISMATCH`: Status discrepancy (e.g. Gateway says `SUCCESS`, RailConnect has `PENDING`).
- `UNMATCHED_INTERNAL`: Present on RailConnect but absent on Dhruva Bank.
- `UNMATCHED_GATEWAY`: Transaction reported by Dhruva Bank webhook without internal order matching.

## Daily Reconciliation Workflow
1. Finance Officer triggers reconciliation via `POST /api/v1/admin/finance/reconciliation/run`.
2. Settlement engine audits day's payments and refunds.
3. Generates reconciliation summary (`run_code`) with matched and discrepancy tallies.
4. Exportable to CSV via `GET /api/v1/admin/reports/export?reportType=FINANCE`.
