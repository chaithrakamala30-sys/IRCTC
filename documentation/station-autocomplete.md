# Station autocomplete

## Endpoint and matching

`GET /api/v1/public/stations/search?q={text}&limit={n}` returns active-station suggestions. Query text is trimmed and must contain 2–80 characters; `limit` is 1–20 (the UI requests 10). Matching supports station-code, name, city, and alternate-name prefix/substring matches plus PostgreSQL `simple` full-text matching. Exact code/name/city and prefix results rank ahead of broader matches.

The database query uses bound parameters and escaped `LIKE` metacharacters. A selected station's stable UUID—not its display text—is submitted with train searches. `GET /api/v1/public/stations/{stationId}` resolves active station details for URL-restored searches.

## Browser behavior and accessibility

The input debounces requests, cancels stale fetches, exposes a listbox/combobox relationship, supports arrow-key selection, Enter, and Escape, and prevents selecting the same station at both ends. Suggestions show the station code, name, city, and state. Search requires selecting an actual suggestion; free text alone is not treated as a station.

## Limitations

There is no trigram extension, typo-tolerant search, geographic distance ranking, or external directory fallback. Full-text and alternate-name matching have not been benchmarked with production-scale railway data. Only active stations are returned.
