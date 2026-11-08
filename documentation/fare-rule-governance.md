# Fare-rule governance

## Version lifecycle

Each logical rule has independently numbered versions. Version content is editable only while `DRAFT`; submission freezes it. The allowed sequence is:

`DRAFT → SUBMITTED → APPROVED → PUBLISHED → RETIRED`

An author or any editor of a version is recorded in `fare_rule_version_authors`. Every author/editor is barred from approving or publishing that version, even if they hold those permissions. An approved version must be published by a non-author/non-editor. Changes after submission require a new version.

## Permissions

| Permission | Purpose | Default role grant |
|---|---|---|
| `fares:rules:read` | Read rules, categories, audit, and run simulation | Super Admin, Railway Admin, Finance Admin |
| `fares:rules:manage` | Create/edit drafts, add categories, submit | Super Admin, Railway Admin, Finance Admin |
| `fares:rules:approve` | Approve another user's submitted version | Super Admin, Finance Admin |
| `fares:rules:publish` | Publish or retire another user's version | Super Admin, Finance Admin |

These are enforced server-side with method-level authorization. Hiding an action in the UI is not an authorization boundary.

## Publication safety

Publishing locks fare publication operations for the transaction, then checks for an overlapping effective period at the same scope and priority. A conflict is returned rather than making selection ambiguous. The database also guards valid lifecycle transitions and prevents content changes to submitted or approved versions and their child rows.

Older versions remain published for their non-overlapping historical/future effective dates; publishing a later version does not silently rewrite history. Retiring a version excludes it from future quotes.

## Audit

Create, edit, submit, approve, publish, and retire events are append-only and include the actor, version, event time, and event details. Quotes separately persist the exact selected rule-version reference with their immutable snapshot.
