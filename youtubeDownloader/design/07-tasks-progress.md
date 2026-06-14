---
doc: tasks-progress
updated_by: specDrivenCoordinator
---

# 07 — Tasks Progress

Per-task resolution log maintained by `specDrivenCoordinator`. An entry here means the task was implemented, tested, reviewed (resolved), committed, and pushed.

## T-0.1 — Create parent pom.xml as Maven aggregator
- commit: 6f53828
- review: design/reviews/code/T-0.1-r1.md
- resolved: 2026-05-03
- notes: clean minimal aggregator pom; NFR-BUILD-TOOL + NFR-JAVA-VERSION satisfied; no tests applicable (M0 scaffolding)


## T-0.2 — Create yt-core/pom.xml with Java 17 + core deps
- commit: 0037a56
- review: design/reviews/code/T-0.2-r1.md
- resolved: 2026-05-03
- notes: yt-core child pom + parent dependencyManagement (okhttp/jackson/slf4j + junit/assertj/jqwik); ADR-0002 + ADR-0004 satisfied; 1 nit (cosmetic)

## T-0.3 — Create yt-cli/pom.xml with picocli + slf4j-simple
- commit: a7fca12
- review: design/reviews/code/T-0.3-r1.md
- resolved: 2026-05-03
- notes: yt-cli child pom depending on yt-core + picocli 4.7.7 + slf4j-simple; 1 nit (cosmetic); no double SLF4J binding

## T-0.4 — Configure maven-shade-plugin for fat jar
- commit: 31be5e4
- review: design/reviews/code/T-0.4-r1.md
- resolved: 2026-05-03
- notes: shade 3.6.0 in yt-cli producing youtube-downloader-1.0.0.jar (5.65 MB); Main-Class set; transformers + signature filters configured; 1 nit (size FYI per R-6)

## T-0.5 — Empty Cli class with --version and --help
- commit: a7fb847
- review: design/reviews/code/T-0.5-r1.md
- resolved: 2026-05-03
- notes: first Java code; picocli @Command Cli with mixin help/version; 8 tests green; M0 gate commands verified; 1 minor + 2 nits accepted (T-1.11 supersedes)

## T-0.6 — Configure JaCoCo 80% line-coverage gate in yt-core
- commit: e4715df
- review: design/reviews/code/T-0.6-r1.md
- resolved: 2026-05-03
- notes: jacoco 0.8.14; BUNDLE/LINE/COVEREDRATIO/0.80 check in verify phase; yt-core only; gate activates when T-1.1 lands first class

## T-0.7 — Configure Surefire + Failsafe integration profile
- commit: 2ad055d
- review: design/reviews/code/T-0.7-r1.md
- resolved: 2026-05-03
- notes: Surefire/Failsafe 3.5.5 centralised in parent; default excludes @Tag("integration"), profile activates Failsafe; AC-11.3 satisfied; 0 findings

## T-0.8 — Block network I/O in unit tests (AC-11.3, AC-11.4)
- commit: 5618f6b
- review: design/reviews/code/T-0.8-r2.md (prior: r1.md)
- resolved: 2026-05-03
- notes: NoNetworkExtension (SecurityManager + JUnit 5 auto-extension) instead of Maven Enforcer — AC requires runtime check; shared via test-jar; integration profile escape-hatch on Failsafe; r1 closed with 1 minor (gitignore fix); r2 clean

## T-0.9 — README quickstart for mvn package + --help/--version
- commit: 3cdf7c7
- review: design/reviews/code/T-0.9-r1.md
- resolved: 2026-05-03
- notes: README updated with Requirements, Quickstart, M0 status note, design-doc index; 0 findings; 4 praise items

---

## Milestone M0 — CLOSED at 3cdf7c7

## T-1.1 — VideoId record + minimal UrlParseException stub
- commit: 2405235
- review: design/reviews/code/T-1.1-r1.md
- resolved: 2026-05-03
- notes: first real production code in yt-core; AC-1.1 + INV-5 satisfied; 29 tests; 100% line coverage; CT-REQ-N2, CT-REQ-N3 satisfied; JaCoCo gate fired + passed

