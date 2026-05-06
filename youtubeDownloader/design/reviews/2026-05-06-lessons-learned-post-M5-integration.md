---
doc: lessons-learned
phase: post-M5-integration
date: 2026-05-06
author: specDrivenCoordinator (with user collaboration during integration testing)
head_at_write: 19e2e5b + in-flight portrait-fix
---

# Lessons Learned — Post-M5 Integration Testing

A retro covering the live-URL integration testing session on 2026-05-06, after all 45 tasks were marked complete at commit `c6f9f78` and DCR-1 landed the first NFR refresh at `19e2e5b`. User drove the session by testing real YouTube URLs. Several assumptions baked into the design broke or were wrong. This document captures what happened and what we'd do differently.

## Summary in one line

Every task passed its unit and fixture tests, the spec-driven machinery did its job, the tool shipped to M5 close — and yet the very first real-URL test revealed three distinct bugs the test suite could not catch. That gap is the main lesson.

---

## Timeline of the session

1. User ran `java -jar ... "https://www.youtube.com/watch?v=Y8IAS4999-Q"` → HTTP 400 from InnerTube `/player`
2. Diagnosed as Risk R-1 (pinned ANDROID client version stale); DCR-1 amendment bumped `NFR-ANDROID-CLIENT-VERSION = 19.09.37 → 21.02.35` plus SDK/UA/osVersion. Metadata call now returns 200.
3. Next failure: CDN `videoplayback` GET returned 403. Coordinator misdiagnosed as PO Token requirement, then as chunk-size enforcement. Both diagnoses ended up wrong.
4. User pointed to a simple Python snippet using `Range: bytes=0-1048575`, which triggered the realisation that the 1 MB boundary was **content-specific**, not universal — Y8IAS4999-Q is a premium-locked / geo-restricted Indian-market video where YouTube intentionally serves only the first 1 MB to non-authenticated viewers.
5. Tested Rickroll (`dQw4w9WgXcQ`) — full 81 MB muxed MP4 downloaded successfully. Code actually works for ordinary public videos.
6. Tested `qE5Hsy6uzMg` (YouTube Short, portrait) — download succeeded but QuickTime showed no video, only audio. Diagnosed as a FormatSelector bug: the max-height filter interprets `height` as "quality tier" but for portrait Shorts YouTube reports `height=1920, width=1080` so the default `max-height=1080` excludes the H.264 1080p option and picks VP9 instead. MP4 with VP9 isn't playable in QuickTime.
7. User verified by downloading H.264 version via a standalone Python script + ffmpeg mux — plays correctly, confirming the bug is in selection, not in the pipeline. Fix dispatched.

---

## Lessons — production code

### L-1. Real-network tests are the only tests that matter for this class of tool

Our 1000+ unit tests exercised fixtures captured on 2026-05-03. Every test was green. Every diagnostic dimension (JaCoCo coverage, contract tests, spec adherence, exit codes) reported healthy. None of them could catch:

