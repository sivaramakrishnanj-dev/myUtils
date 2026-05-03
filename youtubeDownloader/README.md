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

Phase 2 — Design in progress. `01-overview.md` is in draft. Phase 1 — Requirements **resolved** (1a `1481921`; 1b `d300785`; 1c `41eefc0`). Code has not started.
