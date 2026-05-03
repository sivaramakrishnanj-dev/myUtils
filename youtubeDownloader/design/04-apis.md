---
doc: apis
last_reviewed: 2026-05-03
phase: 2-design
status: draft
review:
approved_in:
---

# 04 — APIs

This document is the **contract reference** for every boundary youtubeDownloader crosses: four external contracts we consume (YouTube InnerTube, video CDN, timedtext, thumbnail CDN), one external contract we invoke (ffmpeg CLI), one contract we provide to the shell (our CLI), and one contract we provide to Java callers (our library API).

> **Prose vs formal:** this document describes contracts in prose. Where a contract has a wire-format shape, the **machine-checkable schema lives in [`06-formal/`](./06-formal/) (Phase 3) and is authoritative**. When prose here disagrees with a schema there, the schema wins and the prose is fixed. For contracts without a wire format (CLI flag names, library method signatures, ffmpeg command-line syntax), prose here is authoritative.

---

## 1. External contracts we consume

### 1.1 YouTube InnerTube `/youtubei/v1/player`

**Endpoint:** `https://www.youtube.com/youtubei/v1/player`
**Method:** `POST`
**Called by:** `InnerTubeClient` (`02-architecture.md` § 1.2.1) per ADR 0001
**Called how often:** exactly once per `download(...)` invocation (AC-12.3)

#### 1.1.1 Request headers

| Header | Value | Source |
|---|---|---|
| `Content-Type` | `application/json` | — |
| `User-Agent` | `com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip` | `NFR-ANDROID-USER-AGENT` |
| `X-YouTube-Client-Name` | `3` | Canonical ANDROID client ID |
| `X-YouTube-Client-Version` | `19.09.37` | `NFR-ANDROID-CLIENT-VERSION` |
| `Accept-Language` | `en-US,en;q=0.9` | Matches `hl` in body |

#### 1.1.2 Request body shape

```json
{
  "videoId": "<11-char-id>",
  "context": {
    "client": {
      "clientName": "ANDROID",
      "clientVersion": "19.09.37",
      "androidSdkVersion": 34,
      "hl": "en",
      "gl": "US",
      "osName": "Android",
      "osVersion": "14",
      "platform": "MOBILE"
    }
  }
}
```

All values except `videoId` are constants pinned in Phase 1c NFRs (`NFR-ANDROID-CLIENT-VERSION`, `NFR-ANDROID-SDK-VERSION`, `NFR-INNERTUBE-HL`, `NFR-INNERTUBE-GL`). The schema is formalised in [`06-formal/innertube-player-request.schema.json`](./06-formal/innertube-player-request.schema.json) (Phase 3).

#### 1.1.3 Response shape — fields the tool consumes

Status: `200 OK` on success; any non-200 maps to exit code `10` (network) after retries are exhausted (AC-12.4).

**Consumed fields** (everything else ignored per ADR 0004):

```
{
  "videoDetails": {
    "videoId": string,           // must equal request videoId
    "title": string,
    "isLive": boolean,
    "isPrivate": boolean,
    "audioLanguage": string?     // optional; BCP-47; used by AC-8.1 step 3
    "thumbnail": { "thumbnails": [ { "url": string, "width": int, "height": int }, ... ] }
  },
  "playabilityStatus": {
    "status": "OK" | "UNPLAYABLE" | "LIVE_STREAM_OFFLINE" | "LOGIN_REQUIRED" | "ERROR" | "AGE_VERIFICATION_REQUIRED",
    "reason": string?            // human-readable when status != OK
  },
  "streamingData": {
    "adaptiveFormats": [
      {
        "itag": int,
        "mimeType": string,        // e.g. "video/mp4; codecs=\"avc1.640028\""
        "bitrate": long,
        "width": int?,             // video streams only
        "height": int?,            // video streams only
        "fps": int?,               // video streams only
        "audioSampleRate": string?,// audio streams only, base-10 string
        "contentLength": string?,  // base-10 string
        "url": string?,            // present when no signatureCipher
        "signatureCipher": string? // present when URL is cipher-protected; mutually exclusive with "url"
      },
      ...
    ]
  },
  "captions": {
    "playerCaptionsTracklistRenderer": {
      "captionTracks": [
        {
          "baseUrl": string,       // timedtext URL (see § 1.3)
          "languageCode": string,  // BCP-47
          "kind": string?,         // "asr" for auto-generated; absent for manual
          "name": { "simpleText": string }   // human name, e.g. "English"
        },
        ...
      ]
    }
  }
}
```

