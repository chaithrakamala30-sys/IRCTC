# Overnight and multi-day journey calculation

## Service date and stop dates

Route stops store an arrival and departure day offset from the train's service date. If the passenger selects a journey date for an origin stop with departure offset `d`, the service date is:

```text
serviceDate = journeyDate - d
```

For each stop, arrival and departure local dates are `serviceDate + arrivalDayOffset` and `serviceDate + departureDayOffset`, respectively. Missing arrival or departure times remain absent in route details.

## Time zones and duration

Each stop time is combined with its station's configured IANA time zone. The API returns zoned departure and arrival values; the UI formats each using its station's local time zone. Duration is elapsed time between the resulting instants, not a subtraction of wall-clock times. Thus journeys across midnight or multiple days retain the configured offset and date.

Java's `withLaterOffsetAtOverlap()` chooses the later/standard offset for an ambiguous local time during a daylight-saving overlap. A nonexistent local time during a daylight-saving gap is shifted forward according to Java's zone rules. PostgreSQL `AT TIME ZONE` is used for ordering and SQL duration; its behavior should be checked against Java for any non-Indian station zones before such zones are used operationally.

## Filters and display

Departure filters compare the origin's local departure clock time; arrival filters compare the destination's local arrival clock time. Result cards show arrival date when it differs from departure date. Route details include each stop's local arrival/departure date and its day offset.

The unit tests cover an overnight route, an ambiguous DST overlap, and a nonexistent DST-gap time. PostgreSQL execution of the corresponding search SQL was not available locally; see the [testing report](phase-4-testing-report.md).
