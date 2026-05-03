---
doc: operations
last_reviewed: 2026-05-03
phase: 2-design
status: draft
review:
approved_in:
---

# 05 — Operations

This document is the **operator's guide** for youtubeDownloader. It covers building, running, packaging, updating, and troubleshooting. Audience: the CLI User (P1), the Maintainer / Future Self (P4), and anyone who has to diagnose a failure.

> **Scope:** everything here describes what a user or maintainer **does** with the tool, not what the tool **is**. For what the tool is, read `01-overview.md`. For what it does internally, read `02-architecture.md`.

---

## 1. Build

### 1.1 Prerequisites

| Dependency | Version | Source | Required for |
|---|---|---|---|
| **JDK** | 17+ | `NFR-JAVA-VERSION = 17` | Compile, test, run |
| **Maven** | 3.9+ | `NFR-BUILD-TOOL` | Build |
| **ffmpeg** | ≥ 4.0 | `NFR-MIN-FFMPEG-VERSION` | Run (for video mux and MP3 transcode only; transcript-only and audio-only-m4a paths tolerate its absence per AC-13.5) |
| **git** | any recent | — | Clone the repo |

On **macOS (Homebrew):**
```bash
brew install openjdk@17 maven ffmpeg
```

On **Debian/Ubuntu:**
```bash
sudo apt install openjdk-17-jdk maven ffmpeg
```

On **RHEL/Fedora:**
```bash
sudo dnf install java-17-openjdk-devel maven ffmpeg
```

### 1.2 Build commands

```bash
# From the project root (youtubeDownloader/):

# Full build: compile, test, package the fat jar
mvn clean package

# Skip tests (for quick rebuilds)
mvn clean package -DskipTests

# Run only unit tests (fast, offline — per NFR-UNIT-TEST-RUNTIME-BUDGET = 30s)
mvn test

# Run the network-touching integration suite (opt-in; see § 4)
mvn verify -P integration

# Regenerate coverage report
mvn jacoco:report
open target/site/jacoco/index.html  # macOS; Linux users substitute xdg-open
```

The fat jar lands at `target/youtube-downloader-1.0.0.jar`. Size target: ≤ 10 MB (see § 6).

### 1.3 Module layout

```
youtubeDownloader/
├── pom.xml                               # parent — aggregator only
├── yt-core/                              # library module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/srk/myutils/yd/core/
│       │   ├── YoutubeDownloader.java
│       │   ├── DownloadRequest.java
│       │   ├── DownloadResult.java
│       │   ├── ... (11 components per 02-architecture.md § 1)
│       │   └── internal/
│       │       └── DownloadContext.java
│       └── test/java/com/srk/myutils/yd/core/
│           ├── ... unit tests (per AC-11)
│           └── resources/fixtures/       # InnerTube JSON fixtures, caption XML fixtures
├── yt-cli/                               # CLI module (depends on yt-core)
│   ├── pom.xml
│   └── src/main/java/com/srk/myutils/yd/cli/
│       ├── Cli.java                       # picocli entrypoint
│       ├── StderrProgressListener.java
│       └── ExitCodeMapper.java
└── design/                               # this folder
```

The parent `pom.xml` is a Maven aggregator only. Fat-jar assembly happens in `yt-cli` via the shade plugin, pulling `yt-core` plus all transitive runtime dependencies into one jar.

### 1.4 Build verification checklist

Before publishing a release (or merging to `main`), verify:

- [ ] `mvn clean verify` passes (includes unit tests + JaCoCo coverage gate at `NFR-UNIT-TEST-COVERAGE-MINIMUM = 80%`)
- [ ] `mvn test` completes within `NFR-UNIT-TEST-RUNTIME-BUDGET = 30 s`
- [ ] Fat jar size ≤ 10 MB (`ls -lh yt-cli/target/*-shaded.jar`)
- [ ] `java -jar yt-cli/target/youtube-downloader-*-shaded.jar --version` prints a version
- [ ] `java -jar ... --help` prints help and exits `0`
- [ ] `mvn verify -P integration` passes against at least one real YouTube URL (if network is available)

---

## 2. Run

### 2.1 First run

```bash
# Download a full video (best-quality ≤ 1080p, muxed MP4)
java -jar youtube-downloader-1.0.0.jar "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

# Audio-only, default m4a
java -jar youtube-downloader-1.0.0.jar --audio-only "https://youtu.be/dQw4w9WgXcQ"

# Audio-only as MP3 (requires ffmpeg for transcode)
java -jar youtube-downloader-1.0.0.jar --audio-only --audio-format mp3 "URL"

# Transcript only (no media; ffmpeg not needed)
java -jar youtube-downloader-1.0.0.jar --transcript "URL"

# Everything at once (video + MP3 audio + transcript + thumbnail)
java -jar youtube-downloader-1.0.0.jar --video --audio-only --audio-format mp3 --transcript --thumbnail "URL"

# Into a specific directory with a custom name
java -jar youtube-downloader-1.0.0.jar --output-dir ~/videos --output "my-download" "URL"

# Under 720p cap
java -jar youtube-downloader-1.0.0.jar --max-height 720 "URL"

# Debug mode (full stack trace on failure, verbose logs, verbose ffmpeg)
java -jar youtube-downloader-1.0.0.jar --debug "URL"
```

