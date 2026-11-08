# Published schedule and route version selection

## Eligibility

Search considers only published or superseded schedule and route-version records that have a publication timestamp. Their effective date ranges, along with the train's effective range, must contain the service date derived from the selected origin stop. Draft, suspended, retired, unpublished, inactive, or out-of-range data is excluded.

Superseded versions remain eligible for their historical effective dates; status alone does not erase the version from passenger search. The selected schedule ID supplies operating weekdays and exceptions. The selected route-version ID supplies ordered stops and stop offsets for the detail view.

## Operating calendar

Weekday matching uses ISO weekday numbering. `RUN` is an explicit date override. Any exception for the service date suppresses normal weekday matching, which allows `CANCEL` to cancel an ordinarily operating day.

## Ambiguity protection and operational constraint

The query counts eligible schedule/route candidates per train and excludes a train if more than one candidate matches the selected service date. Publishing is serialized on the train row. When a new schedule starts after and overlaps the current published schedule, the current version is closed on the preceding day and the effective-range change is audited. Any remaining overlap against published or superseded versions is rejected. This is intentionally conservative: it rejects overlaps even when configured weekdays would not collide. Administrators must resolve ambiguous historical ranges through the authorized master-data workflow.

This safeguard prevents silently selecting conflicting calendars while allowing an ordinary future version to replace the current one. The current local validation did not exercise the publication transaction against PostgreSQL. Review schedule-version lifecycle behavior and run the database integration flow before applying V6 to a live environment.

## Composition scope

Coach-class summaries and class filtering use the train composition's current published version and active coaches/configurations. They describe the configured composition only; they are not date-specific availability, reservations, or inventory.
