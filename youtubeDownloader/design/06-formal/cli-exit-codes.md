---
doc: cli-exit-codes
last_reviewed: 2026-05-03
phase: 3-formal
status: draft
---

# CLI Exit-Code Contract

This is the **canonical exit-code contract** for youtubeDownloader. The prose in `04-apis.md` § 3.1.4 and the matrix in `02-architecture.md` § 3 describe the same contract; when they disagree with this file, **this file wins** and the prose is fixed.

> **Stability guarantee:** exit codes 0, 2, 10, 11, 20, 21, 22, 30, 40, 50, 60, 70, 130, 143 are pinned at Phase 3. They will not be renumbered in MVP. New categories added in future phases must use codes not already listed here.

---

## 1. Canonical table

| Code | Category | Detecting component | Trigger | `stderr` message template | Source requirement |
|---|---|---|---|---|---|
| `0` | success | — | All components returned normally | — | AC-5.2 |
| `2` | `args` | `UrlParser` + CLI parser | URL doesn't match the four accepted shapes (AC-1.1); or unknown flag | `Error: args: <detail>` | AC-5.2 |
| `10` | `network` | `InnerTubeClient`, `StreamDownloader`, `CaptionDownloader`, `ThumbnailDownloader` | DNS, TCP, TLS, or HTTP transport failure after all retries exhausted (AC-12.4, `NFR-INNERTUBE-MAX-RETRIES`, `NFR-STREAM-MAX-RETRIES`) | `Error: network: <host>: <cause>` | AC-5.2 |
| `11` | `innertube` | `PlayerResponseExtractor` | JSON does not deserialize into `PlayerResponse`; required field missing; type mismatch; or `playabilityStatus.status` unrecognized | `Error: innertube: response shape unexpected — <field>` | AC-5.2 |
| `20` | `unavailable` | `PlayerResponseExtractor` | `playabilityStatus.status` ∈ `{ UNPLAYABLE, LOGIN_REQUIRED, ERROR, AGE_VERIFICATION_REQUIRED }` | `Error: unavailable: <reason from InnerTube>` | AC-5.2 |
| `21` | `live` | `PlayerResponseExtractor` | `videoDetails.isLive = true` OR `playabilityStatus.status = LIVE_STREAM_OFFLINE` (AC-1.7) | `Error: live: live streams are not supported in this MVP` | AC-5.2, AC-1.7 |
| `22` | `cipher` | `FormatSelector` | All candidate formats have non-empty `signatureCipher` | `Error: cipher: this video requires JavaScript signature deciphering, which is out of scope for this tool. Use yt-dlp for this URL.` | AC-5.2, AC-5.3 |
| `30` | `format` | `FormatSelector` | Non-empty `adaptiveFormats`, but none pass `--max-height` / audio-required filters | `Error: format: no video/audio format matches the selection criteria` | AC-5.2 |
| `40` | `captions` | `FormatSelector` (caption-selection sub-path) | No caption track for `--lang` chain (AC-8.3); only ASR + `--no-asr` (AC-7.4); no caption tracks at all (AC-6.4) | `Error: captions: <specific reason>. Available: <comma-separated list>.` | AC-5.2, AC-6.4, AC-7.4, AC-8.3 |
| `50` | `output` | `OutputWriter` | Intended output file exists and `--force` not given (AC-3.6) | `Error: output: file '<path>' already exists (pass --force to overwrite)` | AC-5.2, AC-3.6 |
| `60` | `ffmpeg` | `FfmpegMuxer` | `ffmpeg -version` fails (AC-13.2); detected version below `NFR-MIN-FFMPEG-VERSION` (AC-13.3); non-zero mux / transcode exit (AC-13.4) | `Error: ffmpeg: <reason>\n<last 20 lines of ffmpeg stderr>` | AC-5.2, AC-13.* |
| `70` | `filesystem` | `OutputWriter`, `StreamDownloader` | Any `IOException` during write, including free-disk probe failure (`NFR-MIN-DISK-FREE`) | `Error: filesystem: <path>: <cause>` | AC-5.2 |
| `130` | `sigint` | shutdown hook | User pressed Ctrl-C (POSIX signal 2 → `128 + 2 = 130`) | No new stderr line; existing error/progress line is flushed | Shell convention |
| `143` | `sigterm` | shutdown hook | Process received SIGTERM (signal 15 → `128 + 15 = 143`) | No new stderr line | Shell convention |

**Message-template conventions:**
- `<placeholder>` is replaced with a runtime value.
- Every AC-5.2 category (codes `2` through `70`) produces exactly one `Error:` line on stderr (AC-5.1, AC-5.4). Stack trace on failures is emitted **only** when `--debug` is set (AC-5.5).
- Signals `130` and `143` do **not** emit an `Error:` line — they are not failures per se. The existing progress line (if any) gets a newline flush so the shell prompt is clean.

---

## 2. Machine-readable appendix

For tests and tooling. Same content as § 1 above, in YAML.

