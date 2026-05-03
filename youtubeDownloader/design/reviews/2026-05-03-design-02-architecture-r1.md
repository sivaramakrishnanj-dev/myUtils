---
review_id: 2026-05-03-design-02-architecture-r1
reviewed_commit: ec90ff8
reviewer: srk
author: srk
phase: design
status: resolved
resolved_commit: ec90ff8
resolved_at: 2026-05-03
---

# Review: Design — 02-architecture.md — round 1

**Scope:** `design/02-architecture.md` at commit `ec90ff8`. Second Phase 2 Design artifact. Covers component view (11 components in 4 subsystems), three flow sequence diagrams, failure-handling matrix, retry model, shutdown, concurrency model, CLI-vs-library module boundary, two new open questions.

---

## Review notes

No blocking, major, minor, or nit comments. Draft approved as-is.

**What was verified:**

- **Component decomposition** — 11 components across Resolve & fetch / Select & download / Post-process & emit / Lifecycle & observability subsystems, each with a single well-bounded responsibility and a one-line invariant statement. The split keeps each component offline-testable per AC-11.1 (pure functions for `UrlParser`, `PlayerResponseExtractor`, `FormatSelector`, `CaptionConverter`) and preserves the library-module clean-surface invariant of AC-9.1..AC-9.5 (no `System.exit`, injectable progress listener, typed exception hierarchy).
- **Flow diagrams** — three Mermaid sequence diagrams cover the three main user operations. Each step is numbered and each decision arrow is annotated with the AC it fulfills. The shared Resolve-and-fetch prefix is visually consistent across the three flows, and the divergence point (after `PlayerResponse` is produced) is clear.
- **Failure matrix** — AC-5.2's 11 exit-code categories (0, 2, 10, 11, 20, 21, 22, 30, 40, 50, 60, 70) are each mapped to a detecting component, a trigger condition, and a user-visible stderr one-liner. `ErrorMapper` is called out as the single source of truth for the category → exit-code mapping, preventing ad-hoc exit codes from sneaking into other components.
- **Retry model** — deliberately asymmetric budgets (InnerTube 3 retries; streams 2 retries with byte-0 restart; no retry for captions / thumbnails / ffmpeg / filesystem / format-selection). The rationale is documented per class — signed-URL expiry for streams, tight-timeout cheapness for captions / thumbnails, terminal-error nature for ffmpeg and filesystem. This saves implementation time and matches the US-5 "fail fast with a useful error" spirit.
- **Shutdown model** — SIGINT / SIGTERM installs a shutdown hook that closes streams, SIGTERMs ffmpeg with a 5s grace, flushes the progress executor, and exits 130/143. `.yt-tmp/` retained on interrupt or failure (for debugging) and cleaned only on success. This is a considered choice, not a default.
- **Concurrency model** — one orchestration thread per `download(...)` call plus one background scheduled-executor for progress, full stop. OOS-5 (no concurrent fragments) is honoured. Library embedders scale by instantiating multiple orchestrators, each isolated.
- **Module boundary** — clean separation of `yt-core` (library, 11 components) from `yt-cli` (picocli, stderr `ProgressListener`, SLF4J binding, `System.exit` mapping). The exception hierarchy is 1-to-1 with AC-5.2 categories (AC-9.4), which is exactly what makes embedder-side catch-by-subtype feasible.
- **Traceability** — 41 AC refs, 21 NFR refs, 2 OOS refs, 2 US refs all cross-checked against `00-requirements.md` during draft preparation. Every decision made in this doc traces back to a requirement.
- **Three pre-flagged reviewer-push-points** (11-component split, caption-selection-logic-in-FormatSelector smell, strict single-threading for video+audio) were acknowledged during draft preparation and accepted as-drafted. Legitimate targets for a `-r2` round if implementation experience surfaces a reason to revisit.
- **ADR placeholders** — four `[ADR-NNNN — pending]` references in the text (ADR-0001 ANDROID client, ADR-0002 OkHttp, ADR-0003 ffmpeg ProcessBuilder, ADR-0004 Jackson). These will be rewritten to live links when the ADR commits land.
- **Two new open questions** — OQ-E (parallel caption+media in a combined flow) and OQ-F (`.yt-tmp/` retention semantics on failure vs interrupt) added with target resolution "Phase 5" for both. Both are tuning questions, not blockers.

**Why this review has no numbered comments:**

Silence in a review process must be explicit. This file exists to record that the Phase 2 `02-architecture.md` draft was reviewed, considered complete, and approved as-drafted — not that review was skipped. Future contributors reading the architecture doc can trace its approval to commit `ec90ff8` via this review file.

**Phase gate:** `02-architecture.md` is **resolved**. Proceeding to write ADRs 0001–0004 as four separate commits, one per ADR, each an atomic reviewable unit. When each ADR is committed, the corresponding `[ADR-NNNN — pending]` placeholder in `02-architecture.md` is rewritten to a live link in the same commit. After the four ADRs land, the next Phase 2 artifact is `03-data-model.md`.
