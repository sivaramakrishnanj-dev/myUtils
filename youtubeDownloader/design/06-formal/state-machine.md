---
doc: state-machine
last_reviewed: 2026-05-03
phase: 3-formal
status: draft
---

# Download Lifecycle State Machine

This document is the **formal state machine** for one `YoutubeDownloader.download(...)` invocation. The same diagram appears in [`03-data-model.md`](../03-data-model.md) § 3 for the data-model reader's benefit; this file is **authoritative** — if the two diverge, this file wins.

> **Role in Phase 5:** property tests drive random transition sequences through this machine and assert every invariant at every intermediate state. Invariants are numbered (INV-1, INV-2, ...) and stable — test IDs reference them.

---

## 1. Diagram

```mermaid
stateDiagram-v2
    [*] --> INIT

    INIT --> RESOLVING: download(request) called

    RESOLVING --> PARSING_PLAYER_RESPONSE: InnerTube 200 OK
    RESOLVING --> TERMINATED: network / retry-exhausted → exit 10
    RESOLVING --> TERMINATED: ffmpeg probe failed (when needed) → exit 60

    PARSING_PLAYER_RESPONSE --> SELECTING_FORMATS: response parsed OK
    PARSING_PLAYER_RESPONSE --> TERMINATED: parse error → exit 11
    PARSING_PLAYER_RESPONSE --> TERMINATED: playabilityStatus UNPLAYABLE/LOGIN_REQUIRED/ERROR/AGE_VERIFICATION_REQUIRED → exit 20
    PARSING_PLAYER_RESPONSE --> TERMINATED: isLive OR LIVE_STREAM_OFFLINE → exit 21

    SELECTING_FORMATS --> DOWNLOADING_VIDEO: video selected AND audio selected (Flow A start)
    SELECTING_FORMATS --> DOWNLOADING_AUDIO: audioOnly mode (Flow B start)
    SELECTING_FORMATS --> DOWNLOADING_CAPTIONS: transcriptOnly mode (Flow C start)
    SELECTING_FORMATS --> TERMINATED: all formats ciphered → exit 22
    SELECTING_FORMATS --> TERMINATED: no matching format → exit 30
    SELECTING_FORMATS --> TERMINATED: no caption for --lang chain → exit 40

    DOWNLOADING_VIDEO --> DOWNLOADING_AUDIO: video bytes complete
    DOWNLOADING_VIDEO --> TERMINATED: stream retry exhausted → exit 10
    DOWNLOADING_VIDEO --> TERMINATED: filesystem write failed → exit 70

    DOWNLOADING_AUDIO --> MUXING: audio bytes complete AND video path
    DOWNLOADING_AUDIO --> DOWNLOADING_CAPTIONS: audio bytes complete AND --transcript AND audioOnly
    DOWNLOADING_AUDIO --> DOWNLOADING_THUMBNAIL: audio bytes complete AND --thumbnail AND no mux AND no transcript
    DOWNLOADING_AUDIO --> WRITING_OUTPUTS: audio bytes complete AND audioOnly AND m4a AND no transcript AND no thumbnail
    DOWNLOADING_AUDIO --> TRANSCODING_AUDIO: audio bytes complete AND audioOnly AND mp3
    DOWNLOADING_AUDIO --> TERMINATED: stream retry exhausted → exit 10
    DOWNLOADING_AUDIO --> TERMINATED: filesystem write failed → exit 70

    MUXING --> DOWNLOADING_CAPTIONS: mux done AND --transcript
    MUXING --> DOWNLOADING_THUMBNAIL: mux done AND --thumbnail AND no transcript
    MUXING --> TRANSCODING_AUDIO: mux done AND --audio-format mp3
    MUXING --> WRITING_OUTPUTS: mux done AND no further work
    MUXING --> TERMINATED: ffmpeg mux failed → exit 60

    TRANSCODING_AUDIO --> DOWNLOADING_CAPTIONS: transcode done AND --transcript
    TRANSCODING_AUDIO --> DOWNLOADING_THUMBNAIL: transcode done AND --thumbnail AND no transcript
    TRANSCODING_AUDIO --> WRITING_OUTPUTS: transcode done AND no further work
    TRANSCODING_AUDIO --> TERMINATED: ffmpeg transcode failed → exit 60

    DOWNLOADING_CAPTIONS --> DOWNLOADING_THUMBNAIL: captions done AND --thumbnail
    DOWNLOADING_CAPTIONS --> WRITING_OUTPUTS: captions done AND no further work
    DOWNLOADING_CAPTIONS --> TERMINATED: caption fetch failed AND transcript is the only output → exit 10 or 40

    DOWNLOADING_THUMBNAIL --> WRITING_OUTPUTS: thumbnail done (or failed; failure non-fatal)

    WRITING_OUTPUTS --> CLEANING_TEMP: all files written
    WRITING_OUTPUTS --> TERMINATED: output file exists → exit 50
    WRITING_OUTPUTS --> TERMINATED: disk / permissions → exit 70

    CLEANING_TEMP --> DONE: .yt-tmp/ emptied and removed if possible

    DONE --> [*]
    TERMINATED --> [*]
```

