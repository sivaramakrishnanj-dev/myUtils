# Test Fixtures

## Origin

All `innertube-*.json` fixtures in this directory were **synthesized** during Phase 3 (formal contracts) based on the JSON Schemas in `design/06-formal/`. They are structurally valid per those schemas but do not represent real InnerTube responses captured from the network.

Each fixture carries an `"x-captured-on"` top-level field recording its provenance date and type (e.g., `"synthesized-2026-05-03"`).

## Status

**Awaiting real-capture refresh at v1.1+.** See `contract-tests.md` § 7 `TODO(capture)` items. When network access is available, these fixtures should be replaced with real InnerTube responses and `x-captured-on` updated to the capture date.

## Test-writing guidance

- Do **NOT** depend on synthesis-specific quirks (exact URL strings, synthetic signature values, placeholder video IDs like `privprivpr1`). Assert structural properties and domain-logic outcomes, not fixture-specific literals.
- The `x-captured-on` field is metadata only — `PlayerResponseExtractor` ignores it per ADR-0004 (`FAIL_ON_UNKNOWN_PROPERTIES = false`).

## Shape-drift monitoring

See **OQ-A** in `design/01-overview.md` for the open question on InnerTube response shape stability. When real captures replace these synthesized fixtures, shape-drift between the schema and the live response will surface as contract-test failures.
