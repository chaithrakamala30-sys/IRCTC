# Passenger categories

Passenger categories are platform configuration, not assertions about external railway policy. Migration V8 creates one generic `ADULT` category with no age bounds or discount so an administrator can use it in an explicitly authored rule. No fare price or concession is seeded.

Administrators with `fares:rules:manage` can add categories using a unique uppercase code, display name, and optional inclusive minimum/maximum age. The passenger quote request may include an age; where a category has an age boundary, age is required and validated by the backend. Categories without age bounds do not require age. A category's name alone never causes a discount.

Each rule version may configure at most one fixed/percentage charge or discount for each category. Components may separately be scoped to a category. Rules referencing inactive or unknown categories are rejected at authoring time. Passenger categories are currently append-only through the admin API; there is no update/deactivation endpoint.

The public category response includes only active category metadata and age bounds. Passenger ages are used for validation but are not copied into persisted quote snapshots or public quote responses.