> The diagram shows all transitions including failure edges. SIGINT / SIGTERM transitions are modelled separately in § 3 because they can interrupt any non-terminal state.

---

## 2. State definitions

| State | Orchestrator meaning | `ProgressEvent.Phase` emitted to listener |
|---|---|---|
| `INIT` | Entry point; constructing `DownloadContext`, installing shutdown hook | — (no event) |
| `RESOLVING` | `UrlParser.parse()` + `InnerTubeClient.fetchPlayer()` + optional `FfmpegMuxer.probeVersion()` | `RESOLVING` |
| `PARSING_PLAYER_RESPONSE` | `PlayerResponseExtractor.parse(bytes)` | `RESOLVING` (merged) |
| `SELECTING_FORMATS` | `FormatSelector.select(request, response)` — picks video, audio, caption, thumbnail | `RESOLVING` (merged) |
| `DOWNLOADING_VIDEO` | `StreamDownloader.download(videoFormat)` → `video.part` | `DOWNLOADING_VIDEO` |
| `DOWNLOADING_AUDIO` | `StreamDownloader.download(audioFormat)` → `audio.part` | `DOWNLOADING_AUDIO` |
| `MUXING` | `FfmpegMuxer.mux(video.part, audio.part)` → `out.mp4` | `MUXING` |
| `TRANSCODING_AUDIO` | `FfmpegMuxer.transcodeMp3(audio.part)` → `out.mp3` | `TRANSCODING` |
| `DOWNLOADING_CAPTIONS` | `CaptionDownloader.download(track.baseUrl)` → XML bytes → `CaptionConverter` | `DOWNLOADING_CAPTIONS` |
| `DOWNLOADING_THUMBNAIL` | `ThumbnailDownloader.download(thumbnailUrl)` → `.jpg` | `DOWNLOADING_THUMBNAIL` |
| `WRITING_OUTPUTS` | `OutputWriter.write(...)` for every output file | `WRITING_OUTPUTS` |
| `CLEANING_TEMP` | Delete `.yt-tmp/*` and directory (success path only) | — |
| `DONE` | Successful terminal state; `DownloadResult` returned to caller | `DONE` |
| `TERMINATED` | Failure / signal terminal state; exception thrown to caller (or exit code for CLI) | — (exception, not event) |

---

## 3. SIGINT / SIGTERM handling

Signals interrupt any non-terminal state. The shutdown hook installed in `INIT`:

1. Sets `DownloadContext.cancelled = true` (internal flag).
2. Interrupts the current blocking operation:
   - For `DOWNLOADING_*` — aborts the active HTTP body read.
   - For `MUXING` / `TRANSCODING_AUDIO` — sends SIGTERM to the child ffmpeg, waits 5 s, SIGKILL.
   - For `WRITING_OUTPUTS` — closes any open `FileOutputStream`.
3. Transitions to `TERMINATED` via `ErrorMapper` with a synthetic `ShutdownException`.
4. Exits the JVM with code `130` (SIGINT) or `143` (SIGTERM) per the CLI exit-code contract.

**`.yt-tmp/` is retained** on signal-induced termination — same as on failure (`02-architecture.md` § 5; `05-operations.md` § 1.3).

A signal received in `CLEANING_TEMP` (cleanup already in progress) is honoured — cleanup completes if possible, otherwise `.yt-tmp/` is left as-is. A signal in `DONE` is moot; the JVM is already exiting.

---

## 4. Invariants

These invariants are the **formal guarantees** of the state machine. Phase 5 property tests assert each one at every intermediate state. IDs are stable; tests reference them.

### Structural invariants (about the machine itself)

- **INV-1 (single terminal path).** Every run enters exactly one of `{DONE, TERMINATED}` and does not leave. A run never re-enters a working state after `TERMINATED`.

- **INV-2 (DAG on success).** On the happy path (entering `DONE`), the sequence of states is a strict topological order — no state is visited twice. On the failure path, the same property holds up to the point of `TERMINATED`.

- **INV-3 (state → phase mapping is functional).** Every state in § 2 maps to at most one `ProgressEvent.Phase` value. The listener never sees two distinct phases emitted for the same state.

- **INV-4 (terminal states never emit progress).** `DONE` emits a final `DONE` phase event; `TERMINATED` emits nothing (an exception propagates instead).

### Resource invariants (about what the process holds)

- **INV-5 (one `DownloadContext` per run).** No shared mutable state between runs; concurrent library calls each have their own context.

- **INV-6 (`.yt-tmp/` lifecycle).** `.yt-tmp/` is created at most once per run, before `DOWNLOADING_VIDEO` / `DOWNLOADING_AUDIO` (whichever runs first). It is deleted only when `CLEANING_TEMP` runs, which is only reached from `WRITING_OUTPUTS → CLEANING_TEMP → DONE`. It is retained on every other terminal path (INV-1 intersection with "not via `CLEANING_TEMP`").

