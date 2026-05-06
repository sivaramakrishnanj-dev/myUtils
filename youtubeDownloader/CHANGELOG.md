# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-05-06

Initial release. Single-site YouTube downloader — video, audio, transcript, thumbnail — from a single video URL.

### Added

#### M0 — Scaffolding (`3cdf7c7`)
- Maven multi-module project (`yt-core` library + `yt-cli` fat jar) (`6f53828`..`3cdf7c7`)
- JaCoCo 80% line-coverage gate on `yt-core` (`e4715df`)
- Surefire + Failsafe integration test profile (`2ad055d`)
- Network I/O blocked in unit tests via SecurityManager extension (`5618f6b`)

#### M1 — Metadata fetch (`d7d8604`)
- URL parsing for 4 YouTube URL shapes: `youtube.com/watch`, `youtu.be`, `youtube.com/shorts`, `m.youtube.com/watch` (`8438e62`)
- InnerTube ANDROID client with OkHttp (`0db0b93`)
- OkHttp retry interceptor: 3 retries, exponential backoff, retryable on 429/5xx/IOException (`fcef051`)
- PlayerResponse Jackson tree-walk parser (`eae8ee5`)
- Full exception hierarchy with 11 typed subclasses (`c67fdec`)
- Playability check mapping (private/deleted/geo-blocked/live/cipher) (`04c2a8f`)
- ErrorMapper translating exceptions to CLI exit codes (`3ad7fef`)
- CLI flag parsing with picocli (`eb48264`)
- Error handling pipeline with `--debug` stack traces (`23d9d47`)
- SLF4J logging at component boundaries (`b47d19b`)
- YoutubeDownloader orchestrator skeleton (`d7d8604`)

#### M2 — Stream download (`7f1d239`)
- FormatSelector: best video (avc1>vp9>av01) + best audio (m4a>webm) (`11d77c7`)
- Cipher-protected video detection with exit code 22 (`c505ba4`)
- StreamDownloader with HTTP Range resume + 64KB chunks (`b917ef9`)
- Stream download retry: 2 retries, 500/1000ms backoff (`69e5fd5`)
- ProgressReporter with 100ms TTY / 1000ms non-TTY rendering (`45fc4a6`)
- ProgressListener functional interface + StderrProgressListener (`51d6c46`)
- `--quiet` flag suppresses progress output (`e6d4058`)
- OutputWriter: filename sanitization, truncation, overwrite guard, free-disk check (`66df778`)
- DownloadContext `.yt-tmp` lifecycle with orphan cleanup (`8a5cd09`)
- `--audio-only` orchestrator path producing M4A (`4e873dc`)
- `--max-height` flag with default 1080 (`7f1d239`)

#### M3 — Mux + MP3 transcode (`1bfc0ab`)
- FfmpegMuxer version probe with minimum version 4.0 enforcement (`0285cfd`)
- ffmpeg mux: separate video + audio → single MP4 (`b6b9359`)
- ffmpeg MP3 transcode: libmp3lame at 192 kbps (`f97932a`)
- ffmpeg stderr ring-buffer (last 20 lines on failure) (`2c635db`)
- ffmpeg shutdown hook: SIGTERM → 5s grace → SIGKILL (`f7abb5f`)
- ffmpeg per-invocation 600s timeout (`d170340`)
- `--ffmpeg-location` flag for custom ffmpeg path (`2d69f08`)
- Flow A end-to-end: video + audio + mux → MP4 (`dc27b91`)
- `--audio-format mp3` + Flow B' transcode path (`703f83c`)
- ffmpeg check skipped for M4A-only and transcript-only paths (`1bfc0ab`)

#### M4 — Captions + thumbnails (`51a10dd`)
- Caption track selection: manual→ASR fallback, language matching (`9b41068`)
- CaptionDownloader with 10s timeout (`70a1dae`)
- XML caption parser (XXE-safe, entity-decoded) (`f9a557a`)
- SRT output with millisecond timestamps (`dda9a0e`)
- Plain-text transcript with duplicate-prefix collapse (`fbdb82c`)
- `--transcript`, `--lang`, `--no-asr` CLI flags (`f714155`)
- ASR fallback wiring and `usedAsrFallback` result field (`86ff3b6`)
- ThumbnailDownloader picking best resolution (`26612c8`)
- `--thumbnail` CLI flag (`d46032a`)
- Caption + thumbnail orchestrator integration (`556c4ce`)
- Flow C: transcript-only without media download (`51a10dd`)

#### M5 — Polish + release (`a1ff421`)
- `--video` flag + AC-2.5 warning on `--video --audio-only` (`a3d2836`)
- Exit-code correctness sweep across 47 throw sites (`e70377d`)
- `--debug` flag polish: stack trace only in debug mode (`07b40b8`)
- Integration test suite with fat-jar ProcessBuilder invocation (`1ce5f17`)
- Fixture provenance documentation (`21a0626`)
- Full v1.0.0 README with all flags, exit codes, troubleshooting (`e05dc3b`)
- GitHub Actions CI workflow (ubuntu + macOS matrix, Java 17) (`a1ff421`)

### Known Limitations
- Cipher-protected videos not supported (exit 22 → use yt-dlp)
- Single video URLs only (no playlists, channels, or search)
- No cookies or authenticated sessions (public videos only)
- macOS and Linux only (Windows not supported in MVP)
- No live stream support (exit 21)

[1.0.0]: https://github.com/sivarj/youtubeDownloader/releases/tag/v1.0.0
