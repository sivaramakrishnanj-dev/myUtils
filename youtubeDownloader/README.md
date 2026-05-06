# youtubeDownloader

A single-site YouTube downloader in Java — video, audio, transcript, thumbnail — from a single video URL.

## Requirements

| Dependency | Version | Required for |
|---|---|---|
| **Java** | 17+ | Build and run |
| **Maven** | 3.9+ | Build |
| **ffmpeg** | ≥ 4.0 (optional) | Video muxing (MP4) and MP3 transcode only |

ffmpeg is **not needed** for audio-only M4A downloads, transcript downloads, or thumbnail downloads (AC-13.5).

## Install

```bash
git clone <repo-url>
cd youtubeDownloader
mvn clean package
```

The fat jar lands at `yt-cli/target/youtube-downloader-1.0.0.jar`.

Verify:

```bash
java -jar yt-cli/target/youtube-downloader-1.0.0.jar --version
java -jar yt-cli/target/youtube-downloader-1.0.0.jar --help
```

## Quickstart

```bash
# Download a full video (best quality ≤ 1080p, muxed MP4)
java -jar yt-cli/target/youtube-downloader-1.0.0.jar "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

# Audio-only (M4A, no ffmpeg needed)
java -jar yt-cli/target/youtube-downloader-1.0.0.jar --audio-only "https://youtu.be/dQw4w9WgXcQ"

# Transcript only (SRT + TXT, no ffmpeg needed)
java -jar yt-cli/target/youtube-downloader-1.0.0.jar --transcript "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
```

## Common commands

```bash
JAR="java -jar yt-cli/target/youtube-downloader-1.0.0.jar"

# Video (default — best ≤ 1080p MP4)
$JAR "URL"

# Video capped at 720p
$JAR --max-height 720 "URL"

# Audio-only as M4A (no re-encoding, no ffmpeg)
$JAR --audio-only "URL"

# Audio-only as MP3 (requires ffmpeg)
$JAR --audio-only --audio-format mp3 "URL"

# Transcript in a specific language
$JAR --transcript --lang fr "URL"

# Transcript, refuse auto-generated captions
$JAR --transcript --no-asr "URL"

# Thumbnail only
$JAR --thumbnail "URL"

# Everything: video + audio + transcript + thumbnail
$JAR --video --audio-only --audio-format mp3 --transcript --thumbnail "URL"

# Custom output directory and filename
$JAR --output-dir ~/videos --output "my-video" "URL"

# Overwrite existing files
$JAR --force "URL"

# Debug mode (verbose logs, stack traces on failure)
$JAR --debug "URL"

# Quiet mode (suppress progress, errors still shown)
$JAR --quiet "URL"

# Custom ffmpeg location
$JAR --ffmpeg-location /usr/local/bin/ffmpeg "URL"
```

## All flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `<URL>` | positional | required | YouTube video URL |
| `--video` | bool | true (when no other output flag) | Include muxed MP4 output |
| `--audio-only` | bool | false | Audio-only mode (disables video) |
| `--audio-format` | `m4a` \| `mp3` | `m4a` | Audio format; `mp3` requires ffmpeg |
| `--transcript` | bool | false | Include SRT + TXT transcript |
| `--lang` | BCP-47 code | auto (en → audio lang → first) | Caption language |
| `--no-asr` | bool | false | Refuse auto-generated captions |
| `--thumbnail` | bool | false | Include thumbnail (.jpg) |
| `--max-height` | int | 1080 | Max video height in pixels; 0 = uncapped |
| `--output-dir` | path | current directory | Output directory (created if missing) |
| `--output` | string | derived from title | Base filename (extension auto-appended) |
| `--force` | bool | false | Overwrite existing output files |
| `--ffmpeg-location` | path | `ffmpeg` on PATH | Override ffmpeg binary path |
| `--quiet` | bool | false | Suppress progress output |
| `--debug` | bool | false | Verbose logs + stack traces on failure |
| `--version` | — | — | Print version and exit |
| `--help` / `-h` | — | — | Print help and exit |

Full contract: [`design/04-apis.md`](./design/04-apis.md) § 3.1.

## Exit codes

| Code | Category | Meaning |
|---|---|---|
| 0 | success | All good |
| 2 | args | Invalid URL or unknown flag |
| 10 | network | DNS/TCP/TLS/HTTP failure after retries |
| 11 | innertube | YouTube response shape changed |
| 20 | unavailable | Video private, deleted, or geo-blocked |
| 21 | live | Video is a livestream or premiere |
| 22 | cipher | Requires JS signature deciphering (use yt-dlp) |
| 30 | format | No format matches `--max-height` filter |
| 40 | captions | No caption track for requested language |
| 50 | output | Output file exists (use `--force`) |
| 60 | ffmpeg | ffmpeg missing, too old, or failed |
| 70 | filesystem | Disk full or write permission denied |
| 130 | sigint | Ctrl-C |
| 143 | sigterm | Process killed |

