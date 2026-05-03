# youtubeDownloader

A single-site YouTube downloader in Java — video, audio, transcript, thumbnail — from a single video URL.

## Requirements

- **Java 17+**
- **Maven 3.8+**

## Quickstart

```bash
# Clone and build
git clone <repo-url>
cd youtubeDownloader
mvn package

# Verify the build
java -jar yt-cli/target/youtube-downloader-1.0.0.jar --version
java -jar yt-cli/target/youtube-downloader-1.0.0.jar --help
```

> **M0 status:** the tool currently supports `--help` and `--version` only. Download functionality arrives in milestone M1 and later. See [`design/07-tasks.md`](./design/07-tasks.md) for the full roadmap.

## Project structure

- **`yt-core/`** — library module (domain logic, no CLI dependency)
- **`yt-cli/`** — CLI module (picocli entrypoint, depends on `yt-core`); produces the fat jar

## Design docs

This project is built **spec-first**. The full design baseline lives in [`design/`](./design/):

- [`00-requirements.md`](./design/00-requirements.md) — requirements and acceptance criteria
- [`01-overview.md`](./design/01-overview.md) — project overview and scope
- [`02-architecture.md`](./design/02-architecture.md) — component decomposition
- [`03-data-model.md`](./design/03-data-model.md) — domain types and state machine
- [`04-apis.md`](./design/04-apis.md) — external contracts and CLI flag reference
- [`05-operations.md`](./design/05-operations.md) — build, run, and troubleshoot
- [`06-formal/`](./design/06-formal/) — JSON schemas and contract tests
- [`07-tasks.md`](./design/07-tasks.md) — implementation plan

## Legality note

Only download content you have the right to download. YouTube's Terms of Service restrict downloading of copyrighted material that you don't own or have a licence for. This project is built for learning and for personal use against content the operator is licensed to access.
