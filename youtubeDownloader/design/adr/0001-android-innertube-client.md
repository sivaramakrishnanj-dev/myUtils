# ADR 0001 — Use the ANDROID InnerTube client as the primary stream-metadata source

- **Status:** Accepted
- **Date:** 2026-05-03
- **Deciders:** srk
- **Tags:** innertube, youtube, cipher, anti-abuse

## Context

YouTube does not publish a documented API for downloading video streams. Every tool that downloads a YouTube video — yt-dlp, youtube-dl, NewPipe, and countless smaller forks — works by reverse-engineering YouTube's **InnerTube API**, the same internal JSON-RPC API that the official YouTube web, mobile, and TV clients use.

The InnerTube `/youtubei/v1/player` endpoint accepts a `context.client` block identifying which YouTube client is making the request (web, mobile web, Android, iOS, Android VR, TV, etc.) and returns a JSON payload containing stream metadata, caption tracks, thumbnails, and playability flags. Different clients receive different response shapes. Most importantly, different clients receive stream URLs with different encodings:

- **Web and mobile-web clients** typically receive URLs whose signature is scrambled by a JavaScript function embedded in YouTube's `base.js` player script. Recovering a usable URL requires downloading `base.js`, parsing it, extracting the descrambling function, and executing it in a JavaScript runtime (Nashorn, GraalJS, Rhino, deno, node). This is known as "signature deciphering" and is the single most complex piece of yt-dlp. It is explicitly out of scope for this MVP per OOS-2.
- **Android, iOS, Android VR, TV clients** typically receive stream URLs that do NOT require signature deciphering — the URLs in `streamingData.adaptiveFormats[].url` can be used directly as HTTP GET targets. These clients are the path of choice for any tool that wants to avoid shipping a JavaScript engine.

Two forces shape the choice of which non-web client to use:

- **Response stability.** The shape of the JSON response must be predictable enough that our fixture-based offline tests (AC-11.1, AC-11.2) aren't constantly breaking. The ANDROID client has the longest-running predictable shape among the non-web clients in the public reverse-engineering community.
- **Anti-abuse resilience.** YouTube periodically tightens its anti-abuse defences — some historical mitigations have blocked specific client-name / client-version combinations, injected PO-token requirements, or required new request fields. A wrong or stale client-version triplet results in either HTTP 4xx responses or reduced-format responses that fail our format-selection rules (AC-1.3, AC-1.4).

yt-dlp's own default (as of early 2026) uses `android_vr,web_safari` with fallback logic based on age-restriction, premium status, and logged-in state. That complexity is out of scope for an MVP. We need one simple default that works for public, non-live, non-age-restricted videos (OOS-3, OOS-6, OOS-7).

AC-12.1, AC-12.2, and AC-12.3 already require a consistent `context.client` block and a plausible `User-Agent` for every InnerTube request. NFR-ANDROID-CLIENT-VERSION (`19.09.37`), NFR-ANDROID-SDK-VERSION (`34`), NFR-ANDROID-USER-AGENT, NFR-INNERTUBE-HL (`en`), and NFR-INNERTUBE-GL (`US`) already pin the specific values. This ADR records the reasoning behind choosing the ANDROID client family in the first place.

## Decision

**We use the ANDROID InnerTube client as the sole stream-metadata source for MVP. Every InnerTube `/youtubei/v1/player` request is sent with `context.client.clientName = "ANDROID"` and a matching `clientVersion`, `androidSdkVersion`, `hl`, `gl`, and HTTP `User-Agent` per the NFRs listed above.**

Concretely:

- The `InnerTubeClient` component in `02-architecture.md` § 1.2.1 is hard-wired to the ANDROID context for MVP. No client-selection strategy, no fallback to other clients.
- If the ANDROID client returns stream URLs whose `signatureCipher` field is non-empty for all candidates (i.e., YouTube has started requiring signature deciphering even for the Android client for a given video), the tool fails fast with AC-5.2 exit code `22` and the cipher-specific error message in AC-5.3. Users are explicitly directed to yt-dlp for those URLs.
- The Android client identity values (client version, SDK, User-Agent) are pinned as NFRs so that updating them is a requirements round (a new NFR review round), not a silent code change. YouTube's anti-abuse occasionally deprecates specific client versions; when that happens, we expect OQ-A in `01-overview.md` to trigger and land a new NFR round.