Full contract: [`design/06-formal/cli-exit-codes.md`](./design/06-formal/cli-exit-codes.md).

## ffmpeg requirement

ffmpeg is needed **only** for:

- **Video downloads** — muxing separate video + audio streams into a single MP4
- **MP3 conversion** — transcoding audio to MP3 via `--audio-format mp3`

These operations work **without ffmpeg** (no install needed):

- `--audio-only` (default M4A format)
- `--transcript`
- `--thumbnail`

Install ffmpeg:

```bash
# macOS
brew install ffmpeg

# Debian/Ubuntu
sudo apt install ffmpeg

# Verify
ffmpeg -version   # must be ≥ 4.0
```

## Troubleshooting

**Invalid URL (exit 2)**
Check the URL matches one of: `youtube.com/watch?v=ID`, `youtu.be/ID`, `youtube.com/shorts/ID`, `m.youtube.com/watch?v=ID`. Run `--help` for usage.

**Network failure (exit 10)**
Check internet connectivity. If persistent, YouTube may have deprecated the client version — see [§ 7.3 of operations](./design/05-operations.md).

**Cipher-protected video (exit 22)**
This video requires JavaScript signature deciphering, which is out of scope. Use [yt-dlp](https://github.com/yt-dlp/yt-dlp) for this URL.

**ffmpeg missing or too old (exit 60)**
Install ffmpeg ≥ 4.0 (see above). If ffmpeg fails mid-process, the error message includes the last 20 lines of ffmpeg's stderr.

**Transcript unavailable (exit 40)**
The video has no captions in the requested language. Try without `--lang`, or drop `--no-asr` to allow auto-generated captions. Run with `--debug` to see available tracks.

**Output file exists (exit 50)**
Pass `--force` to overwrite, or use `--output-dir` / `--output` to change the destination.

**YouTube changed something (exit 11)**
Run with `--debug` and file an issue with the stderr output attached.

For the full decision tree, see [`design/05-operations.md`](./design/05-operations.md) § 5.1.

## Limits

This tool deliberately covers a small subset of what yt-dlp does. It does **not** support:

- Playlists, channels, or search URLs (single video only)
- Signature deciphering (cipher-protected videos fail with a pointer to yt-dlp)
- Live streams (rejected with a clear error)
- Cookies or authenticated sessions (public videos only)
- Age-restricted or members-only content
- Format-selection DSL (`-f "bv*+ba"` style)
- Concurrent/parallel fragment downloads
- Any site other than YouTube
- Windows (macOS and Linux only for MVP)

See the [out-of-scope table](./design/00-requirements.md) in requirements for rationale.

## Development

```bash
# Full build + tests + coverage gate
mvn clean verify

# Unit tests only (offline, fast — target ≤ 30s)
mvn test

# Integration tests (hits the network, opt-in)
mvn verify -P integration

# Coverage report
mvn jacoco:report
open yt-core/target/site/jacoco/index.html
```

### Project structure

```
youtubeDownloader/
├── pom.xml              # parent aggregator
├── yt-core/             # library module (domain logic)
├── yt-cli/              # CLI module (picocli, produces fat jar)
└── design/              # spec-first design docs
```

### Design docs

This project is built **spec-first**. The full design baseline lives in [`design/`](./design/):

| Doc | Contents |
|---|---|
| [`00-requirements.md`](./design/00-requirements.md) | User stories, acceptance criteria, NFRs |
| [`01-overview.md`](./design/01-overview.md) | Project overview and scope |
| [`02-architecture.md`](./design/02-architecture.md) | Component decomposition |
| [`03-data-model.md`](./design/03-data-model.md) | Domain types and state machine |
| [`04-apis.md`](./design/04-apis.md) | CLI flags, library API, external contracts |
| [`05-operations.md`](./design/05-operations.md) | Build, run, troubleshoot |
| [`06-formal/`](./design/06-formal/) | JSON schemas, contract tests, exit codes |
| [`07-tasks.md`](./design/07-tasks.md) | Implementation plan |

## Legality note

Only download content you have the right to download. YouTube's Terms of Service restrict downloading of copyrighted material that you don't own or have a licence for. This project is built for learning and for personal use against content the operator is licensed to access.