Full schema in [`06-formal/innertube-player-response.schema.json`](./06-formal/innertube-player-response.schema.json) (Phase 3).

#### 1.1.4 Error responses

| Status | Meaning | Exit code |
|---|---|---|
| `200 OK`, `playabilityStatus.status == "OK"` | Success | — (continue) |
| `200 OK`, `playabilityStatus.status != "OK"` | Video unavailable / live / age-restricted / login-required | `20` (unavailable) or `21` (live) per AC-5.2 |
| `400 Bad Request` | Request malformed — our fault, not retryable | `11` (parse error — our shape assumption wrong) |
| `403 Forbidden` | Client version deprecated; request not signed properly | `10` (network) — but retry is pointless; OQ-A early-warning signal |
| `429 Too Many Requests` | Rate-limited by YouTube anti-abuse | `10` (network) with retry per AC-12.4 |
| `5xx` | Server-side — retryable | `10` (network) with retry per AC-12.4 |

### 1.2 YouTube video CDN (`*.googlevideo.com`)

**Called by:** `StreamDownloader` (`02-architecture.md` § 1.2.2)
**Called how often:** once per selected stream (typically 2 for video + audio; 1 for audio-only)

#### 1.2.1 Request

The URL is taken verbatim from `streamingData.adaptiveFormats[N].url` in the InnerTube response. It is a fully-signed URL with embedded time-limited authentication parameters (`expire`, `ei`, `ip`, `signature`, etc.) — no additional signing on our side. It is valid for approximately 6 hours from generation.

| Header | Value |
|---|---|
| `User-Agent` | Same `NFR-ANDROID-USER-AGENT` as InnerTube |
| `Range` | `bytes=<start>-` on retry (byte-0 restart per `NFR-STREAM-MAX-RETRIES`) |

**Method:** `GET`

#### 1.2.2 Response

- `Status: 200 OK` (or `206 Partial Content` on `Range` request)
- `Content-Length` present — used for progress total (AC-4.1)
- `Content-Type` matches InnerTube's `mimeType` (one of `video/mp4`, `audio/mp4`, `video/webm`, `audio/webm`)
- Body: raw media bytes. Streamed through to the `.part` file; never buffered in memory.

#### 1.2.3 Failure handling

| Condition | Response | Exit code |
|---|---|---|
| `403 Forbidden` | Signed URL expired | `10` network — not retryable with same URL (would need a fresh `/player` call = new run) |
| `404 Not Found` | Format gone since InnerTube response | `10` network — not retryable |
| Timeout (idle > `NFR-NETWORK-TIMEOUT-READ`) | Stalled connection | `10` network — retryable up to `NFR-STREAM-MAX-RETRIES` |
| Connection reset | Transient | `10` network — retryable |

### 1.3 YouTube timedtext endpoint

**Called by:** `CaptionDownloader` (`02-architecture.md` § 1.2.2)
**Called how often:** zero or one per run (only when `--transcript` requested)

#### 1.3.1 Request

The URL is the `baseUrl` from the selected `CaptionTrack` in the InnerTube response. No query-parameter modification by us in MVP — we request the default format that YouTube returns.

**Method:** `GET`
**Headers:** same `User-Agent` as InnerTube.

#### 1.3.2 Response

- `Status: 200 OK`
- `Content-Type: text/xml; charset=utf-8`
- Body is an XML document of shape:

```xml
<?xml version="1.0" encoding="utf-8" ?>
<transcript>
  <text start="0.12" dur="1.68">Hello and welcome</text>
  <text start="1.80" dur="2.40">to this video</text>
  ...
</transcript>
```