## T-1.2 — LanguageCode record with matches() primary-subtag rule
- commit: 249b36a
- review: design/reviews/code/T-1.2-r1.md
- resolved: 2026-05-03
- notes: AC-8.2 matches() rule; of() normalizes lowercase primary; 46 tests; 100% class coverage; NPE on matches(null) documented

## T-1.3 — UrlParser with 4 AC-1.1 URL shapes
- commit: 8438e62
- review: design/reviews/code/T-1.3-r2.md (prior: r1.md)
- resolved: 2026-05-03
- notes: 43 tests, 93%/86% line/branch coverage on UrlParser; round 1 open with 2 majors (C1 mobile path, C3 test gap) + 1 minor (C2 error shape); round 2 resolved clean

## Discussion items from T-1.3
- C6 (AC-1.1): strict-reading decisions — m.youtube.com/shorts rejected, youtu.be/<id>/extra rejected. Reviewer confirmed defensible under AC-1.1 "at minimum" phrasing. No amendment needed now; revisit only if user feedback demands a 5th URL shape.

## T-1.4 — Domain records for InnerTube response shape
- commit: 269bbfb
- review: design/reviews/code/T-1.4-r1.md
- resolved: 2026-05-03
- notes: 6 types (5 records + enum); @JsonIgnoreProperties per ADR-0004; 45 tests; 100% coverage on new types; 1 Discussion (wire-type conversion → T-1.7)

## Discussion items from T-1.4
- C6 (schema / Format.audioSampleRate / Format.contentLength): wire-type string→int/long conversion is T-1.7 territory. Flagged for traceability only; no amendment needed.

## T-1.5 — InnerTubeClient with OkHttp + ANDROID context
- commit: 0db0b93
- review: design/reviews/code/T-1.5-r1.md
- resolved: 2026-05-03
- notes: size L; constructor-injected OkHttp for testability; body matches schema byte-for-byte; 39 tests; 95.7% coverage; CT-REQ-1, CT-REQ-N6, CT-REQ-N7, CT-APP-1 (partial), INV-9

## T-1.6 — OkHttp retry interceptor (AC-12.4)
- commit: fcef051
- review: design/reviews/code/T-1.6-r1.md
- resolved: 2026-05-03
- notes: 3 retries with 500/1000/2000ms backoff; retryable set {429, 5xx} + IOException; Sleeper injection for testability; 30 tests; 96.7% coverage

## T-1.7 — PlayerResponseExtractor Jackson tree-walk parser
- commit: eae8ee5
- review: design/reviews/code/T-1.7-r1.md
- resolved: 2026-05-03
- notes: tree-walk Option B; 51 tests across 6 fixtures; CT-APP-1/2/6/7 + CT-RESP-N1..N3 satisfied (N4-N8 are schema-validation territory); 97% coverage; 1 Discussion (C10 AC-11.1 clarification)

## Discussion items from T-1.7
- C10 (AC-11.1, ADR-0004): AC-11.1 is silent on whether the extractor validates semantic consistency (e.g., url/cipher invariant, matching itag/mimeType). Suggested ac-update clarifying syntactic parsing is extractor's scope; semantic validation is downstream (FormatSelector). Not a blocker.

## T-1.9 — Full exception hierarchy (AC-9.4)
- commit: c67fdec
- review: design/reviews/code/T-1.9-r1.md
- resolved: 2026-05-03
- notes: sealed hierarchy; 11 final subclasses; CT-EXIT-UNIT-1..11 satisfied; 73 tests; spec names used (Hard Rule 6); 1 Discussion (C9 checked vs unchecked)

