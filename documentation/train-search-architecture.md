# Passenger train-search architecture

## Request path

1. The passenger uses the mobile or laptop Next.js page.
2. The browser requests the same-origin `/api/v1/public/*` path. Vercel deployments proxy that path to the configured Spring Boot API origin; local setups can point `NEXT_PUBLIC_API_BASE_URL` at the API.
3. Spring Security permits only the explicitly public API path without a session. The controller validates and maps the request to the search service.
4. `PublicTrainSearchService` queries PostgreSQL for active, published, effective master data. PostgreSQL remains the authority for results.
5. The response uses the existing `ApiEnvelope` contract. The UI renders explicit loading, empty, and error states.

## Components and responsibilities

| Component | Responsibility |
|---|---|
| `PublicTrainSearchController` | Public station, filter, configuration, search, and route-detail endpoints |
| `PublicTrainSearchService` | Input validation, PostgreSQL candidate selection, filtering, sorting, pagination, and DTO assembly |
| `JourneyTimeCalculator` | Convert route stop local times and service-day offsets into zoned departure/arrival values and duration |
| V6 Flyway migration | Add indexes for active station lookup, published schedules, and route-stop matching |
| `StationAutocomplete` | Debounced, keyboard-accessible station selection |
| `TrainSearchExperience` | Shared search behavior for distinct mobile and laptop route layouts |
| `TrainDetailsExperience` | Journey-specific ordered route display |

## Data flow and consistency

The search query joins schedules to their route version and ordered stops, then validates train, route, schedule, station, effective-date, weekday, and exception state in PostgreSQL. Coach classes are batch-loaded for the page of distinct train IDs; operating weekdays are batch-loaded for the page's schedule IDs. The implementation avoids per-result database queries.

Search results are not cached. Station data, published schedule state, and composition data are read from PostgreSQL; no separate search index or paid data service is introduced.

## Deployment notes

For Vercel, configure the existing same-origin API rewrite through `RAILCONNECT_API_ORIGIN`. For local cross-origin development, configure `NEXT_PUBLIC_API_BASE_URL` and allow the exact frontend origin in `RAILCONNECT_CORS_ALLOWED_ORIGINS`. Never place credentials or private keys in `NEXT_PUBLIC_*` values.