Start and duration are in **seconds** as decimal strings. Text content has HTML entities encoded (`&amp;`, `&quot;`, `&#39;`, etc.); we decode them during conversion (AC-6.3). Schema formalised in [`06-formal/caption-track.schema.json`](./06-formal/caption-track.schema.json) (Phase 3).

#### 1.3.3 Failure handling

| Condition | Response | Exit code |
|---|---|---|
| Empty `<transcript>` body | Caption track is listed but has no cues | `40` (caption unavailable — atypical) |
| HTTP non-200 | Transient or permanent | `10` network — no retry; caption failure is non-fatal when combined with media (just a warning) but fatal when `--transcript` is the only requested output |
| Unexpected XML shape | Schema mismatch | `11` (parse error) |

### 1.4 YouTube thumbnail CDN (`i.ytimg.com`)

**Called by:** `ThumbnailDownloader` (`02-architecture.md` § 1.2.2)
**Called how often:** zero or one per run (only when `--thumbnail` requested)

URL from `videoDetails.thumbnail.thumbnails[]`; we select the entry with the largest `width` × `height`.

**Method:** `GET`
**Response:** `image/jpeg` bytes, written verbatim to `<base>.jpg`. No image processing, no re-encoding, no conversion.

**Failure handling:** thumbnail fetch failure is a WARN-level log event, never a run failure (even when `--thumbnail` is the only requested output — in that case the run succeeds with a warning and no thumbnail file).

---

## 2. External contracts we invoke

### 2.1 ffmpeg CLI

**Called by:** `FfmpegMuxer` (`02-architecture.md` § 1.2.3) per ADR 0003
**Prerequisite:** `ffmpeg` binary on `PATH`, version ≥ `NFR-MIN-FFMPEG-VERSION = 4.0` (AC-13.1, AC-13.3)

#### 2.1.1 Version probe

```
ffmpeg -version
```

**Parsing:** first line matches `^ffmpeg version (\S+)`. The captured version is split on `.` into `(major, minor, patch)`. Compared as integers to `NFR-MIN-FFMPEG-VERSION`.

**Exit status expected:** `0`. Any non-zero → AC-13.2 failure.

#### 2.1.2 Mux invocation (Flow A — video + audio → MP4)

```
ffmpeg \
    -hide_banner \
    -loglevel error \                    # info when --debug (NFR-FFMPEG-LOGLEVEL)
    -i <video.part> \
    -i <audio.part> \
    -c copy \
    -map 0:v:0 \
    -map 1:a:0 \
    -y \                                  # overwrite output (the output path is always unique per run)
    <out.mp4>
```

**Exit status:** `0` on success. Any non-zero → AC-13.4 path: capture last `NFR-FFMPEG-STDERR-LINES = 20` lines of stderr, throw `FfmpegException`, map to exit `60`.

**Timeout:** `NFR-FFMPEG-INVOCATION-TIMEOUT = 600 s`.

#### 2.1.3 Transcode invocation (audio-only → MP3)

```
ffmpeg \
    -hide_banner \
    -loglevel error \                    # info when --debug
    -i <audio.part> \
    -c:a libmp3lame \
    -b:a 192k \                           # NFR-DEFAULT-MP3-BITRATE
    -y \
    <out.mp3>
```

Same timeout and failure semantics as mux.

#### 2.1.4 Signal handling

On JVM SIGINT or SIGTERM, the orchestrator's shutdown hook (`02-architecture.md` § 5) sends SIGTERM to the child ffmpeg process, waits 5 s, then SIGKILL. ffmpeg's stderr buffer is flushed before kill.

---

## 3. Contracts we provide

### 3.1 CLI contract

**Invocation:** `java -jar youtube-downloader-1.0.0.jar [OPTIONS] <URL>`

Exit codes per AC-5.2, canonicalised in `06-formal/cli-exit-codes.md` (Phase 3).

#### 3.1.1 Positional arguments

| Argument | Required | Description |
|---|---|---|
| `<URL>` | yes | A YouTube video URL in one of the four shapes accepted by AC-1.1 |

#### 3.1.2 Flags

