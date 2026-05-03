# ADR 0002 — Use OkHttp for HTTP

- **Status:** Accepted
- **Date:** 2026-05-03
- **Deciders:** srk
- **Tags:** http, library, testing, observability

## Context

The tool makes HTTP requests to four distinct YouTube boundaries (InnerTube `/player`, video CDN `googlevideo.com`, timedtext endpoint, thumbnail CDN `i.ytimg.com`) and one internal subprocess boundary (`ffmpeg -version` and mux / transcode) — the latter not over HTTP. For the four HTTP boundaries, we need:

1. **Progress reporting during body read.** The video-stream download path (`StreamDownloader` in `02-architecture.md` § 1.2.2) must emit byte-progress events as bytes arrive, so `ProgressReporter` can update the user (AC-4.1). A 500 MB file download without progress feels broken even when it isn't.
2. **Retries with exponential backoff.** InnerTube retry budget is `NFR-INNERTUBE-MAX-RETRIES = 3` at `NFR-INNERTUBE-BACKOFF-BASE = 500 ms` (AC-12.4). Stream retry budget is `NFR-STREAM-MAX-RETRIES = 2` with byte-0 restart. We want these handled inside the HTTP layer, not hand-rolled in every component.
3. **Tight timeout control per request.** Different boundaries have different budgets: InnerTube total 30 s (`NFR-INNERTUBE-REQUEST-TIMEOUT`), caption total 10 s (`NFR-CAPTION-DOWNLOAD-TIMEOUT`), thumbnail total 10 s (`NFR-THUMBNAIL-DOWNLOAD-TIMEOUT`), streams unlimited total but 30 s idle-read (`NFR-STREAM-DOWNLOAD-TIMEOUT`, `NFR-NETWORK-TIMEOUT-READ`). The client must let us configure these on a per-call basis without spinning up multiple client instances.
4. **Mockable for offline tests.** AC-11.1..AC-11.4 require unit tests that do not reach the network. We need a way to stand up a scripted HTTP server in-process for tests.
5. **Low dependency cost.** The project ships as a fat jar; every MB matters. The CLI's `java -jar yt-downloader-1.0.0.jar ...` start-up time matters too.
6. **Proven stability on Java 17.** NFR-JAVA-VERSION = 17 means we need a library that's battle-tested on that runtime.

The three realistic Java HTTP-client options on Java 17 are:

- **JDK `java.net.http.HttpClient`** (built-in since Java 11)
- **Apache HttpClient 5**
- **OkHttp** (Square)

The decision affects every HTTP-making component — `InnerTubeClient`, `StreamDownloader`, `CaptionDownloader`, `ThumbnailDownloader` — and the test infrastructure for all four.

## Decision

**We use OkHttp 4.x for all HTTP transport.** Its interceptor model, its companion `MockWebServer` for offline tests, its built-in connection pooling and automatic retry-on-connection-failure, its tiny public API, and its first-class Kotlin/Java interop make it the best fit for this project's needs.

Concretely:

- A single `OkHttpClient` instance is configured at orchestrator start-up with defaults from the NFRs (10 s connect, 30 s read, 30 s call). This base client is reused across all four boundaries.
- Per-call timeouts and retry configuration are applied via `.newCall(request)` with `request.newBuilder()...` and via `Call.timeout()` — no need to instantiate separate clients for different boundaries.
- A custom `Interceptor` implementation emits byte-progress events during response body reads; `StreamDownloader` owns a version of this that wraps `ResponseBody.source()` with a counting sink.
- A custom `Interceptor` implements the InnerTube retry policy (AC-12.4) — 3 retries, exponential backoff from 500 ms, retryable-error whitelist from Section 4.1 of `02-architecture.md`.
- Unit tests use `okhttp3.mockwebserver.MockWebServer` to script fake YouTube responses. No real network ever touches `mvn test`, enforcing AC-11.3.
- The CLI module's `--debug` flag installs OkHttp's `HttpLoggingInterceptor` at body level so users can see exactly what went over the wire.

The OkHttp dependency adds ~800 KB to the fat jar (OkHttp ~600 KB + Okio ~400 KB minus some shared overhead).

## Alternatives considered

