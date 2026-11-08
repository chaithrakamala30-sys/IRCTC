# Train-search algorithm

## Candidate selection

The service validates that both station IDs and a journey date are present, that the stations differ and are active, and that the date is between today in `Asia/Kolkata` and the configured maximum horizon. Page size is limited to 1–100 and page number to 0–10,000.

PostgreSQL joins origin and destination stops from the same route version and requires the origin sequence to be before the destination sequence. A journey date at an intermediate origin is converted to the train's service date by subtracting that stop's departure-day offset.

Candidates must have active trains, categories and stations; a published route; a published or superseded route version with a publication timestamp; and a published or superseded schedule with a publication timestamp. Train, route-version, and schedule effective ranges must all include the derived service date. Routes that include an inactive station are excluded.

## Operating days and exceptions

The source service date's ISO weekday (Monday=1 through Sunday=7) is compared with the schedule's configured days. A dated `RUN` exception overrides the weekday rule. If any exception exists for that date, the regular weekday fallback is suppressed; consequently `CANCEL` does not run. A service is returned only when it has positive elapsed duration.

## Filters, ordering, and paging

Optional filters cover category code/name, published composition coach class code/name, origin-local departure time, destination-local arrival time, maximum elapsed minutes, and train number/name substring. Time bounds are inclusive. Supported sort keys are `DEPARTURE_EARLIEST`, `DEPARTURE_LATEST`, `ARRIVAL_EARLIEST`, `DURATION_SHORTEST`, `DURATION_LONGEST`, and `TRAIN_NUMBER`; train ID is the stable tie-breaker.

The service executes a total-count query and a bounded page query. If multiple eligible schedule or route candidates for a train match the same service date, the train is omitted rather than choosing an ambiguous version silently. When publishing a future schedule, the current published schedule is closed the day before the new effective start; other conflicting published/superseded ranges are rejected.

## Output boundaries

Results contain timetable identity, local station data, zoned journey times, duration, distance, intermediate-stop count, operating weekdays, and published coach-class summaries. They do not imply seat availability, fare, or bookability.