## Discussion items from T-1.9
- C9 (AC-9.1, 04-apis.md § 3.2.2, 02-architecture.md § 6): spec describes exception hierarchy as "checked" but implementation is unchecked (RuntimeException). Unchecked chosen to avoid noisy throws declarations in deep call graph and for consistency with all 3 prior stubs. User should decide: DCR to update the spec to "unchecked" (recommended), or request refactor to checked exceptions (more work, breaks current code).

## T-1.8 — Playability check mapping to typed exceptions
- commit: 04c2a8f
- review: design/reviews/code/T-1.8-r1.md
- resolved: 2026-05-04
- notes: checkPlayability() on PlayerResponseExtractor; AC-1.7 + AC-5.2 satisfied; 16 tests; 100% coverage on method; CT-APP-6, CT-APP-7; 1 Discussion (C7 playabilityReason field)

## Discussion items from T-1.8
- C7 (CT-APP-7, cli-exit-codes.md § 1 exit 20): InnerTube's playabilityStatus.reason field not parsed into PlayerResponse; exit-20 message template expects it. Suggested schema-update to add Optional<String> playabilityReason to VideoDetails or PlayerResponse.

## T-1.10 — ErrorMapper translates exceptions to exit codes
- commit: 3ad7fef
- review: design/reviews/code/T-1.10-r1.md
- resolved: 2026-05-04
- notes: thin dispatcher; 29 tests; 91% coverage; CT-EXIT-UNIT-1..11 satisfied at mapper layer; 1 Discussion (C6 internal/code-1 path undocumented in spec)

## Discussion items from T-1.10
- C6 (AC-5.1, AC-5.2): internal/code-1 path for non-YDE throwables is not documented in cli-exit-codes.md; suggested ac-update for contract completeness.

## T-1.11 — Cli flag parsing (URL, --debug, --quiet)
- commit: eb48264
- review: design/reviews/code/T-1.11-r1.md
- resolved: 2026-05-04
- notes: required URL + storage-only flag getters; 16 tests; T-0.5 no-args test updated; T-1.12 completes exit-code mapping

## T-1.12 — Cli error handling pipeline (AC-5.1..AC-5.5)
- commit: 23d9d47
- review: design/reviews/code/T-1.12-r1.md
- resolved: 2026-05-04
- notes: try/catch Throwable → ErrorMapper → picocli getErr(); --debug stack trace; --quiet preserves errors; 17 tests; CT-EXIT-UNIT-1 fully at CLI

## T-1.13 — SLF4J logging at component boundaries (AC-10.1..10.5)
- commit: b47d19b
- review: design/reviews/code/T-1.13-r1.md
- resolved: 2026-05-04
- notes: per-class loggers; INFO on boundaries; ERROR once at Cli catch; --debug pre-scan sets slf4j-simple property; 15 tests

## T-1.13 — SLF4J logging at component boundaries (AC-10.1..10.5)
- commit: b47d19b
- review: design/reviews/code/T-1.13-r1.md
- resolved: 2026-05-04
- notes: per-class loggers; INFO on boundaries; ERROR once at Cli catch; --debug pre-scan sets slf4j-simple property; 15 tests

## T-1.14 — YoutubeDownloader M1 orchestrator skeleton (AC-9.1, AC-9.2)
- commit: d7d8604
- review: design/reviews/code/T-1.14-r1.md
- resolved: 2026-05-04
- notes: end-to-end M1 flow wired; stub DownloadResult (empty paths); DI into Cli; 22 tests; CT-APP-1/6/7 + CT-EXIT-UNIT-2/11 satisfied at orchestrator level; 1 Discussion (C8 DownloadRequest not yet introduced)

## Discussion items from T-1.14
- C8 (AC-9.1): AC-9.1 lists DownloadRequest as part of the public API surface but T-1.14 uses download(String) overload. DownloadRequest record is M2+ scope (arrives with flag-carrying requests). Suggested ac-update to note incremental introduction as flags arrive.

---

## Milestone M1 — CLOSED at d7d8604

