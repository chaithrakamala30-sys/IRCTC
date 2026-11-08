# Passenger train-search API

All endpoints are under `/api/v1/public`, return the existing `ApiEnvelope` shape, and are available without a passenger account. They are read-only.

| Method and path | Purpose |
|---|---|
| `GET /search-config` | Return configured `maximumDays` |
| `GET /stations/search?q=...&limit=10` | Active station autocomplete |
| `GET /stations/{stationId}` | Active station details for URL restoration |
| `GET /trains/filters` | Active train categories and classes in current published compositions |
| `GET /trains/search` | Search direct services and return a bounded result page |
| `GET /trains/{trainId}?fromStationId=...&toStationId=...&journeyDate=...` | Matching journey plus full ordered route |
| `GET /trains/{trainId}/route?...` | Route only, using the same journey validation |

## Search parameters

Required: `fromStationId`, `toStationId`, and ISO date `journeyDate`.

Optional: `trainCategory`, `coachClass`, `departureFrom`, `departureTo`, `arrivalFrom`, `arrivalTo`, `maximumDuration`, `q` (train number/name), `sortBy`, `page` (default 0), and `size` (default 20). Time values use ISO local time. Date bounds are today through the configured horizon; size is 1–100. Supported sort values are listed in [the search algorithm](search-algorithm.md).

## Response data

Search pages contain `items`, `page`, `size`, and `totalItems`. Each item includes train and category identity, origin/destination, local zoned departure and arrival, their dates, elapsed duration, route distance, intermediate stops, operating weekdays, and coach-class summaries. Route details include ordered stops, local time/date, day offsets, halt, distance, and selected origin/destination markers.

## Errors and limits

Validation errors use the standard error envelope and include codes such as `SAME_STATION`, `PAST_JOURNEY_DATE`, `JOURNEY_DATE_TOO_FAR`, `INVALID_TIME_RANGE`, `INVALID_PAGE`, `INVALID_DURATION`, and `INVALID_SORT`. Unknown/inactive station IDs are rejected; a train journey with no eligible published service returns `TRAIN_JOURNEY_NOT_FOUND`.

Requests are bounded by parameter validation, page limits, and read-transaction timeouts. Do not treat successful timetable results as fare, availability, or booking confirmation. API documentation is exposed by the repository's existing OpenAPI endpoints.