## Alternatives considered

### Alternative 1 — WEB client with signature deciphering

Pros:
- WEB is the "canonical" client — its response shape is the most widely documented in the reverse-engineering community.
- Works for every public video, including ones where Android returns only cipher-protected URLs.

Cons:
- **Requires a JavaScript engine.** Recovering a usable stream URL means downloading `base.js`, extracting the descrambling function via regex, and executing it. The Java options are:
  - **GraalJS** — modern, fast, but a ~40 MB addition to the fat jar. Licensing is complex (GFTC, not fully FOSS for commercial use).
  - **Nashorn** — removed from the JDK in Java 15+. No longer viable on Java 17.
  - **Rhino** — works, but slow, and the signature function regex / AST extraction is a maintenance burden.
  - **External deno / node / bun subprocess** — another external binary dependency on top of ffmpeg. Violates NFR-SUPPORTED-OS simplicity.
- **Increases maintenance surface 10×.** yt-dlp's signature-deciphering module is thousands of lines of Python precisely because YouTube rotates the scrambling function regularly. Every rotation breaks the tool until we update the regex. This is not a good fit for a learning-project MVP (see OOS-2 rationale).
- **Bigger fat jar.** Adding GraalJS pushes the jar size from ~5 MB to ~45 MB.

Rejected because it trades a 10× complexity increase for covering the <5% of videos where the Android client returns only ciphered URLs. The correct MVP response for those videos is to fail fast with a clear message pointing to yt-dlp (AC-5.3, AC-5.2 exit code 22).

### Alternative 2 — IOS or TV client instead of ANDROID

Pros:
- Both also return uncipher'd URLs in most cases.
- `TVHTML5_SIMPLY_EMBEDDED_PLAYER` ("TV embed") has been observed to bypass some age restrictions that ANDROID hits — could widen the supported video set marginally.

Cons:
- **Less widely documented** than ANDROID in community reverse-engineering tooling. Less community signal means slower response when YouTube changes the shape.
- **TV client response shape** differs from ANDROID — `videoDetails.title` moves, `playabilityStatus` enum values differ, caption tracks are sometimes absent. Adopting TV would require our schema and our `PlayerResponseExtractor` to handle a different shape for no meaningful value over ANDROID for the MVP target (public non-age-restricted non-live videos).
- **IOS client** has occasionally required a separate PO-token workflow that ANDROID has not. Adds complexity.

Rejected because ANDROID has the best documentation / community-signal / response-stability combination for the MVP use cases.

### Alternative 3 — Multi-client fallback chain (ANDROID primary, WEB fallback, TV fallback)

Pros:
- Highest coverage of the video corpus.
- Mirrors yt-dlp's strategy.

Cons:
- **Every extra client doubles the maintenance surface.** Each client has its own context-block shape, its own User-Agent expectation, its own occasional anti-abuse response quirks. Every time YouTube changes anything, we have N things to fix instead of 1.
- **Multiplies test fixture count.** Contract tests (Phase 3) need a positive fixture per client, a negative fixture per client, and fixtures for the transitions between clients. From ~6 fixtures to ~18.
- **Premature for MVP.** The MVP goal is to get the primary happy path working end-to-end on public videos. A fallback chain is a v2 feature.

Rejected as gold-plating for MVP. Adding a fallback client when the primary fails is easy to introduce later if/when a concrete failure mode demands it — the `InnerTubeClient` interface in § 1.2.1 isolates the decision behind one boundary.

## Consequences

**Positive:**

