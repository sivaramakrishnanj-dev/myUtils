---
review_id: 2026-05-03-requirements-phase-1c-r1
reviewed_commit: 41eefc0
reviewer: srk
author: srk
phase: requirements
status: resolved
resolved_commit: 41eefc0
resolved_at: 2026-05-03
---

# Review: Requirements — Phase 1c (non-functional requirements) — round 1

**Scope:** `design/00-requirements.md` Phase 1c section at commit `41eefc0` (32 NFRs in 7 groups, NFR → AC coverage table). Phase 1a (user stories) and Phase 1b (acceptance criteria) were resolved separately at `1481921` and `d300785`.

---

## Review notes

No blocking, major, minor, or nit comments. Draft approved as-is.

**What was verified:**

- **Coverage of Phase 1b symbolic references** — all 9 `NFR-*` identifiers introduced by Phase 1b (`NFR-DEFAULT-MP3-BITRATE`, `NFR-MAX-FILENAME-LENGTH`, `NFR-PROGRESS-INTERVAL`, `NFR-ANDROID-CLIENT-VERSION`, `NFR-ANDROID-USER-AGENT`, `NFR-INNERTUBE-MAX-RETRIES`, `NFR-INNERTUBE-BACKOFF-BASE`, `NFR-MIN-FFMPEG-VERSION`, `NFR-FFMPEG-STDERR-LINES`) are now bound to concrete values. The coverage check table at the end of Phase 1c confirms this row-by-row.
- **Value shape discipline** — every NFR value is numeric, version-string-shaped, or platform-name-shaped. No unqualified adjectives. No "reasonable" / "fast" / "sensible".
- **Grouping** — 7 thematic groups (platform/build, InnerTube identity, network timeouts, output/files, progress/logging, ffmpeg, tests) make individual values easy to find and to push back on in isolation. Calling out the InnerTube identity group as "most likely to need future updates" sets correct expectations for a reverse-engineered API.
- **No behaviour sneaking in as NFRs** — every entry pins a threshold or value that an AC in Phase 1b already referenced behaviourally, plus the expected platform/build/logging/test discipline numbers that every project needs. No new capabilities introduced here.
- **Two-phase budgets documented** — network retries (`NFR-INNERTUBE-MAX-RETRIES = 3` with exponential backoff from 500ms) worst-case 3.5s, ffmpeg invocation bounded at 600s, stream download bounded by idle-read timeout only. The combination produces a predictable worst-case end-to-end time for any single run, which is what an operator needs to know.
- **Acknowledged fragilities surfaced in draft preparation** — three values were pre-flagged during draft review as likely sources of future rounds: the Android client-identity triplet (version, User-Agent, SDK), the unlimited stream download timeout, and the 80% line-coverage hard gate. All three were accepted as-drafted; future rounds (`-r2`, etc.) on this file are the documented path if operational experience forces a change.
- **Platform scope locked** — `NFR-SUPPORTED-OS = macOS 13+ and Linux (x86_64, aarch64) with glibc 2.31+`. Windows deliberately excluded for MVP; can be added later without architectural change.

**Why this review has no numbered comments:**

Silence in a review process must be explicit. This file exists to record that the Phase 1c draft was reviewed, considered complete, and approved as-drafted — not that review was skipped. Future contributors reading the requirements doc can trace its approval to commit `41eefc0` via this review file.

**Phase gate:** Phase 1c is **resolved**. With Phase 1a (`1481921`), Phase 1b (`d300785`), and Phase 1c (`41eefc0`) all resolved, **Phase 1 — Requirements is closed.** The combined requirements baseline is now the ground truth for Phase 2 design work. Further changes to any sub-phase require a new review round on this file and an explicit phase-transition entry in the front-matter.

Proceeding to Phase 2 — Design: `01-overview.md`, `02-architecture.md`, `03-data-model.md`, `04-apis.md`, `05-operations.md`, and the first ADRs (anticipated: ANDROID InnerTube client choice, HTTP library choice, ffmpeg-vs-pure-Java mux, Jackson vs Gson for JSON, caption preference resolution strategy).
