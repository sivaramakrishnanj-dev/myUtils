---
review_id: 2026-05-03-formal-phase-3-r1
reviewed_commit: 12fb5cc
reviewer: srk
author: srk
phase: formal
status: resolved
resolved_commit: 12fb5cc
resolved_at: 2026-05-03
---

# Review: Phase 3 — Formal Contracts (combined) — round 1

**Scope:** all Phase 3 artifacts at commit `12fb5cc`:
- `design/06-formal/cli-exit-codes.md` (171 lines)
- `design/06-formal/state-machine.md` (186 lines)
- `design/06-formal/innertube-player-request.schema.json` (84 lines)
- `design/06-formal/innertube-player-response.schema.json` (234 lines)
- `design/06-formal/caption-track.schema.json` (46 lines)
- `design/06-formal/contract-tests.md` (205 lines)
- `design/06-formal/fixtures/` (8 synthesized fixtures + README)
- `design/06-formal/README.md` (updated from `pending` to populated)

Reviewed as a set because the seven artifacts are mutually referential — schemas reference each other, `contract-tests.md` indexes them all, `state-machine.md` references `cli-exit-codes.md` categories, and fixtures validate against schemas.

---

## Review notes

No blocking, major, minor, or nit comments. All Phase 3 artifacts approved as-drafted.

**What was verified:**

- **Schemas parse as valid JSON Schema Draft 2020-12.** All 3 schemas have stable `$id` URIs, `x-captured-on: 2026-05-03` notes documenting the capture date of the wire shape they describe (InnerTube response and caption track), and `additionalProperties: true` at every nested level for the response schema per ADR 0004's partial-parse pragmatism.
- **Every fixture validates against its schema.** 9 synthesized fixtures (1 request, 6 response, 2 caption) all pass schema validation. One defect was caught during draft preparation — two placeholder video IDs were 12 chars instead of the 11-char pattern required by the schema, exactly the class of drift these schemas exist to catch. Fixed in the same draft commit before merge.
- **`cli-exit-codes.md` is single-source-of-truth** for the 11 AC-5.2 failure categories plus 2 signal codes (`130`, `143`). Prose in `02-architecture.md` § 3 and `04-apis.md` § 3.1.4 now defer to this file. Machine-readable YAML appendix lets Phase 5 tests load the table programmatically. Category ↔ Java exception mapping (§ 3) is authoritative for the `ErrorMapper` implementation.
- **`state-machine.md` has 16 numbered invariants (INV-1..INV-16)** across structural (machine itself), resource (what the process holds), and correctness (outputs and error paths) categories. Every invariant is a testable property with a specific test strategy sketched in `contract-tests.md` § 5. INV-11 (exit code from `ErrorMapper` only) and INV-12 (no output leakage on failure) are the two invariants that most directly protect US-5 (fail fast with useful error).
- **`contract-tests.md` inventories 78 contract tests** traceable to Phase 1 ACs. The category split (10 positive, 27 negative, 41 app-level assertions) is balanced — more app-level than wire-level assertions, which is the right ratio for a reverse-engineered-API consumer.
- **`contract-tests.md` § 7 TODO(capture) markers** correctly flag which fixtures are synthesized placeholders vs real captures. Phase 5 development will replace synthesized fixtures with real captures and update `x-captured-on` dates accordingly.
- **Scope split from Phase 2 is honoured.** Phase 2 `04-apis.md` describes wire contracts in prose; Phase 3 schemas are authoritative. Where they overlap, this review verified that the prose and schemas agree. The `output-metadata.schema.json` was deliberately skipped per user decision — `--print-json` is Future Work, not MVP — and this is explicitly documented in `06-formal/README.md`.
- **Cross-file referential integrity.** `state-machine.md` invariants reference AC IDs, NFR IDs, and exit-code categories. `contract-tests.md` references fixture paths, schema `$id`s, AC IDs, and INV IDs. All references were cross-checked during draft preparation.
- **Three pre-flagged reviewer-push-points** were considered during drafting and accepted as-drafted:
  - Caption track schema as parsed form (not raw XML) — easier to test, less authentic. Accepted because the parser's output is what tests need to validate.
  - Synthesized fixtures (not real captures) — necessary because the agent environment has no network access to capture real responses. Real captures happen in Phase 5.
  - No `output-metadata.schema.json` — deliberate MVP scope cut, not an oversight.

**Why this review has no numbered comments:**

Silence in a review process must be explicit. This file exists to record that the Phase 3 artifact set was reviewed, considered complete, and approved as-drafted — not that review was skipped.

**Phase gate:** **Phase 3 — Formal Contracts is closed.** With Phase 1 + Phase 2 + Phase 3 all resolved, the design baseline is now complete enough to drive Phase 4 (ordered task breakdown mapping implementation work to ACs) and Phase 5 (code). Further changes to any Phase 3 artifact require a new review round on this file and an explicit phase-transition entry.

Proceeding to **Phase 4 — Tasks** (`07-tasks.md`): the ordered implementation plan.