- **No JavaScript engine in the dependency graph.** Fat jar stays small (~5 MB), build stays simple, no GraalJS / Rhino / subprocess complexity. This is a large quality-of-life win for a single-maintainer project.
- **AC-5.3 becomes trivial to implement.** If `FormatSelector` sees only ciphered URLs, it throws `CipherRequiredException` and `ErrorMapper` maps it to exit `22` with the cipher-specific message. No "maybe we can decipher" branch.
- **Single client = single request per run** (AC-12.3) is naturally satisfied. No fallback loop, no retry-with-different-client logic, no PO-token-acquisition sub-protocol.
- **Fixtures are simpler.** Phase 3's `06-formal/innertube-player-request.schema.json` and `innertube-player-response.schema.json` each describe one shape — the ANDROID client's. Fewer schemas, fewer contract tests, faster Phase 3.
- **Response-stability alignment with yt-dlp's community.** When YouTube changes the ANDROID response shape, the community usually catches it within hours. We can watch yt-dlp's issue tracker as an early-warning system for our own contract tests.

**Negative / accepted trade-offs:**

- **Videos whose Android-client response has only ciphered URLs will fail** with exit `22`. Users who hit this have a clear remediation (run yt-dlp for that specific URL) but it is an explicit limitation of the tool, called out in README, in `01-overview.md`, and in the AC-5.3 error message itself.
- **Tool will break when YouTube deprecates the specific pinned client version.** This is expected — captured as OQ-A in `01-overview.md`. The fix is a new NFR review round updating `NFR-ANDROID-CLIENT-VERSION` and friends, not a code change. Estimated frequency: once or twice a year based on yt-dlp's release cadence for similar changes.
- **No coverage for age-restricted or members-only videos.** Those videos require authenticated requests (cookies / OAuth), which is OOS-6 and OOS-7 regardless of client choice.
- **No coverage for PO-token-required videos.** If YouTube starts requiring PO tokens for the ANDROID client (as has happened for WEB in some conditions), the tool fails. Adding PO-token acquisition is its own ADR-worthy effort — out of scope for MVP.

**Neutral:**

- The Android client returns `videoDetails.audioLanguage` reliably, which is what AC-8.1 step 3 depends on for caption-language resolution. No change to the caption logic.
- Thumbnail URLs in the Android response are identical to the web response's — no downstream difference.
- The CDN URLs returned by the Android client for `googlevideo.com` are the same format as the web client's, so the `StreamDownloader` component does not care which client sourced the URL (§ 1.2.2).

## References

- `00-requirements.md` § User stories — US-1 (download full video), US-5 (fail fast with useful error), US-12 (behave as a plausible InnerTube client)
- `00-requirements.md` § Acceptance criteria — AC-1.2 (single InnerTube request), AC-5.2 (exit-code category 22), AC-5.3 (cipher-specific error message), AC-12.1..AC-12.4 (InnerTube request identity and retry)
- `00-requirements.md` § Non-functional requirements — NFR-ANDROID-CLIENT-VERSION, NFR-ANDROID-SDK-VERSION, NFR-ANDROID-USER-AGENT, NFR-INNERTUBE-HL, NFR-INNERTUBE-GL
- `00-requirements.md` § Out of scope — OOS-2 (signature deciphering), OOS-6 (cookies / auth), OOS-7 (age-restricted)
- `01-overview.md` § External contracts (assumed) — fragility note on the ANDROID client triplet; OQ-A (ANDROID client triplet still-works validation target Phase 5)
- `02-architecture.md` § 1.2.1 — `InnerTubeClient` component references this ADR
- yt-dlp README § Extractor Arguments → youtube → `player_client` — documents yt-dlp's own default and fallback chain; the model we are intentionally simplifying
- Community reverse-engineering references:
  - [`tyrrrz/YoutubeExplode`](https://github.com/Tyrrrz/YoutubeExplode) — .NET; uses Android client as primary
  - [`sealedtx/java-youtube-downloader`](https://github.com/sealedtx/java-youtube-downloader) — Java; reads Android client family
  - [`TeamNewPipe/NewPipeExtractor`](https://github.com/TeamNewPipe/NewPipeExtractor) — Java; multi-client strategy, more complex than MVP needs