Full flag reference: [`04-apis.md`](./04-apis.md) § 3.1.2.

### 2.2 Interpreting output

**Normal output (stderr — stdout is reserved):**
```
[14:52:03.128 INFO  com.srk.myutils.yd.core.UrlParser - parsed URL → videoId=dQw4w9WgXcQ]
[14:52:03.312 INFO  ... - InnerTube /player 200 OK (response 48.2 KB)]
[14:52:03.519 INFO  ... - selected video: itag=137 mp4 1080p h264, audio: itag=140 m4a 128k aac]
[video]    45%   112.3 MB / 249.1 MB   9.8 MB/s   ETA 00:14
```

Progress line refreshes in place if stderr is a TTY (per AC-4.2), or appends new lines every second when redirected (AC-4.3).

**Failure output (stderr, single line):**
```
Error: cipher: this video requires JavaScript signature deciphering, which is out of scope for this tool. Use yt-dlp for this URL.
```

Exit code per [`04-apis.md`](./04-apis.md) § 3.1.4.

### 2.3 Integrating into scripts

The CLI is script-friendly. stdout is reserved (unused in MVP); stderr carries progress and errors. Exit codes are stable per AC-5.2.

Example shell wrapper:

```bash
#!/usr/bin/env bash
set -euo pipefail

download() {
    local url="$1"
    local dir="${2:-.}"
    if java -jar /opt/yt/youtube-downloader-1.0.0.jar --output-dir "$dir" "$url"; then
        echo "SUCCESS: $url"
    else
        code=$?
        case $code in
            10) echo "NETWORK FAILURE: $url — will retry in 60s"; sleep 60; download "$@" ;;
            22) echo "CIPHER (unsupported) — falling back to yt-dlp"; yt-dlp -o "$dir/%(title)s.%(ext)s" "$url" ;;
             *) echo "FAILED ($code): $url" >&2; return $code ;;
        esac
    fi
}
```

---

## 3. Metrics and observability

MVP has no metrics emitter (no Prometheus, no CloudWatch, no StatsD). The observability contract is **structured logs via SLF4J** per AC-10.1..AC-10.5.

### 3.1 Log levels

| Level | When emitted | Example |
|---|---|---|
| `ERROR` | Each run failure (one line per AC-10.4) | `Error: cipher: this video requires ...` |
| `WARN` | Non-fatal notable events (AC-10.3) | `ASR caption fallback used (no manual track matched 'fr')`, `thumbnail fetch failed, continuing without thumbnail` |
| `INFO` | Each external-boundary call (AC-10.2) | `InnerTube /player 200 OK (response 48.2 KB)`, `stream download complete: 249.1 MB in 00:25` |
| `DEBUG` | Full HTTP request/response headers, InnerTube request body, caption track list, ffmpeg stderr streaming (AC-10.5). Enabled by `--debug`. | Verbose |
| `TRACE` | Unused in MVP | — |

### 3.2 What to log where

Logger names follow `com.srk.myutils.yd.core.<ComponentName>` (e.g., `com.srk.myutils.yd.core.StreamDownloader`). Operators can tune per-component levels by configuring SLF4J's backend (`simplelogger.properties` for `slf4j-simple`, `logback.xml` for logback, etc.).

### 3.3 What's deliberately NOT in MVP

- **No metrics to external systems.** No Prometheus exporter, no CloudWatch putMetricData, no StatsD UDP. If you want metrics, tail the logs and parse them.
- **No distributed tracing.** No OpenTelemetry, no X-Ray. A run is a single process; traces are linear.
- **No persistent run history.** Each run is independent; there's no database, no `~/.youtubeDownloader/runs.db`.
- **No remote crash reporting.** Crashes go to stderr. Users diagnose locally or file an issue manually.

All of these are reasonable additions later (`06-future-work` candidates); they were judged over-budget for MVP.

---

## 4. Testing

### 4.1 Unit tests (default, offline)

`mvn test` runs the unit suite. By contract (AC-11.3, AC-11.4), these **do not touch the network**. They operate on checked-in fixtures under `yt-core/src/test/resources/fixtures/`:

