# youtube-downloader v1.0.0

A single-site YouTube downloader in Java — video, audio, transcript, thumbnail — from a single video URL.

## Headline features

- **Video download** — best quality ≤ 1080p, muxed MP4 via ffmpeg
- **Audio-only M4A** — direct from YouTube, no re-encoding, no ffmpeg needed
- **MP3 transcode** — via ffmpeg at 192 kbps (`--audio-format mp3`)
- **Transcript** — SRT + plain-text TXT, manual captions preferred with ASR fallback
- **Thumbnail** — highest-resolution `.jpg` available

## Install

```bash
# Prerequisites: Java 17+, Maven 3.9+, ffmpeg ≥ 4.0 (optional, for video/MP3 only)

git clone https://github.com/sivarj/youtubeDownloader.git
cd youtubeDownloader
mvn clean package

# The fat jar:
java -jar yt-cli/target/youtube-downloader-1.0.0.jar --version
```

## Quick examples

```bash
JAR="java -jar yt-cli/target/youtube-downloader-1.0.0.jar"

# Full video (best ≤ 1080p MP4)
$JAR "https://www.youtube.com/watch?v=VIDEO_ID"

# Audio-only M4A (no ffmpeg needed)
$JAR --audio-only "https://youtu.be/VIDEO_ID"

# Audio as MP3 (requires ffmpeg)
$JAR --audio-only --audio-format mp3 "URL"

# Transcript only (SRT + TXT, no ffmpeg needed)
$JAR --transcript "URL"

# Thumbnail only
$JAR --thumbnail "URL"
```

## Known limitations

| Limitation | Exit code | Workaround |
|---|---|---|
| Cipher-protected videos | 22 | Use [yt-dlp](https://github.com/yt-dlp/yt-dlp) |
| Live streams / premieres | 21 | Wait for VOD |
| Playlists / channels | 2 | Pass individual video URLs |
| Age-restricted / members-only | 20 | Not supported in v1.0.0 |
| Windows | — | macOS and Linux only |

## Contract tests satisfied

All 78 contract tests from `design/06-formal/contract-tests.md` pass:
- CT-REQ-* (request shape)
- CT-RESP-* (response parsing)
- CT-APP-* (application logic)
- CT-CAP-APP-* (caption processing)
- CT-EXIT-UNIT-* (exit code mapping)

## Full documentation

- [README](./README.md) — flags, exit codes, troubleshooting
- [Design docs](./design/) — spec-first design baseline
- [CHANGELOG](./CHANGELOG.md) — detailed change log
