# Fare quote API

All responses use the standard API envelope and correlation/request IDs. Amounts are returned as decimal JSON numbers with currency `INR`; clients must display the backend result and must not recalculate it.

## Passenger endpoints

| Method and path | Access | Behavior |
|---|---|---|
| `GET /api/v1/public/fares/passenger-categories` | Public | Lists active configured categories and any configured age bounds |
| `POST /api/v1/public/fares/quotes` | Public with CSRF protection | Validates journey/class/categories and persists a new quote |
| `GET /api/v1/public/fares/quotes/{quoteId}` | Public route, ownership checked | Reads a random-ID quote snapshot and reports `ACTIVE` or `EXPIRED` |

The quote request contains `trainId`, `fromStationId`, `toStationId`, `journeyDate`, `coachClassCode`, and 1–20 passenger entries (`categoryCode`, optional `age`). It does not accept a price, distance, discount, tax, or rule-version ID. Repeating the POST refreshes by creating a new quote; it does not extend an existing quote's expiry.

## Admin endpoints

| Method and path | Permission |
|---|---|
| `GET /api/v1/admin/fares/rules`, `/options`, `/passenger-categories` | `fares:rules:read` |
| `POST /api/v1/admin/fares/rules`, `/rules/{ruleId}/versions` | `fares:rules:manage` |
| `GET /api/v1/admin/fares/versions/{versionId}` and `/audit` | `fares:rules:read` |
| `PUT /api/v1/admin/fares/versions/{versionId}` and `POST .../submit` | `fares:rules:manage` |
| `POST .../approve` | `fares:rules:approve` |
| `POST .../publish` and `/retire` | `fares:rules:publish` |
| `POST /api/v1/admin/fares/passenger-categories` | `fares:rules:manage` |
| `POST /api/v1/admin/fares/simulate` | `fares:rules:read` |

The simulator accepts a selected version and journey but does not persist a quote or modify lifecycle state. Missing rules, invalid distance coverage, inactive classes, and invalid passenger categories produce explicit errors.

## Quote response

Contains quote ID, status, journey/class context, distance, passenger-category breakdown, signed itemized line amounts, total, creation/expiry timestamps, and a quote-only notice. Internal rule-version IDs and personal passenger ages are not included in the public response.