## T-2.1 — FormatSelector (AC-1.3..1.5, AC-2.2)
- commit: 11d77c7
- review: design/reviews/code/T-2.1-r1.md
- resolved: 2026-05-04
- notes: codec avc1>vp9>av01; audio m4a>webm; 23 tests; CT-APP-3, CT-APP-4; 99% coverage

## T-2.2 — FormatSelector cipher check (AC-5.3)
- commit: c505ba4
- review: design/reviews/code/T-2.2-r1.md
- resolved: 2026-05-04
- notes: CipherRequiredException (exit 22) when all cipher; CT-APP-5; AC-5.3 message verbatim

## T-2.3 — StreamDownloader (HTTP Range resume + progress)
- commit: b917ef9
- review: design/reviews/code/T-2.3-r1.md
- resolved: 2026-05-04
- notes: 200/206 handling; 64KB chunks; ProgressCallback; 17 tests; 90% coverage

## T-2.4 — StreamDownloader retry (AC-12.4, INV-15)
- commit: 69e5fd5
- review: design/reviews/code/T-2.4-r1.md
- resolved: 2026-05-04
- notes: 2 retries, 500/1000 backoff, partFile truncation preserves resume; 15 tests; 1 Discussion (C7 AC-12.4 scope extension)

## Discussion items from T-2.4
- C7 (AC-12.4, 02-architecture.md § 4.2): AC-12.4 is InnerTube-scoped; stream retry whitelist (429+5xx+IOException) applied by analogy but not spec-mandated. Suggested ac-update to add AC-12.5 or extend § 4.2 to cover streams explicitly.

## T-2.5 — ProgressReporter scheduled rendering (AC-4.1..4.3)
- commit: 45fc4a6
- review: design/reviews/code/T-2.5-r1.md
- resolved: 2026-05-04
- notes: 100ms TTY / 1000ms non-TTY; AtomicLong state; daemon scheduler; 21 tests; 87% coverage; 1 Discussion (C7 ETA format)

## T-2.6 — ProgressListener + StderrProgressListener (AC-4.1, AC-9.3)
- commit: 51d6c46
- review: design/reviews/code/T-2.6-r1.md
- resolved: 2026-05-04
- notes: public @FunctionalInterface; CLI wrapper; ProgressCallback deprecated bridge; 15 tests

## T-2.7 — --quiet suppresses progress (AC-4.4)
- commit: e6d4058
- review: design/reviews/code/T-2.7-r1.md
- resolved: 2026-05-04
- notes: Cli picks NO_OP vs StderrProgressListener; download(String, ProgressListener) overload; 7 tests

## T-2.8 — OutputWriter (AC-3.1..3.6, NFR-MIN-DISK-FREE)
- commit: 66df778
- review: design/reviews/code/T-2.8-r2.md (r1: 1 Major overflow; r2: clean)
- resolved: 2026-05-04
- notes: sanitize + truncate + path derivation + overwrite + free-disk; 52 tests; 93% coverage; CT-EXIT-UNIT-9, CT-EXIT-UNIT-11; round-2 overflow guard

## T-2.9 — DownloadContext (.yt-tmp lifecycle, INV-6)
- commit: 8a5cd09
- review: design/reviews/code/T-2.9-r1.md
- resolved: 2026-05-04
- notes: UUID dirs; AutoCloseable; orphan WARN; 16 tests; INV-5+INV-6

## T-2.10 — --audio-only orchestrator path (AC-2.1, AC-2.3)
- commit: 4e873dc
- review: design/reviews/code/T-2.10-r1.md
- resolved: 2026-05-04
- notes: DownloadRequest record; download(DownloadRequest); audio-only end-to-end; 14 tests; addresses C8 from T-1.14

## T-2.11 — --max-height flag (AC-1.3)
- commit: 7f1d239
- review: design/reviews/code/T-2.11-r2.md (r1: 3 Blockers default=1080 not 0)
- resolved: 2026-05-04
- notes: default 1080; 0 disables; negative → exit 2; 8 tests; DEFAULT_MAX_HEIGHT constant

---

