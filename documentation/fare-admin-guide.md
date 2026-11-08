# Fare administration guide

1. Sign in with an authorized Railway Admin, Finance Admin, or Super Admin account. Do not use shared accounts; authors/editors are retained per version.
2. Create any required passenger categories. Age limits are optional; leave them unset unless a reviewed policy defines them.
3. Create a draft rule. Choose its scope, INR pricing model, effective dates, and priority. Add slabs, components, or passenger adjustments only when explicitly approved. No rule is active at this point.
4. Run the journey simulator against a real published journey and class. Inspect each line and verify the selected date, category, and total.
5. Submit the draft. The content becomes immutable. A different user who did not author or edit that version must approve it.
6. A non-author/non-editor with publish permission publishes the approved version. Overlapping effective windows with the same scope and priority are rejected.
7. Review the version audit history and later quote snapshots as needed. To change a published policy, create a new version rather than editing the old one.

## Mobile and laptop

The console uses the existing admin portal and collapses version tables/forms into a horizontally scrollable table and single-column forms on narrow screens. It does not add a separate administrator authentication path.

## Troubleshooting

- `FARE_CONFIGURATION_UNAVAILABLE`: no matching published rule; create, approve, and publish reviewed pricing.
- `FARE_DISTANCE_NOT_COVERED`: extend the version's distance slabs to cover that distance without overlap.
- `FARE_RULE_MAKER_CHECKER_REQUIRED`: the acting user authored or edited this version; use a different approver/publisher account.
- `FARE_RULE_EFFECTIVE_RANGE_CONFLICT`: reconcile same-scope/priority overlapping effective dates.
- `FARE_CLASS_UNAVAILABLE`: publish coach configuration for the journey or choose a configured class.
