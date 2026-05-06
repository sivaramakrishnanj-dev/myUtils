---
review_id: amendment-nfr-android-client-bump-r2
phase: amendment
triggered_by: T-1.5
request_id: DCR-1 (ripple sweep)
raised_by: coordinator (user-approved ripple sweep)
kind: nfr-update
approved_by_user_at: 2026-05-06T13:38:56+05:30
---

# Amendment Review — NFR ANDROID Client Triplet Ripple Sweep (DCR-1 r2)

## Summary

Follow-up to DCR-1 initial amendment (commit 9501351). Sweeps the four `ripple_unresolved` files flagged in the initial amendment, updating all remaining literal occurrences of the old ANDROID client triplet (`19.09.37 / SDK 34 / Android 14`) to the new values (`21.02.35 / SDK 30 / Android 11`). Only illustrative/description-level strings — no AC text, no NFR rows, no schema shape changes.

## Files modified

| File | Change |
|---|---|
| `design/04-apis.md` | § 1.1.1 request headers table: `User-Agent` and `X-YouTube-Client-Version` values updated. § 1.1.2 request body JSON example: `clientVersion`, `androidSdkVersion`, `osVersion` updated. Front-matter `last_reviewed` bumped. |
| `design/01-overview.md` | § External contracts fragility note: inline version `19.09.37` → `21.02.35`. OQ-A row: triplet values updated + annotation noting refresh at commit 9501351. Front-matter `last_reviewed` bumped. |
| `design/06-formal/contract-tests.md` | CT-REQ-1 scenario description: `v19.09.37, Android 14 SDK 34` → `v21.02.35, Android 11 SDK 30`. Front-matter `last_reviewed` bumped. |
| `design/06-formal/innertube-player-response.schema.json` | Top-level `description` field: `Source: ANDROID client v19.09.37` → `v21.02.35`. No schema shape change. |

## Constraints verification

- [x] Did not touch any AC text or NFR row — only literal version strings and example blocks
- [x] OQ-A annotated with refresh history; OQ-A remains a live open question for future refreshes
- [x] CT-REQ-1 description string updated only; fixture `innertube-request-happy.json` was already updated in initial amendment
- [x] Schema file `description` string only; no schema shape change (no `const`, `enum`, `pattern`, or structural edits)
- [x] Did not touch files outside `scope_of_design_edit`
- [x] Traceability preserved — all AC/NFR/ADR cross-references unchanged

## Consistency checks

- `schemas_validated_against_fixtures`: not_applicable (no schema shape change; only `description` metadata string updated)
- `contract_tests_index_still_consistent`: passed (CT-REQ-1 still references `fixtures/innertube-request-happy.json` which was updated in initial amendment to match new triplet)

## Verdict

**Ripple sweep complete.** All four files now cite the current ANDROID client triplet (`21.02.35 / SDK 30 / Android 11`) consistently with the NFR values updated in commit 9501351. No further ripple detected.