## Milestone M2 — CLOSED at 7f1d239

## T-3.1 — FfmpegMuxer.probeVersion (AC-13.1..13.3)
- commit: 0285cfd
- review: design/reviews/code/T-3.1-r2.md (r1: 1 Major Process leak)
- resolved: 2026-05-04
- notes: Version record + Comparable; NFR-MIN-FFMPEG-VERSION=4.0; 18 tests; CT-EXIT-60a partial; process.destroy in finally (r2 fix)

## T-3.2 — FfmpegMuxer.mux (AC-1.6, INV-8)
- commit: b6b9359
- review: design/reviews/code/T-3.2-r1.md
- resolved: 2026-05-04
- notes: exact command per 04-apis § 2.1.2; process.destroy finally; 10 tests; CT-EXIT-60a/60b

## T-3.3 — FfmpegMuxer.transcodeMp3 (AC-2.4)
- commit: f97932a
- review: design/reviews/code/T-3.3-r1.md
- resolved: 2026-05-04
- notes: libmp3lame -b:a 192k; DEFAULT_MP3_BITRATE constant; 8 tests; 100% method coverage

## T-3.4 — ffmpeg stderr ring-buffer (AC-13.4)
- commit: 2c635db
- review: design/reviews/code/T-3.4-r1.md
- resolved: 2026-05-04
- notes: captureLastLines helper; ArrayDeque cap=20; 11 tests; CT-EXIT-60b

## T-3.5 — FfmpegMuxer shutdown hook (INV-8, 02-arch § 5)
- commit: f7abb5f
- review: design/reviews/code/T-3.5-r1.md
- resolved: 2026-05-04
- notes: static LIVE_PROCESSES + SIGTERM→5s→SIGKILL; 10 tests; INV-8

## T-3.6 — FfmpegMuxer per-invocation timeout (NFR-FFMPEG-INVOCATION-TIMEOUT)
- commit: d170340
- review: design/reviews/code/T-3.6-r1.md
- resolved: 2026-05-04
- notes: 600s timeout; sleep 3600→2 per user (also fixed latent test bug via exec 2>/dev/null); 9 tests; CT-EXIT-60; verify time ~31s

## T-3.7 — --ffmpeg-location flag (AC-13.2 escape hatch)
- commit: 2d69f08
- review: design/reviews/code/T-3.7-r1.md
- resolved: 2026-05-04
- notes: DownloadRequest 6-arg record; plumbing only, T-3.8 uses it; 8 tests

## T-3.8 — Flow A mux integration (AC-1.6, state-machine)
- commit: dc27b91
- review: design/reviews/code/T-3.8-r1.md
- resolved: 2026-05-04
- notes: video+audio+mux end-to-end; 7-arg DownloadRequest (+debug); FfmpegMuxer factory; 18 tests; CT-APP-3/4 at orchestrator level

## T-3.9 — --audio-format mp3 + Flow B' (AC-2.4)
- commit: 703f83c
- review: design/reviews/code/T-3.9-r1.md
- resolved: 2026-05-05
- notes: AudioFormat enum; 8-arg DownloadRequest; probe → download → transcode; 11 tests; CT-EXIT-UNIT-10

## T-3.10 — skip-ffmpeg-check for M4A / transcript (AC-13.5, INV-10)
- commit: 1bfc0ab
- review: design/reviews/code/T-3.10-r1.md
- resolved: 2026-05-05
- notes: no production change needed; 6 tests + 1 @Disabled transcript placeholder; throwing-factory invariant pattern

---

## Milestone M3 — CLOSED at 1bfc0ab

## T-4.1 — FormatSelector.selectCaption (AC-6.4, AC-7.*, AC-8.1..8.3)
- commit: 9b41068
- review: design/reviews/code/T-4.1-r2.md (r1: 2 Blockers AC-7.4 vs AC-8.3 disambiguation)
- resolved: 2026-05-05
- notes: AC-8.1 chain + AC-7.* manual/ASR + AC-8.3 Available list; 22 tests; CT-APP-8/9/10; 100%

