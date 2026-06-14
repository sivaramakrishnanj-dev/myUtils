---
doc: open-questions
updated_by: specDrivenCoordinator
---

# Open questions (code-phase)

Informational: Discussion items raised by reviewers during Phase 5, and DCR (design-change-request) lifecycle entries. Discussion items do not block task resolution — they are surfaced here for the user to consider raising as DCRs on a future task or converting to a designer-conversation session.

## Discussion items from T-1.3

### C6 — AC-1.1 strict-reading confirmation (2026-05-03)
- **spec_refs**: AC-1.1
- **suggested_amendment_kind**: not needed now
- **raised_by**: specDrivenReviewer (round 1)
- **one_line**: strict-reading decisions — m.youtube.com/shorts rejected, youtu.be/<id>/extra rejected. Reviewer confirmed defensible under AC-1.1 "at minimum" phrasing. No amendment needed now; revisit only if user feedback demands a 5th URL shape.

## Discussion items from T-1.4

### C6 — Wire-type conversion for numeric fields (2026-05-03)
- **spec_refs**: innertube-player-response.schema.json, Format.audioSampleRate, Format.contentLength
- **suggested_amendment_kind**: none
- **raised_by**: specDrivenReviewer (round 1)
- **one_line**: InnerTube JSON returns audioSampleRate and contentLength as strings in some fixtures and numbers in others. T-1.4's records declare them as typed int/long. The string→int/long conversion is T-1.7's responsibility (PlayerResponseExtractor). Flagged for traceability only; no amendment needed.

## Discussion items from T-1.7

### C10 — AC-11.1 extractor scope clarification (2026-05-03)
- **spec_refs**: AC-11.1, ADR-0004
- **suggested_amendment_kind**: ac-update
- **raised_by**: specDrivenReviewer (round 1)
- **one_line**: AC-11.1 says extractor is pure-function over in-memory inputs but is silent on whether it validates semantic consistency (e.g., Format.url vs signatureCipher mutual exclusion, itag/mimeType matching). Current implementation does syntactic parsing only; semantic validation is downstream (FormatSelector T-2.1). Suggest an ac-update to make this scope-split explicit in AC-11.1.

## Discussion items from T-1.9

### C9 — Checked vs unchecked exception hierarchy (2026-05-03) — ⚠️ spec-vs-code mismatch
- **spec_refs**: AC-9.1, 04-apis.md § 3.2.2, 02-architecture.md § 6
- **suggested_amendment_kind**: ac-update (recommended) OR code refactor
- **raised_by**: specDrivenReviewer (round 1)
- **one_line**: Spec describes YoutubeDownloaderException hierarchy as "checked" (subclass of Exception). Implementation is unchecked (extends RuntimeException), consistent with all 3 prior stubs (T-1.1 UrlParseException, T-1.5 InnerTubeException/NetworkException, T-1.7 InnerTubeParseException). Unchecked chosen to avoid noisy `throws` declarations through the deep call graph (UrlParser → VideoId → Orchestrator → Cli). Two paths forward: (a) raise a DCR to update AC-9.1 + 04-apis.md + 02-architecture.md to say "unchecked" — recommended since the code is consistent and working; (b) refactor the code to make all 11 subclasses checked — significant churn. User decides.

## Discussion items from T-1.8

### C7 — playabilityStatus.reason field not parsed (2026-05-04)
- **spec_refs**: CT-APP-7, cli-exit-codes.md § 1 exit 20
- **suggested_amendment_kind**: schema-update
- **raised_by**: specDrivenReviewer (round 1)
- **one_line**: InnerTube's playabilityStatus.reason field (human-readable reason text) is not parsed into the PlayerResponse domain; cli-exit-codes.md § 1 exit-20 message template expects it. Suggested schema-update: add Optional<String> playabilityReason to VideoDetails or PlayerResponse. Low-priority polish.

## Discussion items from T-1.10

### C6 — Internal/code-1 path undocumented in spec (2026-05-04)
- **spec_refs**: AC-5.1, AC-5.2
- **suggested_amendment_kind**: ac-update
- **raised_by**: specDrivenReviewer (round 1)
- **one_line**: ErrorMapper defensively maps non-YoutubeDownloaderException throwables to (exit code 1, "Error: internal: ..."). cli-exit-codes.md does not document this path. Suggested ac-update to add exit 1 / "internal" category for contract completeness.

## Discussion items from T-1.14