- **YouTube rejecting the pinned ANDROID client version** (fixture had the old version hardcoded)
- **CDN 403 on specific videos** (fixture URLs don't correspond to real CDN endpoints)
- **VP9/AV1 codec selection picking wrong stream for portrait videos** (fixture data was a rickroll-shaped landscape video)

A single real-URL integration test running in CI against a known-stable public video would have caught the first two within days of YouTube's changes landing. That test existed in the repo (`YoutubeDownloaderIT.realVideoDownload_audioOnly`) but was `@Disabled`.

### L-2. The reason the IT was `@Disabled` was exactly wrong

The coordinator (me) wrote this guidance into the T-5.4 implementer brief:

> *"IMPORTANT: tests that require real network on CI may fail without ffmpeg on the build host... Full end-to-end tests (--audio-only against a real URL) will work locally with ffmpeg but may need to be @DisabledIfEnvironmentVariable on CI without ffmpeg."*
> *"Do NOT actually download from YouTube in the committed tests — too flaky. The T-5.10 smoke-test task covers manual/CI verification."*

The stated reason was "flaky tests produce red CI, developers learn to ignore failures, false sense of security." That reasoning is valid for a regular feature regression suite. It is **backwards** for a tool whose entire value proposition is "it downloads from YouTube." For this class of tool:

- A flaky real-URL test that goes red when YouTube changes is **the correct signal** — the maintainer WANTS to know immediately
- Green unit tests + red real tests is a TRUE state of the world ("code is internally consistent; external contract broke") — that's diagnostic gold, not noise
- The manual `SMOKE_TESTS.md` checklist compensation never forces a maintainer to run anything before tagging — a checklist is not a gate

**The IT test should have been `@EnabledIfEnvironmentVariable(named = "YOUTUBE_IT_ENABLED", matches = "true")` — default enabled locally, gated by env var for CI where desired**, rather than `@Disabled` which is opt-in-always.

Worse, the `@Disabled` message referred to "requires ffmpeg" — but the test used `--audio-only` which is an M4A path that per AC-13.5 deliberately does NOT need ffmpeg. The stated reason was wrong, making the decision even harder to re-evaluate.

### L-3. Fixture-based tests give false confidence about external contracts

The spec correctly identified offline testability as a design goal (AC-11.1, AC-11.2). That was the right call for unit tests. But the project conflated "we have offline tests" with "we have sufficient tests", which is different.

yt-dlp's test suite has 80+ `test_download` tests that hit real YouTube URLs in CI. When they break, someone fixes them within hours. That's the only way to know the tool actually works.

### L-4. OOS-2 was scoped correctly but the manifestation changed

The spec carved out "signature deciphering" as out-of-scope (OOS-2). The assumption was that `signatureCipher` fields in the InnerTube response would be the failure signal. In 2026, the actual gates are:

- **Geo-restriction with 1 MB preview** (`gcr=` in URL; serves first 1 MB only to non-region viewers)
- **SABR-only responses** (`streamingData.adaptiveFormats[i]` has no `url` or `signatureCipher` fields at all; only `serverAbrStreamingUrl`)
- **PO Token requirements** (client-dependent)

None of those three existed when the spec was written. Risk R-1 (client version deprecation) and R-2 (signature cipher becomes mandatory) covered the spirit but not the letter. The tool's AC-5.3 error message says "this video requires JavaScript signature deciphering, which is out of scope for this tool" — but a user today hitting any of the three new gates gets a generic `Error: network: HTTP 403` because we didn't know to catch and re-map those.

### L-5. QuickTime reveals codec-preference failures that ffprobe does not

Our tests verified "file is a valid MP4 with two streams". They did not verify "file plays in a real player." QuickTime's strict codec policy (H.264/HEVC/ProRes only, no VP9) caught our selector bug in 30 seconds of user testing where 1000 unit tests missed it entirely. A "download file, play in VLC or QuickTime, watch 10 seconds" step in the smoke checklist would have caught this.

### L-6. Portrait / landscape orientation is a real design variable

The design's AC-1.3 says "height ≤ max-height" with no discussion of orientation. YouTube Shorts' vertical format inverts the convention — the `height` field in the InnerTube response is the LONGER dimension for 9:16 content. The tool's `FormatSelector` interpreted the field literally, which is why the 1080p H.264 Shorts format was filtered out and the lower-resolution VP9 chosen instead.

The fix (use `min(width, height)` as the "quality height") is a small change but reveals a blind spot in the original requirements analysis. The spec did not explicitly consider non-16:9 content, and neither did any code reviewer across M1 and M2.

---

## Lessons — diagnostic process (mine as coordinator)

### L-7. I misdiagnosed twice in one session

**First wrong diagnosis — PO Token.** I saw HTTP 403 on CDN, looked at yt-dlp source confirming ANDROID has `GVS_PO_TOKEN_POLICY required=True`, and jumped to "PO Token gate." I proposed a diagnostic `--po-token` flag. When the user shared a Chrome cURL, I realized Chrome's session was WEB client + SABR path, not extractable for ANDROID. I admitted the mistake.

**Second wrong diagnosis — chunk-size enforcement.** I saw `bytes=0-10485759` return 403 and `bytes=0-1023` return 206, concluded the CDN caps chunk size. Ran an implementer fix reducing chunk size to 1 MB. It worked for the first 1 MB then failed. I concluded it was n-parameter throttling (OOS-2). The actual cause was content-specific preview-gating: the video was premium-locked Indian content; the CDN gives the first 1 MB as preview.

Both errors came from the same failure mode: **pattern-matching curl output to yt-dlp's source code without testing the pattern on a known-working control**. If I had tested Rickroll first (known global public video), I would have seen end-to-end success at step 1 and known the boundary was content-specific, not universal.

### L-8. Control experiments matter

Rickroll works. Mere Sai fails at 1 MB. Lion King Shorts has no stream URLs at all. These are three different failure modes conflated under "HTTP 403" until a control experiment separated them. The lesson: when diagnosing a failure, **immediately test the control** before building theories. Two minutes of Rickroll testing would have saved 45 minutes of wrong diagnosis.

### L-9. User's "did you try this simpler thing" was more valuable than my investigation

The user's Python snippet with `Range: bytes=0-1048575` was the unblocker. I had been running curl probes for 30 minutes building a theory. The snippet didn't add new information per se — it reframed the question. "Have you tried the straightforward thing first?" turned out to be the right question.

Lesson for coordinator role: when diagnosing, **narrate what I'm about to test and why** so the user can interrupt with "just try X instead" before I commit to a theory.

### L-10. Spec-aligned deferral is a real tool in the box

When the diagnosis concluded "this is OOS-2 territory", the right answer was to accept it, improve the error message, and ship. Not to keep trying. The spec had explicit scope carve-outs (OOS-2, R-1, R-2, OQ-A) for exactly this class of problem. Using them as "reasons we don't try further" when the investigation hits a genuine scope boundary is good engineering. I kept wanting to try one more thing — user's calm return to "let's just verify with a simple test" kept me honest.

---

## What should change going forward

### Pre-v1.0 (before tagging)

1. **Enable `YoutubeDownloaderIT.realVideoDownload_audioOnly`** with a known-stable public URL (Rickroll `dQw4w9WgXcQ` or a Creative Commons sample). Run on every CI build. If red, maintainer investigates; don't tag v1.0.0 red.
2. **Add the portrait-video selector fix** — in flight already.
3. **Improve CDN-403 error message** to catch common failures: "the video appears geo-restricted, premium-only, or SABR-only; not supported in MVP (AC-5.3 / OOS-2)." Map partial-success-then-403 to `VideoUnavailableException` (exit 20) rather than bare `NetworkException`.
4. **Add one more real-URL IT** for `--audio-only` + `--transcript` + `--thumbnail` on the same stable URL. Each should assert the file is >100 bytes and is a valid MP4/m4a/srt/jpg.
5. **Document Shorts / portrait / premium-locked / geo-restricted limitations** in README.md troubleshooting section explicitly.

### During v1.x maintenance

6. **Subscribe to yt-dlp release notes** — when they push a client-version bump, we should follow. Keep a `CLIENT_REFRESH.md` maintainer doc logging what we track.
7. **Monthly real-URL smoke in CI** against 3-5 canonical URLs — when any fails, that's the R-1 / R-2 monitoring signal the spec contemplated.
8. **Add a "play in VLC for 3 seconds" smoke step** to SMOKE_TESTS.md — ffprobe "file has 2 streams" is not the same as "file plays." Could be automated via `ffmpeg -i foo.mp4 -f null -t 5 -`.

### Future DCRs to consider

9. **DCR candidate**: widen AC-5.3 message to cover the four current R-2-family failures (geo-restricted, premium-locked, SABR-only, PO-Token-required), not just "signature cipher". The current AC-5.3 wording is narrow; reality is broader.
10. **DCR candidate**: clarify AC-1.3 for portrait content ("max-height" means "max of the shorter dimension").
11. **DCR candidate**: update OQ-A in 01-overview.md from "check if ANDROID client version still works" to "monitor yt-dlp's ANDROID + WEB + TV client choice for signal on which client family YouTube is currently honoring for the `/player` endpoint AND for CDN streams."

---

## What the spec-driven process did well

To balance the criticism: the process caught real problems.

- **DCR-1 flow executed cleanly.** Within one session, user-approved amendment → designer edit to 4 spec files + ADR log → code refresh → tests re-pass. Amendment commit + code commit have bidirectional traceability (commit body references amendment SHA, spec review file references task). That is the spec-driven system functioning exactly as designed for its "NFR drift" use case.
- **Hard Rule 2 kept me from cheating.** When the user's `sleep 3600` concern came in, my first instinct was to edit the test directly. The agent write-scope constraint forced routing through the tester lane — who found a latent bug in the same test (the 3600-second sleep never actually tested the timeout because of a stderr-read deadlock). The process catching a latent test bug while fixing the user's surface complaint is a clean win.
- **Scope discipline held across 45 tasks.** No M2 task accidentally pulled in M3 work, no implementer added features beyond the task row. Where the implementer made a spec-text-vs-AC interpretation call (T-0.8's Enforcer → SecurityManager deviation), they documented it clearly and the reviewer accepted with rationale. Hard Rule 6 (spec > task text > implementer preference) was consistently honored.
- **Rigorous amendment bookkeeping.** Every DCR has a lifecycle entry in `design/open-questions.md`, a pair of commits with traceability, and — for the ripple-unresolved case — explicit user approval before the sweep. When we revisit DCR-1 for R-2 / client rotation in v1.1+, the provenance is all there.

The process is sound. The manifestations of failure that bit us are specific to the volatile external boundary (YouTube's anti-abuse policy) — not flaws in spec-driven development itself.

---

## Epilogue — honest v1.0.0 posture

What the tool can honestly claim at commit `19e2e5b` + the pending portrait-fix:

**Works:**
- Metadata fetch (InnerTube `/player`) for all visible videos
- Audio-only M4A download for unrestricted public videos (verified on Rickroll, 3.3 MB file, end-to-end)
- Video + mux MP4 download for unrestricted public videos (verified on Rickroll, 81 MB file, plays in QuickTime)
- Caption fetch for videos with captions available (URL mechanism works; srv3 XML format parser has a known gap)
- Thumbnail fetch (URL mechanism works)
- Flow A, B, B', C orchestration all wire correctly

**Does not work:**
- Videos with `gcr=<region>` geo-restriction when requester is outside region — CDN serves only first 1 MB
- Premium-locked subscription content — same 1 MB preview behavior
- Videos returning SABR-only responses (no direct stream URLs at all, e.g., Lion King Shorts video tested)
- srv3-format captions parse to empty file (parser bug)
- Portrait Shorts with VP9-preferred selection (pending portrait-fix dispatch)

**Known tomorrow-risk:**
- ANDROID client version 21.02.35 will also eventually be deprecated (R-1). The next refresh will be another DCR of identical shape to DCR-1. The refresh-log section in ADR-0001 is the format for capturing these as they land.
- YouTube may roll SABR-only responses to broader content categories at any time. If the SABR-only set grows to include most content, the tool's "works for public videos" claim erodes and a v2.0 SABR-support effort becomes real.

These are honest boundaries. The spec anticipated them; the operational reality is that the spec was right.
