---
review_id: amendment-nfr-android-client-bump-r1
phase: amendment
triggered_by: T-1.5
request_id: DCR-1
raised_by: specDrivenImplementer
kind: nfr-update
approved_by_user_at: 2026-05-06T13:32:45+05:30
---

# Amendment Review — NFR ANDROID Client Triplet Bump (DCR-1)

## Summary

Updates the ANDROID client identity triplet (`NFR-ANDROID-CLIENT-VERSION`, `NFR-ANDROID-SDK-VERSION`, `NFR-ANDROID-USER-AGENT`) from the deprecated `19.09.37 / SDK 34 / Android 14` values to the current yt-dlp master values `21.02.35 / SDK 30 / Android 11`. This is Risk R-1 from `07-tasks.md` § 5 manifesting as anticipated.

## Files modified

| File | Change |
|---|---|
| `design/00-requirements.md` | Phase 1c Group 2 table: updated three NFR value cells + rationale text. Front-matter `last_reviewed` bumped. |
| `design/adr/0001-android-innertube-client.md` | Added § "Client-version refresh log" documenting this refresh and the pattern for future refreshes. Updated inline parenthetical values in Context section. |
| `design/06-formal/innertube-player-request.schema.json` | Updated `examples` block values (`clientVersion`, `androidSdkVersion`, `osVersion`) and description strings. Schema constraints unchanged. |
| `design/06-formal/fixtures/innertube-request-happy.json` | Updated `context.client` sub-fields to match new triplet. `videoId` unchanged (`dQw4w9WgXcQ`). |

## Constraints verification

- [x] Did not touch files outside `scope_of_design_edit` (ripple in `04-apis.md`, `01-overview.md`, `contract-tests.md` flagged as unresolved for user)
- [x] Did not edit Phase 1a user stories in `00-requirements.md`
- [x] Traceability preserved — AC refs to `NFR-ANDROID-*` unchanged; only values updated
- [x] ADR-0001 status remains "Accepted"; new subsection appended, existing decision/rationale untouched
- [x] Schema `const` for `clientName="ANDROID"` unchanged; only example values updated
- [x] Fixture `videoId` remains `"dQw4w9WgXcQ"`; only `context.client` sub-fields updated

## Consistency checks

- `schemas_validated_against_fixtures`: passed (manual structural verification — `jsonschema` library not available in environment; fixture matches all schema constraints by inspection)
- `contract_tests_index_still_consistent`: not_applicable (CT-REQ-1 description in `contract-tests.md` references old values but is outside scope; flagged as ripple_unresolved)

## Verdict

**Amendment is complete and internally consistent within scope.** Ripple in files outside scope is documented for user action before code resumes.