- **INV-7 (stream files open ≤ 1 at a time).** In `DOWNLOADING_VIDEO` and `DOWNLOADING_AUDIO`, at most one of `video.part` / `audio.part` is open for write at any instant. The states are strictly sequential — the machine is never in both simultaneously.

- **INV-8 (no active ffmpeg child outside MUXING/TRANSCODING_AUDIO).** A child ffmpeg process exists only while the state is `MUXING` or `TRANSCODING_AUDIO`. On state exit, the process has terminated (normally or via SIGTERM/SIGKILL).

- **INV-9 (single InnerTube request).** Exactly one HTTP POST to `/youtubei/v1/player` is issued per run, in state `RESOLVING`. No retries across states; retries within `RESOLVING` are bounded by `NFR-INNERTUBE-MAX-RETRIES` (`AC-12.4`).

- **INV-10 (ffmpeg probe locality).** If ffmpeg is needed for the run, `probeVersion()` executes exactly once and only inside `RESOLVING`. If not needed (transcript-only or audio-only-m4a paths), it is not invoked at all (`AC-13.5`).

### Correctness invariants (about outputs and error paths)

- **INV-11 (exit code from `ErrorMapper` only).** No component other than `ErrorMapper` decides the exit code. Every path into `TERMINATED` carries a `YoutubeDownloaderException` whose `exitCode()` value is what the CLI exits with.

- **INV-12 (no output leakage on failure).** If the run enters `TERMINATED`, no final output file (`.mp4`, `.mp3`, `.m4a`, `.srt`, `.txt`, `.jpg`) exists at the user-facing `<output-dir>`. Partial files in `.yt-tmp/` are allowed.

- **INV-13 (output files written atomically-enough).** `WRITING_OUTPUTS` writes each final file either in full or not at all — a partial final file is never left in `<output-dir>`. (Implementation: write to a sibling with `.partial` suffix in `<output-dir>`, then `Files.move(ATOMIC_MOVE)` on success. Filesystem-crash-safe atomicity is not required for MVP.)

- **INV-14 (overwrite check before any output write).** Before entering `WRITING_OUTPUTS`, every intended output path is checked against existing files. If any exists and `--force` is not set, transition is directly to `TERMINATED` with exit `50`; no partial writes happen.

- **INV-15 (progress monotonicity).** Within `DOWNLOADING_VIDEO` / `DOWNLOADING_AUDIO` / `DOWNLOADING_CAPTIONS` / `DOWNLOADING_THUMBNAIL`, `ProgressEvent.bytesDownloaded` is monotonically non-decreasing within a single stream attempt. On retry (byte-0 restart per `NFR-STREAM-MAX-RETRIES`), the counter resets to 0.

- **INV-16 (ASR fallback only flipped in `SELECTING_FORMATS`).** `DownloadResult.usedAsrFallback` is set by `FormatSelector` during caption selection (`SELECTING_FORMATS`). Later states never change its value.

---

## 5. Flow → state path reference

For quick orientation:

| Flow | Path through the machine |
|---|---|
| A — video + audio → MP4 | `INIT → RESOLVING → PARSING_PLAYER_RESPONSE → SELECTING_FORMATS → DOWNLOADING_VIDEO → DOWNLOADING_AUDIO → MUXING → [DOWNLOADING_CAPTIONS] → [DOWNLOADING_THUMBNAIL] → WRITING_OUTPUTS → CLEANING_TEMP → DONE` |
| A' — video + audio + MP3 transcode | `... MUXING → TRANSCODING_AUDIO → ...` (bracketed states unchanged) |
| B — audio-only m4a | `INIT → RESOLVING → PARSING_PLAYER_RESPONSE → SELECTING_FORMATS → DOWNLOADING_AUDIO → [DOWNLOADING_CAPTIONS] → [DOWNLOADING_THUMBNAIL] → WRITING_OUTPUTS → CLEANING_TEMP → DONE` |
| B' — audio-only MP3 | `INIT → RESOLVING → PARSING_PLAYER_RESPONSE → SELECTING_FORMATS → DOWNLOADING_AUDIO → TRANSCODING_AUDIO → [...] → WRITING_OUTPUTS → CLEANING_TEMP → DONE` |
| C — transcript-only | `INIT → RESOLVING → PARSING_PLAYER_RESPONSE → SELECTING_FORMATS → DOWNLOADING_CAPTIONS → [DOWNLOADING_THUMBNAIL] → WRITING_OUTPUTS → CLEANING_TEMP → DONE` |

Bracketed states are conditional on the corresponding `DownloadRequest` flag.

---

## 6. What this document does not pin

- **Implementation choices about how transitions are dispatched** (switch statement, state pattern, method chaining) — a Phase 4/5 concern.
- **Timing** — how long a state can take. Upper bounds are in NFRs (`NFR-*-TIMEOUT`); they are not part of the state machine semantics.
- **Concurrent runs** — this machine describes one run in isolation. Library thread-safety for N concurrent runs is the `YoutubeDownloader`-class-level contract (`04-apis.md` § 3.2.5), not a state-machine property.
