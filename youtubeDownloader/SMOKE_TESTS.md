# Smoke Tests — Cross-Platform Verification

Manual and automated smoke-test procedures for verifying the youtube-downloader
fat-jar on all supported OS targets per `NFR-SUPPORTED-OS`:

- macOS 13+ (x86_64, aarch64)
- Linux x86_64 (glibc 2.31+)
- Linux aarch64 (glibc 2.31+)

## Prerequisites

- JDK 17+ installed and on `PATH`
- `ffmpeg` 6+ installed and on `PATH`
- Fat-jar built: `mvn clean package -DskipTests` produces
  `yt-cli/target/youtube-downloader-1.0.0.jar`

## Automated Smoke Test

Run `scripts/smoke-test.sh` from the project root. It exercises the fat-jar
with basic commands and asserts expected exit codes.

```bash
./scripts/smoke-test.sh
```

## Manual Checklist

Run each command on every target OS. Record pass/fail.

| # | Command | Expected Exit Code | Expected Behaviour |
|---|---------|-------------------|--------------------|
| 1 | `java -jar yt-cli/target/youtube-downloader-1.0.0.jar --version` | 0 | Prints version string to stdout |
| 2 | `java -jar yt-cli/target/youtube-downloader-1.0.0.jar --help` | 0 | Prints usage/help text to stdout |
| 3 | `java -jar yt-cli/target/youtube-downloader-1.0.0.jar` | 2 | Prints usage hint to stderr, exits with USAGE error |
| 4 | `java -jar yt-cli/target/youtube-downloader-1.0.0.jar "not-a-url"` | 2 | Prints invalid-URL error to stderr (args category) |
| 5 | `java -jar yt-cli/target/youtube-downloader-1.0.0.jar "https://www.youtube.com/watch?v=dQw4w9WgXcQ" --audio-only -o /tmp/yt-smoke` | 0 | Downloads audio file to `/tmp/yt-smoke/` |
| 6 | `ffmpeg -version` | 0 | Confirms ffmpeg is available (mux tests depend on it) |

## OS-Specific Notes

### macOS 13+ (Apple Silicon / Intel)

- GitHub Actions runner: `macos-14` (Apple Silicon M1)
- Homebrew ffmpeg: `brew install ffmpeg`
- Java: Temurin 17 via `setup-java` action or `brew install --cask temurin`

### Linux x86_64

- GitHub Actions runner: `ubuntu-latest` (x86_64)
- ffmpeg: `sudo apt-get install -y ffmpeg`
- Java: Temurin 17 via `setup-java` action

### Linux aarch64

- GitHub Actions runner: `ubuntu-22.04-arm` (ARM64, available as larger runner)
- If ARM runner unavailable, verify manually on an aarch64 host or via QEMU
- ffmpeg: `sudo apt-get install -y ffmpeg`
- Java: Temurin 17 aarch64 via `setup-java` action

## Manual Integration Test (T-5.4)

The integration test `YoutubeDownloaderIT.realVideoDownload_audioOnly` is
`@Disabled` by default (requires network + ffmpeg). To run it manually as a
deeper smoke check:

```bash
# Build the fat-jar first
mvn clean package -DskipTests

# Run the single disabled IT directly
mvn -pl yt-core test -Dtest="com.srk.myutils.yd.core.integration.YoutubeDownloaderIT#realVideoDownload_audioOnly" \
    -DfailIfNoTests=false \
    -Djunit.jupiter.conditions.deactivate=org.junit.jupiter.api.condition.DisabledCondition
```

This downloads a real Creative Commons video (`BaW_jenozKc`) in audio-only mode
and asserts exit code 0. Use it to verify the full download pipeline on a new OS
or after InnerTube client changes.

## CI Integration

The GitHub Actions workflow (`.github/workflows/ci.yml`) runs the smoke-test
script on `ubuntu-latest` and `macos-14` as part of every push/PR. Linux
aarch64 is included when the `ubuntu-22.04-arm` runner is available.