Flag names are final at this Phase 2 lock; Phase 5 may not rename them without a new review round on this file.

| Flag | Type | Default | Description |
|---|---|---|---|
| `--video` | bool | `true` when neither `--audio-only` nor `--transcript`/`--thumbnail` alone is given | Include muxed MP4 output (US-1) |
| `--audio-only` | bool | `false` | Audio-only mode (US-2) — disables `--video` implicitly |
| `--audio-format <fmt>` | `m4a` \| `mp3` | `m4a` | Audio format (AC-2.3, AC-2.4). `mp3` triggers ffmpeg transcode at `NFR-DEFAULT-MP3-BITRATE` |
| `--transcript` | bool | `false` | Include SRT + TXT transcript (US-6) |
| `--lang <code>` | string (BCP-47) | unset (preference chain) | Caption language (AC-8.1, AC-8.2) |
| `--no-asr` | bool | `false` | Refuse ASR fallback for captions (AC-7.4) |
| `--thumbnail` | bool | `false` | Include thumbnail (.jpg) |
| `--max-height <px>` | int | `1080` | Max video height; `0` = uncapped (AC-1.3) |
| `--output-dir <path>` | path | current working directory | Output directory (AC-3.1, AC-3.2); created if missing |
| `--output <name>` | string | derived from title (AC-3.3) | Base output filename (AC-3.5); extension appended per output type |
| `--force` | bool | `false` | Overwrite existing outputs (AC-3.6) |
| `--ffmpeg-location <path>` | path | `ffmpeg` on `PATH` | Override ffmpeg binary location |
| `--quiet` | bool | `false` | Suppress progress output (AC-4.4); errors still emitted |
| `--debug` | bool | `false` | Emit full stack traces on failure (AC-5.5); set SLF4J to `DEBUG`; set ffmpeg `-loglevel info` |
| `--version` | bool | — | Print tool version and exit `0` |
| `--help` / `-h` | bool | — | Print help and exit `0` |

#### 3.1.3 Output streams

- **stdout**: reserved. MVP does not write to stdout at all. (Future `--print-json` feature would use it.)
- **stderr**: progress (AC-4.*), logs (AC-10.*), errors (AC-5.1).

#### 3.1.4 Exit codes

Canonical mapping from AC-5.2:

| Code | Category |
|---|---|
| 0 | Success |
| 2 | Argument / URL parse error |
| 10 | Network failure |
| 11 | InnerTube response parse error |
| 20 | Video unavailable (private, deleted, login-required, age-restricted) |
| 21 | Video live / premiere |
| 22 | Cipher-protected (AC-5.3) |
| 30 | No matching format |
| 40 | Caption unavailable in requested language |
| 50 | Output file exists |
| 60 | ffmpeg missing / too old / failed |
| 70 | Filesystem error |
| 130 | SIGINT (Ctrl-C) — non-AC-5.2 |
| 143 | SIGTERM — non-AC-5.2 |

### 3.2 Library (Java) API

**Module:** `yt-core` (Maven artifact `com.srk.myutils.yd:yt-core:1.0.0`)
**Package:** `com.srk.myutils.yd.core`

Public surface per AC-9.1 through AC-9.5. Types fully described in [`03-data-model.md`](./03-data-model.md) § 2.

#### 3.2.1 Entrypoint

```java
public final class YoutubeDownloader {

    /**
     * Execute a download per the request.
     *
     * @throws YoutubeDownloaderException on any failure (category per subtype).
     *         Each subtype maps 1:1 with AC-5.2's category set (AC-9.4).
     * @see DownloadRequest#builder()
     */
    public DownloadResult download(DownloadRequest request) throws YoutubeDownloaderException;
}
```

#### 3.2.2 Exception hierarchy

Per AC-9.4, exactly one subclass per AC-5.2 category:

