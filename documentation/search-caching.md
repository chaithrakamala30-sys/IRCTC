# Search caching

## Current behavior

There is no application-level cache for station suggestions, filters, search results, or route details. The PostgreSQL master-data tables remain the source of truth, so an administrator's published change is not hidden behind a separate search cache. No Redis service or paid cache infrastructure was activated.

The passenger browser stores up to three recent search selections in `localStorage` under `railconnect.recent-searches`. This is a convenience shortcut containing station display data and a journey date; it is not an authoritative result cache. Replaying a recent search resolves the station IDs and executes a fresh API search.

## Invalidation and future work

Because result data is not cached by the application, there is no server-side invalidation protocol. If caching is later introduced, it must be bounded, keyed by effective service date and selected route/stations/filters, and invalidated or versioned against published master data. Never cache or imply seat availability, reservations, or fare state as part of this timetable search.