```
fixtures/
├── innertube/
│   ├── happy-1080p.json          # full public video with all formats
│   ├── cipher-required.json       # all formats have signatureCipher → AC-5.3 path
│   ├── live.json                  # isLive = true → exit 21
│   ├── unplayable-private.json    # playabilityStatus = UNPLAYABLE → exit 20
│   └── no-captions.json           # captions array empty → AC-6.4 path
├── captions/
│   ├── en-manual.xml
│   ├── en-asr.xml
│   └── empty.xml
└── urls/
    ├── valid-watch.txt            # one line per accepted URL form
    └── invalid.txt                # one line per rejection
```

Each fixture carries an `x-captured-on: YYYY-MM-DD` note in a companion `.meta.json` (`06-formal/README.md` convention).

### 4.2 Integration tests (opt-in, online)

`mvn verify -P integration` runs the integration suite, which **does** touch the network. These are marked `@IntegrationTest` and excluded from the default profile.

Integration tests run against a short list of public, stable URLs:
- Rick Astley — "Never Gonna Give You Up" (`dQw4w9WgXcQ`) — canonical happy path, cheap
- A known-short Creative Commons video
- A Shorts URL
- A `youtu.be` short-link URL

The integration suite is **not** gated on coverage. Its purpose is smoke testing against reality, not coverage padding.

### 4.3 Coverage

JaCoCo is configured in `yt-core`'s `pom.xml` with a minimum of `NFR-UNIT-TEST-COVERAGE-MINIMUM = 80%` line coverage. The gate runs during `mvn verify`. The CLI module (`yt-cli`) is excluded from the gate — it's mostly picocli annotations and `System.exit` glue.

Check coverage locally:
```bash
mvn jacoco:report
open yt-core/target/site/jacoco/index.html
```

### 4.4 Contract tests (Phase 3)

Phase 3's `06-formal/contract-tests.md` will index contract tests that validate fixtures against their JSON Schemas. Those are part of the unit-test run and must pass on every `mvn test`.

---

## 5. Common failures and remediation

Matrix of every AC-5.2 exit code to what caused it and what the user should do.

| Exit | Category | Typical cause | Remediation |
|---|---|---|---|
| `0` | success | — | — |
| `2` | args / URL | URL doesn't match any accepted shape; unknown flag | Check the URL; run with `--help` |
| `10` | network | DNS / TCP / TLS failure, HTTP 4xx/5xx after retries | Check connectivity; retry later; if persistent, may indicate ADR 0001 fragility (client version deprecated — see OQ-A) |
| `11` | InnerTube parse | Response shape changed — YouTube updated something | Capture a `--debug` run and a fresh fixture; update `06-formal/innertube-player-response.schema.json` and re-test |
| `20` | video unavailable | Video private, deleted, geo-blocked, or age-restricted | Check the video in a browser; if age-restricted/private/members-only, this tool can't help (OOS-6, OOS-7) |
| `21` | live / premiere | Video is a livestream or scheduled premiere | Wait for it to end and become a VOD; live streams are OOS-3 |
| `22` | cipher-protected | All candidate formats require JS signature deciphering | Run `yt-dlp` instead for this URL. This is explicitly out of scope (OOS-2). |
| `30` | no matching format | `--max-height` too restrictive; strange-format video | Relax `--max-height`; try without it |
| `40` | caption unavailable | No captions in `--lang`; only ASR exists and `--no-asr` was set | Try without `--lang`; drop `--no-asr` |
| `50` | output file exists | File already at target path | Use `--force`, or delete the existing file, or change `--output-dir`/`--output` |
| `60` | ffmpeg | Missing, too old, or subprocess failed | `brew install ffmpeg` (or dist equivalent); upgrade if < 4.0; for subprocess failure, read the 20 stderr lines in the error message |
| `70` | filesystem | Disk full, permissions, path not writable | Free up disk; check permissions on `--output-dir` |
| `130` | SIGINT | User pressed Ctrl-C | Expected. `.yt-tmp/` retained for inspection |
| `143` | SIGTERM | Process killed by parent | Expected. `.yt-tmp/` retained |

### 5.1 "My video won't download" decision tree

```
Start
 ↓
Does --version work? ──── no ──→ Install / fix Java. NFR-JAVA-VERSION=17.
 ↓ yes
Does --help work? ──── no ──→ Fat jar broken; rebuild (§ 1.2).
 ↓ yes
Try the URL. What's the exit code?
 ↓
 ├─ 10  → Network. `ping youtube.com`; check proxy/VPN; retry.
 ├─ 11  → YouTube changed the API. Capture --debug output, file issue.
 ├─ 20  → Video unavailable. Open in browser — is it private / deleted?
 ├─ 21  → Livestream. Wait for VOD.
 ├─ 22  → Cipher. Use yt-dlp for this URL. Expected per OOS-2.
 ├─ 30  → No format. Drop --max-height.
 ├─ 40  → Caption not available. Drop --lang or --no-asr.
 ├─ 50  → Output exists. Use --force.
 ├─ 60  → ffmpeg. `ffmpeg -version`; upgrade if < 4.0.
 ├─ 70  → Disk. `df -h`; check permissions.
 ├─ 130 → You pressed Ctrl-C. Run again.
 └─ else → Unknown. Run with --debug; file an issue with stderr attached.
```

