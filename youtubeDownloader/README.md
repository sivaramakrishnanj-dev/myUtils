# youtubeDownloader

A single-site YouTube downloader in Java — video, audio, transcript, thumbnail — from a single video URL.

This project is being built **spec-first**. No code is written until the relevant design phases are approved.

> **Start here:** [`design/README.md`](./design/README.md)

## Quick facts

- **Language / build:** Java 17, Maven 3.9+
- **GroupId / artifactId:** `com.srk.myutils.yd` / `youtube-downloader` (locked in `pom.xml` once Phase 5 begins)
- **Scope:** one YouTube video URL at a time — see [`design/01-overview.md`](./design/01-overview.md) once it exists
- **Out of scope (MVP):** playlists, signature deciphering, live streams, format-selection DSL, concurrent-fragment downloads
- **External binary dependency:** `ffmpeg` (for video+audio mux and audio format conversion)

## Legality note

Only download content you have the right to download. YouTube's Terms of Service restrict downloading of copyrighted material that you don't own or have a licence for. This project is built for learning and for personal use against content the operator is licensed to access.

## Status

**All design phases resolved.** Phase 1 — Requirements, Phase 2 — Design, Phase 3 — Formal contracts, and Phase 4 — Tasks are all on `origin/main` and reviewed. Phase 5 — Code begins at task `T-0.1` per `design/07-tasks.md`.

Last pushed SHAs per phase:
- Phase 1: 1a `1481921`; 1b `d300785`; 1c `41eefc0`
- Phase 2: `01` `aceca50`; `02` `ec90ff8`; ADRs 0001–0004 `f44e681`/`bae8a87`/`278f51f`/`1d10c7c`; `03` `5a418a1`; `04` `4088d0f`; `05` `ebcc8b9`
- Phase 3: `12fb5cc`
- Phase 4: `ec9e74b`