### C8 — DownloadRequest record not yet introduced (2026-05-04)
- **spec_refs**: AC-9.1
- **suggested_amendment_kind**: ac-update
- **raised_by**: specDrivenReviewer (round 1)
- **one_line**: AC-9.1 lists DownloadRequest as part of the public API surface but T-1.14 uses download(String) overload. DownloadRequest record is M2+ scope (arrives incrementally as flags land). Suggested ac-update to note incremental API introduction.

## G1 gate observations (2026-05-04)

### OBS-1 — Stack trace leak on non-debug failure
- **observed**: G1 verification on 2026-05-04, HEAD d7d8604
- **symptom**: `java -jar ... "not-a-url"` emits the stack trace to stderr even though --debug is NOT set
- **root cause**: Cli.call()'s `LOGGER.error(report.message(), t)` with SLF4J-simple backend prints the stack trace as part of the logger output (SLF4J default behavior when a Throwable is passed)
- **spec violation**: AC-5.1 ("exactly one line of error output to stderr on failure"); AC-5.4 (--debug should be the ONLY trigger for stack trace)
- **severity**: bug, but non-blocking for G1 (exit codes and error messages correct)
- **fix candidates**:
  - a) In Cli.call() catch block, call `LOGGER.error(report.message())` (no Throwable arg) when --debug is false; `LOGGER.error(report.message(), t)` when --debug is true
  - b) Or structure all ERROR logs as message-only; stack trace purely via t.printStackTrace when --debug
- **routing**: flag for a follow-up task in M5 polish (T-5.3 --debug flag handling) or a mini-task in M2

## Discussion items from T-2.4

### C7 — AC-12.4 scope extension to streams (2026-05-04)
- **spec_refs**: AC-12.4, 02-architecture.md § 4.2
- **suggested_amendment_kind**: ac-update
- **raised_by**: specDrivenReviewer (round 1)
- **one_line**: AC-12.4 literally scopes retry semantics to InnerTube requests; T-2.4 applies the same whitelist (429+5xx+IOException) to StreamDownloader by analogy with architecture § 4.2 retry table, but this is not spec-mandated. Suggested ac-update to add AC-12.5 or extend § 4.2 to cover stream retry explicitly.


## Closure log

### OBS-1 — CLOSED at 07b40b8 (T-5.3)
- **original**: stack trace leaking on non-debug failure (AC-5.1 violation)
- **resolution**: Cli.call() no longer passes Throwable to LOGGER.error(); stack trace only when --debug is set
- **verified**: CliDebugBehaviorTest (8 tests)


## OQ-design-1 — T-1.5 design-change requested — 2026-05-06 (DCR-1)

- kind: nfr-update
- raised_by: specDrivenImplementer (via coordinator; triggered by operational integration failure)
- spec_refs_touched: NFR-ANDROID-CLIENT-VERSION, NFR-ANDROID-SDK-VERSION, NFR-ANDROID-USER-AGENT, ADR-0001, 04-apis.md § 1.1.1
- problem_statement: |
    Post-M5 integration testing against a real YouTube URL revealed that
    the pinned ANDROID client version 19.09.37 now produces HTTP 400 from
    YouTube's InnerTube /player endpoint. This is Risk R-1 from
    07-tasks.md § 5 manifesting exactly as the designers anticipated,
    and OQ-A from 01-overview.md is the documented trigger.
- user_decision: approved
- designer_status: amended
- amendment_commit: 9501351, fd1b98b (ripple sweep)
- resumed_task_commit: 19e2e5b
- ripple_unresolved_at_amendment:
    - design/04-apis.md § 1.1.1–1.1.2 (illustrative values stale)
    - design/01-overview.md § External contracts / OQ-A
    - design/06-formal/contract-tests.md CT-REQ-1 description
    - design/06-formal/innertube-player-response.schema.json description
- ripple_resolution: all 4 swept into commit fd1b98b (DCR-1 ripple sweep amendment)


## Discussion items from FIX-portrait-selection (2026-05-06)

### C5 — AC-1.3 wording: "height" vs "quality tier (shorter dimension)"
- **spec_refs**: AC-1.3
- **suggested_amendment_kind**: ac-update
- **raised_by**: specDrivenReviewer
- **one_line**: AC-1.3 literally says "height ≤ max-height" but semantic intent is "quality tier (shorter dimension) ≤ max-height" per YouTube's qualityLabel convention. The portrait-selection bug (fixed in commit 02f6674) originated from the literal reading. Suggested ac-update: reword to "shorter dimension" or "quality tier" explicitly to prevent re-litigation on future orientation-related bugs.