## T-4.2 — CaptionDownloader (AC-6.1, NFR-CAPTION-DOWNLOAD-TIMEOUT)
- commit: 70a1dae
- review: design/reviews/code/T-4.2-r1.md
- resolved: 2026-05-05
- notes: single GET, 10s timeouts, NetworkException on failure, 9 tests, CT-CAP-APP-1, 100%

## T-4.3 — CaptionConverter.parseXml (AC-6.1, AC-6.3, AC-11.1)
- commit: f9a557a
- review: design/reviews/code/T-4.3-r1.md
- resolved: 2026-05-05
- notes: pure function; XXE-safe; entity decoder; 20 tests; CT-CAP-APP-1, CT-CAP-APP-2

## T-4.4 — CaptionConverter.toSrt + formatSrtTimestamp (AC-6.2)
- commit: dda9a0e
- review: design/reviews/code/T-4.4-r1.md
- resolved: 2026-05-06
- notes: scope-collapsed into T-4.3; 17 tests; CT-CAP-APP-3; 85% coverage

## T-4.5 — CaptionConverter.toTxt + PlainTextTranscript (AC-6.2, § 2.8)
- commit: fbdb82c
- review: design/reviews/code/T-4.5-r1.md
- resolved: 2026-05-06
- notes: duplicate-prefix collapse; PlainTextTranscript record; 16 tests; CT-CAP-APP-4; 100%

## T-4.6 — --transcript/--lang/--no-asr CLI flags (AC-6.1, AC-7.4, AC-8.2)
- commit: f714155
- review: design/reviews/code/T-4.6-r2.md (r1: 1 Blocker stray .mp4 + 1 Major .gitignore)
- resolved: 2026-05-06
- notes: 11-arg DownloadRequest; .gitignore media patterns; SrtDocument T-4.4 carryover included; 17 tests

## T-4.7 — DownloadResult.usedAsrFallback wiring verification (AC-7.3, INV-16)
- commit: 86ff3b6
- review: design/reviews/code/T-4.7-r1.md
- resolved: 2026-05-06
- notes: no production change; field from T-1.14 stub; @Disabled INV-16 placeholder pending T-4.10; 7 tests

## T-4.8 — ThumbnailDownloader (NFR-THUMBNAIL-DOWNLOAD-TIMEOUT, AC-9.1)
- commit: 26612c8
- review: design/reviews/code/T-4.8-r1.md
- resolved: 2026-05-06
- notes: pickBest by width\u00d7height; 10s timeout; 11 tests; 100%

## T-4.9 — --thumbnail CLI flag
- commit: d46032a
- review: design/reviews/code/T-4.9-r1.md
- resolved: 2026-05-06
- notes: 12-arg DownloadRequest; plumbing only; 7 tests

## T-4.10 — Caption + thumbnail orchestrator integration (state-machine § 5)
- commit: 556c4ce
- review: design/reviews/code/T-4.10-r1.md
- resolved: 2026-05-06
- notes: 7-arg YoutubeDownloader ctor; transcript+thumbnail side-effects on Flow A/B/B'; INV-16 enabled end-to-end; CT-APP-8/9/10; 18 tests

## T-4.11 — Flow C transcript-only (state-machine Flow C)
- commit: 51a10dd
- review: design/reviews/code/T-4.11-r1.md
- resolved: 2026-05-06
- notes: --transcript alone skips media; no muxer invocation; 13 tests; CT-APP-8/9/10 via Flow C

---

## Milestone M4 — CLOSED at 51a10dd

## T-5.1 — CLI flag audit + --video flag (04-apis § 3.1.2, AC-2.5)
- commit: a3d2836
- review: design/reviews/code/T-5.1-r2.md (r1: 2 Majors AC-2.5 WARN missing)
- resolved: 2026-05-06
- notes: 13-arg DownloadRequest (+video); AC-2.5 WARN emitted on --video --audio-only; 9 tests

