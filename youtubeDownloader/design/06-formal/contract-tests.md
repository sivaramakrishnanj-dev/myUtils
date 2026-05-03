---
doc: contract-tests
last_reviewed: 2026-05-03
phase: 3-formal
status: draft
---

# Contract Tests Index

This is the **implementation checklist for Phase 5 test writing**. Every row in the tables below becomes one JUnit test. Each references a fixture file under `fixtures/` (positive cases) or an inline synthesized shape described in the row (negative cases).

> **Enforcement model:** contract tests run under the default `mvn test` profile per AC-11.3. They load a fixture, validate against the referenced JSON Schema, and either assert the validation passes (positive) or assert it fails with the expected error (negative). Schema validation is performed via the `com.networknt:json-schema-validator` library (Phase 5 build dep).

---

## 1. InnerTube request schema

**Schema:** [`innertube-player-request.schema.json`](./innertube-player-request.schema.json)

### Positive cases

| ID | Fixture | Scenario | Expected |
|---|---|---|---|
| `CT-REQ-1` | `fixtures/innertube-request-happy.json` | Well-formed ANDROID request with videoId `dQw4w9WgXcQ`, v19.09.37, Android 14 SDK 34, hl=en, gl=US | VALID |

### Negative cases (synthesized inline in test code)

| ID | Shape | Expected failure |
|---|---|---|
| `CT-REQ-N1` | `videoId` omitted | Schema validation error: required property `videoId` |
| `CT-REQ-N2` | `videoId` = `"short"` (not 11 chars) | Pattern mismatch |
| `CT-REQ-N3` | `videoId` = `"dQw4w9WgXcQ!"` (12 chars, invalid char) | Pattern mismatch |
| `CT-REQ-N4` | `context.client.clientName` = `"WEB"` (not ANDROID) | const mismatch — ADR 0001 |
| `CT-REQ-N5` | `context.client.clientVersion` = `"19"` (not x.y.z) | Pattern mismatch |
| `CT-REQ-N6` | Extra top-level property `"foo": "bar"` | Schema rejects: `additionalProperties: false` at root |
| `CT-REQ-N7` | `context.client.androidSdkVersion` = `"34"` (string, not integer) | Type mismatch |
| `CT-REQ-N8` | `context.client.gl` = `"usa"` (3 letters) | Pattern mismatch (expects 2-letter ISO code) |

---

## 2. InnerTube response schema

**Schema:** [`innertube-player-response.schema.json`](./innertube-player-response.schema.json)

> `additionalProperties: true` at every nested level — unknown fields never fail validation (ADR 0004). Negative cases target required-field presence and type correctness only.

### Positive cases

| ID | Fixture | Scenario | Expected |
|---|---|---|---|
| `CT-RESP-1` | `fixtures/innertube-response-happy.json` | Public 1080p video; video+audio formats; both manual and ASR captions | VALID |
| `CT-RESP-2` | `fixtures/innertube-response-cipher.json` | All formats have `signatureCipher`; no `url` | VALID (shape well-formed; cipher check is business logic in FormatSelector) |
| `CT-RESP-3` | `fixtures/innertube-response-live.json` | `videoDetails.isLive = true` | VALID (shape OK; exit 21 comes from PlayerResponseExtractor post-parse check) |
| `CT-RESP-4` | `fixtures/innertube-response-unplayable.json` | `playabilityStatus.status = UNPLAYABLE` with `reason` string | VALID |
| `CT-RESP-5` | `fixtures/innertube-response-no-captions.json` | No `captions` field at all | VALID (captions is optional) |
| `CT-RESP-6` | `fixtures/innertube-response-asr-only.json` | Only ASR caption tracks (no manual) | VALID |

### Negative cases (synthesized inline)

| ID | Shape | Expected failure |
|---|---|---|
| `CT-RESP-N1` | `videoDetails` omitted | Required property missing |
| `CT-RESP-N2` | `playabilityStatus` omitted | Required property missing |
| `CT-RESP-N3` | `videoDetails.videoId` = `""` (empty string) | Pattern mismatch |
| `CT-RESP-N4` | `videoDetails.isLive` = `"true"` (string, not boolean) | Type mismatch |
| `CT-RESP-N5` | `streamingData.adaptiveFormats[0].mimeType` = `"application/octet-stream"` (not `video/*` or `audio/*`) | Pattern mismatch |
| `CT-RESP-N6` | `captions.playerCaptionsTracklistRenderer.captionTracks[0].kind` = `"manual"` (not `"asr"` or absent) | Enum violation |
| `CT-RESP-N7` | `videoDetails.thumbnail.thumbnails` = `[]` (empty) | `minItems: 1` violation |
| `CT-RESP-N8` | `videoDetails.audioLanguage` = `"English"` (not BCP-47) | Pattern mismatch |

### Application-level assertions (beyond schema validation)

These are **post-parse assertions** the Phase 5 `PlayerResponseExtractor` test suite makes on top of the schema. They reference the same fixtures.