### Alternative 1 — JDK `java.net.http.HttpClient`

Pros:
- **Zero additional dependencies.** Already in the JDK since Java 11.
- Supports HTTP/2 and `CompletableFuture`-based async natively.
- First-party — no third-party supply-chain risk.

Cons:
- **Progress reporting requires writing a custom `BodySubscriber`.** The JDK exposes `BodySubscribers` like `ofInputStream`, `ofString`, `ofByteArray`, etc., none of which expose a callback hook during body read. Wiring in a "byte counter" subscriber means implementing `HttpResponse.BodySubscriber<T>` with all of `onSubscribe`, `onNext`, `onError`, `onComplete` — reactive-streams boilerplate. Doable; not cheap; easy to get subtly wrong.
- **No built-in retry abstraction.** The JDK client has no retry interceptor concept. Every `send(request)` call that fails has to be wrapped in application-level retry logic, repeated at every call site or behind a hand-rolled helper.
- **Logging is verbose and manual.** There's no one-line `HttpLoggingInterceptor` equivalent. Debugging what went over the wire means adding `System.out.println` calls around every `send()`.
- **Testing is awkward.** There's no `MockWebServer`-equivalent that integrates cleanly with the JDK client. Options are: run a real lightweight server (e.g., `sun.net.httpserver.HttpServer` — not API) or mock the `HttpClient` itself (brittle, requires injection plumbing).
- **Cookie jar is basic.** Not a concern for MVP (we don't touch cookies per OOS-6), but a future `--cookies` feature would need hand-rolling — on OkHttp it's a `CookieJar` implementation drop-in.

Rejected because the per-call-site boilerplate for progress reporting, retries, and logging would consume the entire "tool is small and simple" advantage we'd gain from a zero-dep HTTP client. The JDK client is right for tools that make a small number of simple requests; the tool we are building is dominated by one long-running body read per run (the stream download) where progress and retry matter most.

### Alternative 2 — Apache HttpClient 5

Pros:
- **Most configurable of the three.** Connection pool tuning, request / response interceptors, socket-level timeout control are all first-class.
- Well-documented and stable on Java 17.
- `HttpRequestInterceptor` / `HttpResponseInterceptor` chain analogous to OkHttp's.
- Built-in retry strategies (`DefaultHttpRequestRetryStrategy`).

Cons:
- **Heaviest fat-jar cost.** Apache HttpClient 5 + HttpCore 5 + commons-logging shim totals ~1.5–2 MB, roughly 2× OkHttp's footprint.
- **Verbose API.** Building a request is more ceremony — `ClassicHttpRequest`, `HttpClientContext`, `HttpClientResponseHandler`. Every call site grows by 3–5 lines vs OkHttp.
- **No `MockWebServer` equivalent** in the first-party ecosystem. Options are `WireMock` (another 2 MB test dependency with its own Jetty) or custom mocks against `HttpClientConnectionManager` (brittle).
- **Progress-on-body-read is possible** via a custom `HttpEntity` wrapper, but the API is more involved than OkHttp's `Interceptor` chaining the `ResponseBody`.

Rejected primarily on fat-jar weight and test-ergonomics grounds. Apache HttpClient 5 is the right choice when you need fine-grained connection-pool tuning or when you're embedding in a product that already has it in the classpath; neither applies here.

### Alternative 3 — Do nothing (no HTTP library; use `URL.openConnection()`)

Pros:
- Fully JDK-bundled, smallest-possible fat jar.

Cons:
- **`URLConnection` is an early-2000s API** with no async, no connection pooling, no interceptor chain, no redirect customization, a `HttpURLConnection` that lies about response codes on 4xx (requires reading `getErrorStream()` conditionally). Hand-rolling retry / progress / timeout controls on top of it is worse than the JDK `HttpClient` alternative.

Rejected without further discussion. This option exists in the list only to make "no HTTP library" an explicit non-choice.

## Consequences

**Positive:**

- **Progress reporting is a small interceptor.** `StreamDownloader` wraps the `ResponseBody` once; all four boundaries benefit from the same progress plumbing without per-component code duplication.
- **`MockWebServer` enables AC-11.1..AC-11.4 directly.** Every wire-facing test writes a fixture into the mock server, runs the component under test against its URL, asserts the component's behaviour. No network, fully deterministic, runs in `mvn test` under `NFR-UNIT-TEST-RUNTIME-BUDGET = 30 s`.
- **`HttpLoggingInterceptor` gives `--debug` output for free.** One line in the CLI module adds full request + response logging at body level for the user — no per-component `logger.debug(...)` calls needed.
- **Retry interceptor is single-file.** InnerTube's retry policy from `02-architecture.md` § 4.1 is implemented once in one `Interceptor` and applied to the InnerTube `Call` only. Stream retries (Section 4.2) are handled at a higher level (`StreamDownloader` re-issues the call with byte-0 restart on failure) because they require orchestrator cooperation.
- **Small and stable.** OkHttp 4.x is the last pre-Kotlin-native version; pure Java consumers get a stable API with no Kotlin stdlib transitive dependency in the Java classpath beyond kotlin-stdlib-common (required for annotations, ~500 KB).
- **First-class `Call.timeout()`.** Per-call total-time budget (e.g., 10 s for captions) is a one-method call on the specific `Call`, without instantiating a new `OkHttpClient` per boundary.

**Negative / accepted trade-offs:**

- **~800 KB added to the fat jar** (OkHttp 4.x + Okio). Not zero, but the offline-testing and progress-reporting wins are worth it for an MVP.
- **OkHttp 4.x pulls in `kotlin-stdlib`** (~500 KB) even for pure-Java consumers. OkHttp 5.x (released early 2025) removes this, but we're sticking to 4.x for maximum-stability reasons; 5.x adoption is planned as a follow-up once it has a few more months of production signal. Not tracked as an open question because it's a minor packaging optimization, not a capability gap.
- **Custom retry policies are per-`Call`, not per-application.** If the tool grows to have more boundaries with different retry policies, we'll have more `Interceptor` implementations. Fine for four boundaries; monitor if we grow to a dozen.
- **No HTTP/3 support** in OkHttp 4.x. Not relevant for MVP (YouTube CDN speaks HTTP/2 today and fallback to 1.1 as needed); flagged in case a future protocol-observability concern arises.

**Neutral:**

- OkHttp's default connection pool (5 idle connections, 5 min keep-alive) is fine for a single-run CLI tool. No tuning needed.
- Library embedders (US-9) get a well-known, well-documented HTTP client. If they've ever used `Retrofit` or any Square lib, OkHttp's idioms are familiar.
- OkHttp's `Interceptor.chain().request()` / `Interceptor.chain().proceed()` idiom is easy to understand for any Java dev reading `02-architecture.md` § 1.2 components.

## References

- `00-requirements.md` § User stories — US-4 (see progress), US-5 (fail fast), US-11 (offline tests), US-12 (plausible InnerTube client)
- `00-requirements.md` § Acceptance criteria — AC-4.1 (progress with bytes, rate, ETA), AC-11.3 (network fails unit tests), AC-12.4 (InnerTube retry with exponential backoff)
- `00-requirements.md` § Non-functional requirements — NFR-NETWORK-TIMEOUT-CONNECT, NFR-NETWORK-TIMEOUT-READ, NFR-INNERTUBE-REQUEST-TIMEOUT, NFR-INNERTUBE-MAX-RETRIES, NFR-INNERTUBE-BACKOFF-BASE, NFR-STREAM-MAX-RETRIES, NFR-CAPTION-DOWNLOAD-TIMEOUT, NFR-THUMBNAIL-DOWNLOAD-TIMEOUT
- `02-architecture.md` § 1.2 — all four HTTP-making components reference this ADR
- `02-architecture.md` § 4 — retry model; InnerTube interceptor; stream restart-on-retry
- [`square/okhttp`](https://github.com/square/okhttp)
- [OkHttp `MockWebServer` docs](https://square.github.io/okhttp/4.x/mockwebserver/okhttp3.mockwebserver/-mock-web-server/)
- [OkHttp `HttpLoggingInterceptor` docs](https://square.github.io/okhttp/4.x/logging-interceptor/okhttp3.logging/-http-logging-interceptor/)
- ADR 0001 (ANDROID InnerTube client) — companion decision for the boundary OkHttp will talk to
