---
review_id: 2026-05-03-design-03-data-model-r1
reviewed_commit: 5a418a1
reviewer: srk
author: srk
phase: design
status: resolved
resolved_commit: 5a418a1
resolved_at: 2026-05-03
---

# Review: Design — 03-data-model.md — round 1

**Scope:** `design/03-data-model.md` at commit `5a418a1`. Third Phase 2 Design artifact. Covers domain type catalogue (18 types in 5 groups, ER diagram, per-type details with invariants), download lifecycle state machine with invariants, wire-format translation boundary, and scope split with `04-apis.md` / `06-formal/`.

---

## Review notes

No blocking, major, minor, or nit comments. Draft approved as-is.

**What was verified:**

- **Immutability discipline** — every type is a Java `record` or `enum`, matching AC-9.1 (public library API of records and interfaces only) and AC-11.1 (pure functions over in-memory inputs). `DownloadContext` is the one place that could have been mutable for pragmatism but is kept immutable with copy-with semantics, enforcing the "a phase's input is always exactly what it declares" property.
- **`VideoId` and `LanguageCode` as validated value types** — both have `Pattern`-validated constructors that throw at boundary entry, preventing every downstream component from having to re-validate. `LanguageCode.matches()` is the single place that encodes AC-8.2's primary-subtag match rule; no other component needs its own string-comparison logic.
- **Public vs internal package split** — all 17 user-facing types are in `com.srk.myutils.yd.core`; `DownloadContext` is the sole member of `com.srk.myutils.yd.core.internal`. The boundary is enforceable by the `yt-core` Maven module's visibility contract. AC-9.5 (CLI consumes only library's public API) is structurally supported.
- **`PlayerResponse` and subtypes** — only the fields the tool consumes are modelled, with `@JsonIgnoreProperties(ignoreUnknown = true)` per ADR 0004. Invariants on `Format` (exactly-one-of-video-or-audio; `url.isEmpty() == hasCipher()`) capture the AC-5.3 cipher-fail-fast contract at the type level.
- **`DownloadRequest` invariants** — builder-enforced (`audioOnly` implies `!video`), sensible defaults (1080 `maxHeight`, `M4A` audio), a single "request with no outputs" guard rejected with `IllegalArgumentException` (programmer error, not our exception hierarchy). Matches AC-2.* and AC-3.* behaviour requirements without introducing new flags beyond what Phase 1 pinned.
- **`DownloadResult` with `Optional<Path>`** — explicit about which outputs were produced. `usedAsrFallback: boolean` surfaces the AC-7.3 "ASR was substituted" fact to library embedders who may want to log or warn on it. All invariants listed (e.g., `srtPath` and `txtPath` both present or both absent) are testable.
- **`ProgressEvent.Phase` enum aligned with state machine** — Section 3.3's alignment table pins the mapping so `ProgressReporter` (architecture § 1.2.4) never has to decide phase names at runtime. Internal states (`PARSING_PLAYER_RESPONSE`, `SELECTING_FORMATS`, etc.) are merged into `RESOLVING` for user-visible progress — correct choice for MVP; can be split later without changing the state machine.
- **State machine completeness** — 14 working states + `TERMINATED` + `DONE`, with explicit transitions for every failure path mapped to AC-5.2 exit codes (10/11/20/21/22/30/40/50/60/70). The "TERMINATED never returns to a working state" invariant is the spec-level guarantee that no zombie state can occur during a run. SIGINT/SIGTERM is documented as a separate transition category with exit codes `130`/`143` outside the AC-5.2 category set (they're signals, not failures).
- **Object-graph-at-peak sketch** — Section 4 gives a reviewer a clear mental picture of what's in memory when `MUXING` is running: ~1 MB of domain objects + two `.part` files on disk + ffmpeg out-of-process + OkHttp pool. Supports the `NFR-MAX-MEMORY = JVM default` choice.
- **Wire-format translation boundary table** — Section 5 enumerates every point where wire becomes domain and identifies the translator component. This is the hand-off surface to `04-apis.md` (wire prose) and `06-formal/` (wire schemas).
- **Scope split with next artifacts is explicit** — Section 6 lists what `04-apis.md` and `06-formal/` pin (wire JSON/XML shapes, exact CLI flags, exact ffmpeg command-line, exit-code contract, contract tests). No double-specification.
- **Cross-reference integrity** — 22 AC refs, 4 NFR refs, 2 ADR refs all cross-checked against `00-requirements.md` and `adr/` during draft preparation.

**Why this review has no numbered comments:**

Silence in a review process must be explicit. Proceeding to `04-apis.md`.