```
YoutubeDownloaderException (abstract)
├── UrlParseException                   -> exit code 2
├── NetworkException                    -> exit code 10
├── InnerTubeParseException             -> exit code 11
├── VideoUnavailableException           -> exit code 20
├── LiveStreamException                 -> exit code 21
├── CipherRequiredException             -> exit code 22
├── NoMatchingFormatException           -> exit code 30
├── CaptionUnavailableException         -> exit code 40
├── OutputExistsException               -> exit code 50
├── FfmpegException                     -> exit code 60
└── FilesystemException                 -> exit code 70
```

Each subclass exposes its AC-5.2 category code via a `public int exitCode()` method so the CLI module can map without parsing class names.

All exceptions are **checked**. The library's contract is that `download()` either returns a `DownloadResult` or throws a specific subtype of `YoutubeDownloaderException`. No `RuntimeException` subtypes are thrown from normal failure paths.

#### 3.2.3 `DownloadRequest.Builder` fluent API

```java
DownloadRequest request = DownloadRequest.builder()
    .url("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    .video(true)
    .audioOnly(false)
    .audioFormat(AudioFormat.M4A)
    .transcript(true)
    .languageCode(LanguageCode.of("en"))
    .noAsr(false)
    .thumbnail(false)
    .maxHeight(1080)
    .outputDir(Paths.get("/tmp/downloads"))
    .outputName(null)                          // null = derive from title
    .force(false)
    .progressListener(new MyProgressListener())
    .build();

DownloadResult result = new YoutubeDownloader().download(request);
```

#### 3.2.4 `ProgressListener`

```java
public interface ProgressListener {
    void onProgress(ProgressEvent event);
}

public record ProgressEvent(
    Phase phase,
    long bytesDownloaded,      // -1 when not applicable
    long totalBytes,           // -1 when unknown
    long bytesPerSecond,       // -1 when not applicable
    Duration elapsed,
    Optional<Duration> etaRemaining
) { }
```

Phase enum values and throttling behaviour per [`03-data-model.md`](./03-data-model.md) § 2.6.

#### 3.2.5 Thread-safety

- `YoutubeDownloader` is **stateless** — safe to share one instance across threads.
- Each call to `download(...)` creates its own `DownloadContext` and background progress thread. No shared mutable state.
- Library embedders may call `download(...)` concurrently from N threads; each run is fully isolated.

#### 3.2.6 SLF4J logging

The library uses SLF4J for all diagnostic output (AC-10.1). It does **not** bundle an SLF4J backend. Callers must provide one (e.g., `slf4j-simple`, `logback`, `log4j2-slf4j-binding`). The CLI module ships `slf4j-simple`.

Logger names follow the convention `com.srk.myutils.yd.core.<ComponentName>` so callers can configure levels per component.

### 3.3 Temp-file contract

During a run, the library creates files under `<output-dir>/.yt-tmp/` per `NFR-TEMP-DIR-STRATEGY`. The directory is:
- Created on first use (`Files.createDirectories`).
- Used for `.part` files during download.
- Cleaned (file-by-file deletion, then `Files.delete` on the directory if empty) on success only.
- **Retained** on failure and on SIGINT/SIGTERM for post-mortem inspection.

Callers may rely on this behaviour but should not **depend** on specific filenames inside `.yt-tmp/` — the naming scheme is an implementation detail and may change in a patch release.

---

## 4. Summary: contract inventory

| # | Contract | Kind | Direction | Pinned by |
|---|---|---|---|---|
| 1 | InnerTube `/player` request/response | HTTP + JSON | Outbound | § 1.1 + `06-formal/innertube-player-*.schema.json` (Phase 3) |
| 2 | Video CDN stream | HTTP + bytes | Outbound | § 1.2 |
| 3 | Timedtext caption | HTTP + XML | Outbound | § 1.3 + `06-formal/caption-track.schema.json` (Phase 3) |
| 4 | Thumbnail image | HTTP + bytes | Outbound | § 1.4 |
| 5 | ffmpeg CLI | exec | Outbound | § 2.1 |
| 6 | our CLI | exec + stderr + exit code | Inbound | § 3.1 + `06-formal/cli-exit-codes.md` (Phase 3) |
| 7 | our library (Java) | method call | Inbound | § 3.2 |

All seven contracts are pinned at Phase 2 design. Changes require a new review round on this file.
