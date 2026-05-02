---
doc: formal-index
last_reviewed: 2026-05-02
status: pending   # populated during Phase 3
---

# 06 — Formal Specs

This folder holds the **machine-checkable specs** for youtubeDownloader. Every data shape that crosses a process boundary (InnerTube wire messages, caption tracks, our output metadata JSON, our CLI exit-code contract) and the download-lifecycle state machine with its invariants live here.

> **Rule:** when the prose in `04-apis.md` and a schema here disagree, the schema wins. Fix the prose.

## What lives here

| Artifact | Purpose | Enforced by |
|---|---|---|
| `innertube-player-request.schema.json` | Shape of the JSON body we POST to `https://www.youtube.com/youtubei/v1/player` (ANDROID client) | Contract tests + runtime construction of the request |
| `innertube-player-response.schema.json` | Shape of the JSON response we consume from the same endpoint — the parts we depend on | Contract tests + runtime validation / partial parsing |
| `caption-track.schema.json` | Shape of the XML or JSON timed-text response from YouTube's caption endpoint for a single track | Contract tests + runtime parsing |
| `output-metadata.schema.json` | Shape of the JSON metadata file this tool writes alongside each download | Contract tests + integration tests |
| `cli-exit-codes.md` | This tool's CLI exit-code contract (success, invalid URL, network failure, ffmpeg missing, etc.) | Integration tests |
| `state-machine.md` | Download-lifecycle state diagram (resolve → fetch metadata → select formats → download → mux → emit) + invariants that must hold in every state | Property tests over the state machine |
| `contract-tests.md` | Index of contract tests derived from the schemas and state machine — positive cases, negative cases, edge cases | Test suite in `src/test/` |

## Phase

This folder is populated during **Phase 3 — Formal contracts**, after Phase 2 (Design) is approved.

## Conventions

- **JSON Schema draft:** Draft 2020-12 (`"$schema": "https://json-schema.org/draft/2020-12/schema"`).
- **Schema IDs:** every schema has a stable `$id` of the form `https://github.com/srk/youtubeDownloader/<name>.schema.json/v<N>`. Major-version bumps are not backward-compatible.
- **Pinned date:** every schema for a reverse-engineered wire format carries a `x-captured-on: YYYY-MM-DD` note in its `description`, recording when the shape was last observed live. YouTube changes responses without notice — the captured date tells a reader how stale the schema might be.
- **Examples:** every schema has at least one `examples` entry (positive case) and a negative example documented in `contract-tests.md`.
- **Partial-parse pragmatism:** the InnerTube player response is deeply nested and carries fields we don't need. Our schema describes only the fields we depend on, and uses `additionalProperties: true` at every nested level so unknown fields don't fail validation. This is explicitly documented at the top of `innertube-player-response.schema.json`.
