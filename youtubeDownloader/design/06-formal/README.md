---
doc: formal-index
last_reviewed: 2026-05-03
status: draft
---

# 06 — Formal Specs

This folder holds the **machine-checkable specs** for youtubeDownloader. Every data shape that crosses a process boundary (InnerTube wire messages, caption tracks, our CLI exit-code contract) and the download-lifecycle state machine with its invariants live here.

> **Rule:** when the prose in `04-apis.md` and a schema here disagree, the schema wins. Fix the prose.

## What lives here

| Artifact | Purpose | Enforced by |
|---|---|---|
| `innertube-player-request.schema.json` | Shape of the JSON body we POST to `https://www.youtube.com/youtubei/v1/player` (ANDROID client) | Contract tests (`contract-tests.md` § 1) + runtime construction of the request |
| `innertube-player-response.schema.json` | Shape of the JSON response we consume from the same endpoint — the parts we depend on | Contract tests (`contract-tests.md` § 2) + runtime partial parsing |
| `caption-track.schema.json` | Shape of the **parsed** form of timedtext captions (not the raw XML) | Contract tests (`contract-tests.md` § 3) + runtime parser |
| `cli-exit-codes.md` | Canonical exit-code contract (category → code → message) | Integration tests (`contract-tests.md` § 4) |
| `state-machine.md` | Download-lifecycle state diagram + numbered invariants INV-1..INV-16 | Property tests (`contract-tests.md` § 5) |
| `contract-tests.md` | Index of contract tests derived from the schemas and state machine — positive cases, negative cases, edge cases | Test suite in `yt-core/src/test/` |
| `fixtures/` | Synthesized fixture files (one per scenario in `contract-tests.md`). Real captures replace these in Phase 5. | All contract tests |

> **MVP scope note.** `output-metadata.schema.json` (for a future `--print-json` emission) was considered and deliberately skipped for MVP — we don't ship that feature. It will land when the feature does.

## Phase

This folder is populated during **Phase 3 — Formal contracts**, after Phase 2 (Design) is approved.

## Conventions

- **JSON Schema draft:** Draft 2020-12 (`"$schema": "https://json-schema.org/draft/2020-12/schema"`).
- **Schema IDs:** every schema has a stable `$id` of the form `https://github.com/srk/youtubeDownloader/<name>.schema.json/v<N>`. Major-version bumps are not backward-compatible.
- **Pinned date:** every schema for a reverse-engineered wire format carries an `x-captured-on: YYYY-MM-DD` note in its `description`, recording when the shape was last observed live. YouTube changes responses without notice — the captured date tells a reader how stale the schema might be.
- **Examples:** every schema has at least one `examples` entry (positive case) and negative examples documented in `contract-tests.md`.
- **Partial-parse pragmatism:** the InnerTube player response is deeply nested and carries fields we don't need. Our schema describes only the fields we depend on, and uses `additionalProperties: true` at every nested level so unknown fields don't fail validation. This is explicitly documented at the top of `innertube-player-response.schema.json`.
- **Fixture validation:** every committed fixture validates against its schema. A synthesized fixture that fails validation is a bug in the fixture or the schema; one of the two is fixed before merge.