| ID | Fixture | Assertion | Maps to |
|---|---|---|---|
| `CT-APP-1` | `innertube-response-happy.json` | Parsed `PlayerResponse.videoDetails.videoId.value() == "dQw4w9WgXcQ"` | AC-1.2 |
| `CT-APP-2` | `innertube-response-happy.json` | `PlayerResponse.adaptiveFormats.size() == 3` | — |
| `CT-APP-3` | `innertube-response-happy.json` | `FormatSelector.selectVideo(response, maxHeight=1080)` returns itag `137` (H.264 1080p > 720p tiebreak per AC-1.4) | AC-1.4 |
| `CT-APP-4` | `innertube-response-happy.json` | `FormatSelector.selectAudio(response)` returns itag `140` (only audio format) | AC-1.5 |
| `CT-APP-5` | `innertube-response-cipher.json` | `FormatSelector.selectVideo(...)` throws `CipherRequiredException` | AC-5.3 |
| `CT-APP-6` | `innertube-response-live.json` | `PlayerResponseExtractor` post-parse throws `LiveStreamException` | AC-1.7 |
| `CT-APP-7` | `innertube-response-unplayable.json` | Post-parse throws `VideoUnavailableException` with message "This video is private." | AC-5.2 cat 20 |
| `CT-APP-8` | `innertube-response-no-captions.json` + request with `--transcript` | `FormatSelector.selectCaption(...)` throws `CaptionUnavailableException` | AC-6.4 |
| `CT-APP-9` | `innertube-response-asr-only.json` + request without `--no-asr` | `FormatSelector.selectCaption(..., --lang en)` returns the ASR track AND `DownloadResult.usedAsrFallback == true` | AC-7.3 |
| `CT-APP-10` | `innertube-response-asr-only.json` + request with `--no-asr` | `FormatSelector.selectCaption(...)` throws `CaptionUnavailableException` | AC-7.4 |

---

## 3. Caption track (parsed form) schema

**Schema:** [`caption-track.schema.json`](./caption-track.schema.json)

### Positive cases

| ID | Fixture | Scenario | Expected |
|---|---|---|---|
| `CT-CAP-1` | `fixtures/caption-track-happy.json` | 3 cues, ascending startMs, text with escaped quotes and ampersand | VALID |
| `CT-CAP-2` | `fixtures/caption-track-empty.json` | Zero cues — empty transcript | VALID |

### Negative cases (synthesized inline)

| ID | Shape | Expected failure |
|---|---|---|
| `CT-CAP-N1` | `cues` omitted | Required property missing |
| `CT-CAP-N2` | `cues[0].startMs` = `-1` | `minimum: 0` violation |
| `CT-CAP-N3` | `cues[0].text` omitted | Required property missing |
| `CT-CAP-N4` | Extra top-level property | `additionalProperties: false` |

### Application-level assertions (post-parse)

| ID | Input | Assertion | Maps to |
|---|---|---|---|
| `CT-CAP-APP-1` | Raw timedtext XML with `<text start="0.12" dur="1.68">Hello</text>` | `CaptionConverter.parseXml(...)` yields `CaptionCue(startMs=120, durationMs=1680, text="Hello")` | AC-6.1 |
| `CT-CAP-APP-2` | Raw XML with text `"&quot;hello&quot; &amp; goodbye"` | After conversion, cue text = `"\"hello\" & goodbye"` (AC-6.3 HTML entity decode) | AC-6.3 |
| `CT-CAP-APP-3` | `caption-track-happy.json` | `SrtDocument.toString()` produces canonical SRT (numbered cues, `HH:MM:SS,mmm` timestamps, blank-line separator between cues) | AC-6.2 |
| `CT-CAP-APP-4` | `caption-track-happy.json` | `PlainTextTranscript.toString()` produces plain text — one line per cue, no timestamps, no cue numbers, no blank separators | AC-6.2 |

---

## 4. CLI exit-code contract tests

**Source of truth:** [`cli-exit-codes.md`](./cli-exit-codes.md)

Phase 5 integration tests (under the `-P integration` profile, online) cover:

| ID | Invocation | Expected exit code | Maps to |
|---|---|---|---|
| `CT-EXIT-0` | happy-path YouTube URL (e.g., `dQw4w9WgXcQ`) | `0` | — |
| `CT-EXIT-2a` | no URL argument | `2` | AC-5.2 |
| `CT-EXIT-2b` | URL = `"not a url"` | `2` | AC-1.1, AC-5.2 |
| `CT-EXIT-2c` | unknown flag `--unknown` | `2` | AC-5.2 |
| `CT-EXIT-10` | valid URL with network blocked (test harness via proxy) | `10` | AC-5.2 |
| `CT-EXIT-50` | output file already exists without `--force` | `50` | AC-3.6 |
| `CT-EXIT-60a` | `--ffmpeg-location /nonexistent/ffmpeg` + muxed output | `60` | AC-13.2 |
| `CT-EXIT-60b` | valid ffmpeg but deliberately corrupted `.part` input | `60` | AC-13.4 |

Offline unit tests (default `mvn test` profile) cover the same mapping at the library level by asserting the exception-to-exit-code translation per `cli-exit-codes.md` § 3:

| ID | Thrown exception | Expected `ExitCodeMapper` output |
|---|---|---|
| `CT-EXIT-UNIT-1` | `UrlParseException` | `2` |
| `CT-EXIT-UNIT-2` | `NetworkException` | `10` |
| `CT-EXIT-UNIT-3` | `InnerTubeParseException` | `11` |
| `CT-EXIT-UNIT-4` | `VideoUnavailableException` | `20` |
| `CT-EXIT-UNIT-5` | `LiveStreamException` | `21` |
| `CT-EXIT-UNIT-6` | `CipherRequiredException` | `22` |
| `CT-EXIT-UNIT-7` | `NoMatchingFormatException` | `30` |
| `CT-EXIT-UNIT-8` | `CaptionUnavailableException` | `40` |
| `CT-EXIT-UNIT-9` | `OutputExistsException` | `50` |
| `CT-EXIT-UNIT-10` | `FfmpegException` | `60` |
| `CT-EXIT-UNIT-11` | `FilesystemException` | `70` |

---

## 5. State-machine invariants

**Source of truth:** [`state-machine.md`](./state-machine.md) § 4 (INV-1..INV-16)

Phase 5 implements invariant tests via a state-machine simulator — the `DownloadOrchestrator` is driven through all paths (happy, failure, signal) by a test harness, and all 16 invariants are checked at each intermediate state.

| Invariant | Test strategy |
|---|---|
| INV-1 single terminal path | Property test: for random transition sequences, assert we enter exactly one of `{DONE, TERMINATED}` and do not leave |
| INV-2 DAG on success | Record the state visits on a happy run; assert no state appears twice |
| INV-3 state → phase functional | For every state in `state-machine.md` § 2, assert the mapping table is a function (unique phase per state) |
| INV-4 terminal states never emit progress | Run every failure-path fixture; assert `ProgressListener` never receives events from `TERMINATED` |
| INV-5 single DownloadContext | Call `download(...)` from 10 threads concurrently; assert each has its own context (verified by different `runId` UUIDs) |
| INV-6 .yt-tmp/ lifecycle | Happy run: assert `.yt-tmp/` gone after `DONE`. Failure run: assert `.yt-tmp/` present after `TERMINATED` |
| INV-7 stream files open ≤ 1 | Assert file-handle counts mid-run (platform-specific; use `lsof`-equivalent from test harness) |
| INV-8 no ffmpeg outside MUXING/TRANSCODING | Process-counting check via test harness around expected vs actual subprocess lifetimes |
| INV-9 single InnerTube request | Count POSTs to MockWebServer in a happy run; assert exactly 1 |
| INV-10 ffmpeg probe locality | Assert `ffmpeg -version` called zero times on transcript-only / audio-only-m4a runs |
| INV-11 exit code from ErrorMapper only | Code review gate + a test that mocks every component to throw and asserts `ErrorMapper` is the only path to exit codes |
| INV-12 no output leakage on failure | Failure-path test: assert no `.mp4`/`.m4a`/`.mp3`/`.srt`/`.txt`/`.jpg` exists in `<output-dir>` after `TERMINATED` |
| INV-13 atomic output writes | Inject a crash during write; assert no `.partial` remnant in `<output-dir>` |
| INV-14 overwrite check before any write | Pre-populate `<output-dir>` with one expected output; assert `OutputExistsException` thrown **before** any write attempt |
| INV-15 progress monotonicity | Record `ProgressEvent.bytesDownloaded` sequence within a stream; assert non-decreasing within one attempt |
| INV-16 ASR fallback flipped only in SELECTING_FORMATS | Trace `DownloadResult.usedAsrFallback` across states; assert value set exactly once, in `SELECTING_FORMATS` |

---

## 6. Summary — contract test inventory

| Category | Positive | Negative | App assertions | Total |
|---|---|---|---|---|
| InnerTube request | 1 | 8 | — | 9 |
| InnerTube response | 6 | 8 | 10 | 24 |
| Caption track | 2 | 4 | 4 | 10 |
| CLI exit codes (offline) | — | — | 11 | 11 |
| CLI exit codes (integration) | 1 | 7 | — | 8 |
| State-machine invariants | — | — | 16 | 16 |
| **Total** | **10** | **27** | **41** | **78** |

**Phase 5 test-writing target:** 78 contract tests, every one traceable to at least one AC from Phase 1. Coverage gate per `NFR-UNIT-TEST-COVERAGE-MINIMUM = 80%` applies to library-module code; contract tests cover the wire/boundary surface but not all internal branches.

---

## 7. Open items

Items marked `TODO(capture)` — blocked until real network-accessible fixture captures can replace the synthesized examples.

- `TODO(capture)` — Replace all `fixtures/innertube-response-*.json` with real InnerTube responses captured during Phase 5 development. Update each fixture's companion `.meta.json` with the real `x-captured-on` date and the source URL.
- `TODO(capture)` — Capture a real `caption-track-*.json` from a public video's timedtext endpoint, in the parsed form.
- `TODO(capture)` — Capture a shorts URL's InnerTube response (different video-details shape possible).