## T-5.2 — Exit-code correctness sweep (cli-exit-codes § 4)
- commit: e70377d
- review: design/reviews/code/T-5.2-r1.md
- resolved: 2026-05-06
- notes: audited 47 throw sites; fixed DownloadContext RuntimeException→FilesystemException; 48 sweep tests; all 11 CT-EXIT-UNIT re-satisfied

## T-5.3 — --debug flag polish + OBS-1 fix (AC-5.1, AC-5.4, AC-10.5)
- commit: 07b40b8
- review: design/reviews/code/T-5.3-r1.md
- resolved: 2026-05-06
- notes: LOGGER.error no Throwable arg; stack trace only on --debug; OBS-1 closed; 8 tests

## T-5.4 — Integration test suite -P integration (AC-11)
- commit: 1ce5f17
- review: design/reviews/code/T-5.4-r1.md
- resolved: 2026-05-06
- notes: ProcessBuilder fat-jar invocation; 5 active + 1 @Disabled (T-5.10); CT-EXIT-2b

## T-5.5 — Fixture provenance documentation (contract-tests § 7)
- commit: 21a0626
- review: design/reviews/code/T-5.5-r1.md
- resolved: 2026-05-06
- notes: real capture deferred to v1.1+ (network unavailable); x-captured-on metadata on 7 fixtures; README.md; 6 tests

## T-5.6 — Full v1.0.0 README (05-operations.md)
- commit: e05dc3b
- review: design/reviews/code/T-5.6-r1.md
- resolved: 2026-05-06
- notes: 12 sections; 17 flags table; 14 exit codes; 5 design links verified; AC-13.5 carve-out documented

## T-5.7 — GitHub Actions CI workflow
- commit: a1ff421
- review: design/reviews/code/T-5.7-r1.md
- resolved: 2026-05-06
- notes: ubuntu+macos matrix; Java 17 Temurin; Maven cache; integration gated on main push; README badge

## T-5.8 — Release preparation artifacts (tag deferred to user)
- commit: 1e0f6fc
- review: design/reviews/code/T-5.8-r1.md
- resolved: 2026-05-06
- notes: CHANGELOG + RELEASE_NOTES + RELEASING; v1.0.0 tag NOT created (user action required)

## T-5.9 — Fat-jar size + structure integration tests (05-ops § 6.1)
- commit: 41a9437
- review: design/reviews/code/T-5.9-r1.md
- resolved: 2026-05-06
- notes: 10 MB cap enforced; JAR validity + Main-Class assertion; 2 @Tag("integration") tests

## T-5.10 — Smoke-test matrix (NFR-SUPPORTED-OS)
- commit: c6f9f78
- review: design/reviews/code/T-5.10-r1.md
- resolved: 2026-05-06
- notes: SMOKE_TESTS.md + scripts/smoke-test.sh + CI matrix (macos-14 Apple Silicon + ubuntu-latest); 6/6 checks pass locally

---

## Milestone M5 — CLOSED at c6f9f78

---

## ALL MILESTONES COMPLETE — v1.0.0 READY FOR TAG

Total tasks resolved: 45 across M0..M5
HEAD: c6f9f78

Next user action:
  See RELEASING.md for v1.0.0 tag + GitHub Release ceremony.
  Tag creation is intentionally deferred — requires explicit user action
  (destructive git operation with downstream publication consequences).

## T-1.5 (DCR-1 resume) — bump ANDROID client triplet to amended NFR values
- commit: 19e2e5b
- amendment commits: 9501351 (NFRs) + fd1b98b (ripple sweep)
- review: design/reviews/code/T-1.5-dcr1-r1.md
- resolved: 2026-05-06
- notes: NFR-ANDROID-CLIENT-VERSION 19.09.37→21.02.35, SDK 34→30, UA + osVersion refreshed; extracted shared HttpConstants.ANDROID_USER_AGENT across 4 HTTP boundary components; Risk R-1 / OQ-A first activation
