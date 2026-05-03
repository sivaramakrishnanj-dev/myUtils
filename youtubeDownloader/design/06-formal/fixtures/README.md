# Synthesized Fixtures

This folder contains **synthesized** fixture files — minimal hand-crafted examples sized for schema validation in Phase 3. Real captured fixtures arrive in Phase 5 under `yt-core/src/test/resources/fixtures/` with `x-captured-on` dates reflecting real capture times.

The fixtures here exist so:
- Every JSON Schema in this folder has at least one validated positive example.
- `contract-tests.md` has concrete files to reference for its positive / negative case table.
- Phase 5 test writers have templates to clone when capturing real responses.

> **Synthesized data are not real YouTube responses.** Video IDs, URLs, and durations are placeholder values. Do not expect them to resolve against the real InnerTube API.

## Files

| File | Schema | Scenario | Expected validation |
|---|---|---|---|
| `innertube-request-happy.json` | `../innertube-player-request.schema.json` | A well-formed ANDROID request body | VALID |
| `innertube-response-happy.json` | `../innertube-player-response.schema.json` | Public 1080p video with video+audio formats and both manual + ASR English captions | VALID |
| `innertube-response-cipher.json` | `../innertube-player-response.schema.json` | All formats have `signatureCipher` (no direct URL) — triggers AC-5.3 / exit 22 | VALID (the response is well-formed; the failure is a business-logic check in `FormatSelector`) |
| `innertube-response-live.json` | `../innertube-player-response.schema.json` | `videoDetails.isLive: true` — triggers exit 21 | VALID (business-logic check) |
| `innertube-response-unplayable.json` | `../innertube-player-response.schema.json` | `playabilityStatus.status: UNPLAYABLE` — triggers exit 20 | VALID |
| `innertube-response-no-captions.json` | `../innertube-player-response.schema.json` | `captions` field absent entirely — triggers exit 40 when `--transcript` requested | VALID |
| `innertube-response-asr-only.json` | `../innertube-player-response.schema.json` | Only ASR caption tracks; no manual. Tests AC-7.3 fallback logic | VALID |
| `caption-track-happy.json` | `../caption-track.schema.json` | Three sequential cues with HTML entities decoded | VALID |
| `caption-track-empty.json` | `../caption-track.schema.json` | Zero cues — tests AC-6.4 edge case | VALID |

Negative-case examples (shapes that SHOULD fail validation) are described in `../contract-tests.md` but not committed as files — they are synthesized inline in the test code where they are used.