---

## 6. Packaging and distribution

### 6.1 Fat-jar assembly

`yt-cli/pom.xml` uses `maven-shade-plugin` to produce a single self-contained fat jar. Main class: `com.srk.myutils.yd.cli.Cli`.

```bash
mvn -pl yt-cli package -am
# Output: yt-cli/target/youtube-downloader-1.0.0.jar (shaded)
```

Target size: **≤ 10 MB**. Back-of-envelope from ADR decisions:
- `yt-core` compiled classes: ~200 KB
- `yt-cli` compiled classes: ~30 KB
- OkHttp + Okio (ADR 0002): ~800 KB
- kotlin-stdlib (transitive): ~500 KB
- Jackson databind + core + annotations (ADR 0004): ~2 MB
- picocli: ~400 KB
- SLF4J API + slf4j-simple: ~80 KB

**Estimated total: ~4 MB.** Well under the 10 MB target.

### 6.2 Distribution

MVP distribution is **source-only**. Users clone the repo and run `mvn clean package`. No published Maven artifact, no Homebrew tap, no GitHub Release binaries. All three are Future Work.

### 6.3 Versioning

Semantic versioning (`MAJOR.MINOR.PATCH`). MVP is `1.0.0`. Future changes:
- `PATCH` — bug fixes; no contract changes (flag names, exit codes, library API unchanged)
- `MINOR` — new capabilities; existing contracts unchanged
- `MAJOR` — any contract-breaking change (flag rename, exit-code reassignment, library method signature change, exception class removed)

The ANDROID client version triplet (`NFR-ANDROID-*`) is not semver-governed — it updates whenever YouTube forces it to. A fresh triplet in a `PATCH` release is allowed.

---

## 7. Updating and upgrading

### 7.1 Upgrading the tool

```bash
cd youtubeDownloader/
git pull
mvn clean package
```

The resulting fat jar replaces the old one in-place at `yt-cli/target/youtube-downloader-<version>.jar`. Users who have symlinked the jar into `/usr/local/bin` (or equivalent) benefit automatically.

### 7.2 Upgrading ffmpeg

```bash
brew upgrade ffmpeg          # macOS
sudo apt upgrade ffmpeg      # Debian/Ubuntu
```

`NFR-MIN-FFMPEG-VERSION = 4.0` is the floor; no known upper bound. Newer ffmpeg versions are tested before merging with the tool's release.

### 7.3 When YouTube breaks the tool

Expected failure signal: exit code `10` or `11` starts appearing persistently across different URLs. Diagnosis:

1. Run a known-good URL (e.g., `dQw4w9WgXcQ`) with `--debug`.
2. If HTTP 403 / 429 from InnerTube → YouTube has deprecated `NFR-ANDROID-CLIENT-VERSION`. OQ-A early warning. Fix: new NFR review round updating the triplet.
3. If HTTP 200 but `InnerTubeParseException` → response shape changed. Fix: capture fresh fixture, update `06-formal/innertube-player-response.schema.json`, update `PlayerResponseExtractor` if necessary.
4. If neither → unrelated; investigate normally.

### 7.4 Known limits

- **Cipher-protected videos fail** with exit `22`. Not fixable in MVP (OOS-2). Users route those URLs to yt-dlp.
- **No playlist / channel / search URLs.** Single-video URLs only (OOS-1).
- **No cookies or authentication.** Public videos only (OOS-6, OOS-7).
- **Live streams rejected.** Wait for VOD (OOS-3).
- **Windows not supported.** MVP targets macOS + Linux only (`NFR-SUPPORTED-OS`). Windows is plausible future work; filename sanitization (AC-3.3) already accommodates its illegal-char set.

---

## 8. Closing notes

This completes Phase 2 Design. Next phase:

- **Phase 3 — Formal contracts** (`06-formal/`): JSON Schemas for InnerTube request/response, timedtext caption XML (or equivalent structural spec), output metadata JSON, the CLI exit-code contract. Each schema has positive + negative examples in `contract-tests.md`.
- **Phase 4 — Tasks** (`07-tasks.md`): the ordered implementation breakdown mapped to ACs, estimated effort, milestones.
- **Phase 5 — Code** (`src/`): the implementation, one milestone at a time, with tests that reference the fixtures and schemas from Phase 3.