```yaml
exit_codes:
  - code: 0
    category: success
    ac: AC-5.2
    detecting_component: null
    message_template: null
  - code: 2
    category: args
    ac: AC-5.2
    detecting_components: [UrlParser, CliParser]
    message_template: "Error: args: {detail}"
  - code: 10
    category: network
    ac: AC-5.2
    detecting_components: [InnerTubeClient, StreamDownloader, CaptionDownloader, ThumbnailDownloader]
    retryable_before_terminal: true
    message_template: "Error: network: {host}: {cause}"
  - code: 11
    category: innertube
    ac: AC-5.2
    detecting_components: [PlayerResponseExtractor]
    message_template: "Error: innertube: response shape unexpected — {field}"
  - code: 20
    category: unavailable
    ac: AC-5.2
    detecting_components: [PlayerResponseExtractor]
    triggers:
      - "playabilityStatus.status == UNPLAYABLE"
      - "playabilityStatus.status == LOGIN_REQUIRED"
      - "playabilityStatus.status == ERROR"
      - "playabilityStatus.status == AGE_VERIFICATION_REQUIRED"
    message_template: "Error: unavailable: {reason_from_innertube}"
  - code: 21
    category: live
    ac: AC-5.2,AC-1.7
    detecting_components: [PlayerResponseExtractor]
    triggers:
      - "videoDetails.isLive == true"
      - "playabilityStatus.status == LIVE_STREAM_OFFLINE"
    message_template: "Error: live: live streams are not supported in this MVP"
  - code: 22
    category: cipher
    ac: AC-5.2,AC-5.3
    detecting_components: [FormatSelector]
    triggers:
      - "every candidate adaptiveFormats[*].signatureCipher is non-empty"
    message_template: "Error: cipher: this video requires JavaScript signature deciphering, which is out of scope for this tool. Use yt-dlp for this URL."
  - code: 30
    category: format
    ac: AC-5.2
    detecting_components: [FormatSelector]
    message_template: "Error: format: no video/audio format matches the selection criteria"
  - code: 40
    category: captions
    ac: AC-5.2,AC-6.4,AC-7.4,AC-8.3
    detecting_components: [FormatSelector]
    triggers:
      - "no caption tracks at all (AC-6.4)"
      - "only ASR available and --no-asr set (AC-7.4)"
      - "no track matches --lang chain (AC-8.3)"
    message_template: "Error: captions: {reason}. Available: {track_list}."
  - code: 50
    category: output
    ac: AC-5.2,AC-3.6
    detecting_components: [OutputWriter]
    message_template: "Error: output: file '{path}' already exists (pass --force to overwrite)"
  - code: 60
    category: ffmpeg
    ac: AC-5.2,AC-13.2,AC-13.3,AC-13.4
    detecting_components: [FfmpegMuxer]
    triggers:
      - "ffmpeg -version fails (AC-13.2)"
      - "detected version < NFR-MIN-FFMPEG-VERSION (AC-13.3)"
      - "mux or transcode subprocess exits non-zero (AC-13.4)"
    message_template: "Error: ffmpeg: {reason}\\n{last_20_stderr_lines}"
  - code: 70
    category: filesystem
    ac: AC-5.2
    detecting_components: [OutputWriter, StreamDownloader]
    message_template: "Error: filesystem: {path}: {cause}"
  - code: 130
    category: sigint
    convention: "POSIX 128 + signal_number (SIGINT = 2)"
    detecting_component: shutdown-hook
    message_template: null
  - code: 143
    category: sigterm
    convention: "POSIX 128 + signal_number (SIGTERM = 15)"
    detecting_component: shutdown-hook
    message_template: null
```

---

## 3. Category ↔ Java exception mapping

For the library API (AC-9.4, `04-apis.md` § 3.2.2). Maintaining this table in the same file as exit codes prevents the two from drifting.

| Code | Category | Java exception (in `com.srk.myutils.yd.core`) |
|---|---|---|
| `2` | `args` | `UrlParseException` |
| `10` | `network` | `NetworkException` |
| `11` | `innertube` | `InnerTubeParseException` |
| `20` | `unavailable` | `VideoUnavailableException` |
| `21` | `live` | `LiveStreamException` |
| `22` | `cipher` | `CipherRequiredException` |
| `30` | `format` | `NoMatchingFormatException` |
| `40` | `captions` | `CaptionUnavailableException` |
| `50` | `output` | `OutputExistsException` |
| `60` | `ffmpeg` | `FfmpegException` |
| `70` | `filesystem` | `FilesystemException` |

Each exception exposes `int exitCode()` returning the table's `code` column. The CLI's `ExitCodeMapper` catches `YoutubeDownloaderException` and calls `exitCode()` — no string matching, no reflection.

Codes `0`, `130`, `143` have no exception class — they are not failure outcomes (success, SIGINT, SIGTERM respectively).

---

## 4. Invariants

1. **Every non-zero exit from the CLI corresponds to exactly one category in § 1.** The CLI does not emit an undocumented exit code.
2. **Every AC-5.2 category code maps to exactly one library exception subtype** (§ 3). Neither many-to-one nor one-to-many.
3. **stderr contains exactly one `Error:` line per failure run** (AC-5.1). Multiple lines after that only when `--debug` is set and a stack trace follows.
4. **Signal exit codes `130`/`143` do not emit an `Error:` line.** They are user- or OS-initiated, not failures.
5. **The `stderr` message template is stable** for the lifetime of MVP. Tools piping youtubeDownloader can regex-match against the `Error: <category>:` prefix without fear of renames.
