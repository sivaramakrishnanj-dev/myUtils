package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for {@link YoutubeDownloader} orchestration (T-1.14).
 *
 * <p>SUT is not mocked. {@link UrlParser} is real (pure function).
 * {@link InnerTubeClient} is constructed with a short-circuit OkHttp interceptor
 * that returns canned responses — no network I/O (NoNetworkExtension active).
 *
 * <p>Covers: happy path (CT-APP-1), playability failures (CT-APP-6, CT-APP-7),
 * HTTP non-200 (CT-EXIT-UNIT-2), parse failures (CT-EXIT-UNIT-11 equivalent),
 * invalid URL (exit 2), and orchestration flow order.
 */
class YoutubeDownloaderBehaviorTest {

    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final MediaType JSON_TYPE = MediaType.get("application/json");

    @TempDir
    Path tempDir;

    // ── helpers ──────────────────────────────────────────────────────

    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_BYTES = {0x00, 0x01, 0x02, 0x03};

    /**
     * Creates a metadata-only request (audio-only with output dir) for tests
     * that verify URL parsing, InnerTube fetch, and playability checks without
     * triggering the full download/mux flow.
     */
    private static DownloadRequest metadataOnlyRequest(String url, Path outputDir) {
        return new DownloadRequest(url, true, AudioFormat.M4A, 0, Optional.empty(),
                false, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.of(outputDir), false),
                ProgressListener.NO_OP, false, false);
    }

    /**
     * Overload for error-path tests where the request never reaches the download step.
     */
    private static DownloadRequest metadataOnlyRequest(String url) {
        return new DownloadRequest(url, true, AudioFormat.M4A, 0, Optional.empty(),
                false, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                ProgressListener.NO_OP, false, false);
    }

    private static YoutubeDownloader downloaderReturning(int httpStatus, String body) {
        OkHttpClient fakeHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(httpStatus)
                        .message("OK")
                        .body(ResponseBody.create(body, JSON_TYPE))
                        .build())
                .build();
        return new YoutubeDownloader(new UrlParser(), new InnerTubeClient(fakeHttp));
    }

    private static YoutubeDownloader downloaderWithFixture(String fixturePath) {
        OkHttpClient fakeInnerTubeHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(loadFixture(fixturePath), JSON_TYPE))
                        .build())
                .build();
        OkHttpClient fakeStreamHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Length", String.valueOf(FAKE_BYTES.length))
                        .body(ResponseBody.create(FAKE_BYTES, OCTET))
                        .build())
                .build();
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(fakeStreamHttp));
    }

    private static String loadFixture(String resourcePath) {
        try (InputStream is = YoutubeDownloaderBehaviorTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }

    // ── 1. Happy path ───────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path — CT-APP-1")
    class HappyPath {

        @Test
        @DisplayName("happy.json → DownloadResult with videoId and title, all paths empty (M1 stub)")
        void download_givenHappyFixture_returnsMetadataWithNoPaths() {
            YoutubeDownloader sut = downloaderWithFixture("/fixtures/innertube-response-happy.json");

            DownloadResult result = sut.download(metadataOnlyRequest(VALID_URL, tempDir));

            assertThat(result.videoId().value()).isEqualTo("dQw4w9WgXcQ");
            assertThat(result.title()).isEqualTo(
                    "Rick Astley - Never Gonna Give You Up (Official Music Video)");
            assertThat(result.videoPath()).isEmpty();
            assertThat(result.audioPath()).isPresent();
            assertThat(result.srtPath()).isEmpty();
            assertThat(result.txtPath()).isEmpty();
            assertThat(result.thumbnailPath()).isEmpty();
            assertThat(result.usedAsrFallback()).isFalse();
        }

        @ParameterizedTest(name = "URL shape: {0}")
        @ValueSource(strings = {
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://youtu.be/dQw4w9WgXcQ",
                "https://www.youtube.com/shorts/dQw4w9WgXcQ",
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        })
        @DisplayName("All four AC-1.1 URL shapes resolve to the same videoId")
        void download_givenAllUrlShapes_parsesVideoId(String url) {
            YoutubeDownloader sut = downloaderWithFixture("/fixtures/innertube-response-happy.json");

            DownloadResult result = sut.download(metadataOnlyRequest(url, tempDir));

            assertThat(result.videoId().value()).isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        @DisplayName("URL with extra query params still parses correctly")
        void download_givenUrlWithExtraParams_parsesVideoId() {
            YoutubeDownloader sut = downloaderWithFixture("/fixtures/innertube-response-happy.json");

            DownloadResult result = sut.download(metadataOnlyRequest(
                    "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", tempDir));

            assertThat(result.videoId().value()).isEqualTo("dQw4w9WgXcQ");
        }
    }

    // ── 2. Playability failures ─────────────────────────────────────

    @Nested
    @DisplayName("Playability failures")
    class PlayabilityFailures {

        @Test
        @DisplayName("unplayable.json → VideoUnavailableException (exit 20) — CT-APP-7")
        void download_givenUnplayableVideo_throwsVideoUnavailableException() {
            YoutubeDownloader sut = downloaderWithFixture("/fixtures/innertube-response-unplayable.json");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest("https://www.youtube.com/watch?v=privprivpr1")))
                    .isInstanceOf(VideoUnavailableException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(20));
        }

        @Test
        @DisplayName("live.json → LiveStreamException (exit 21) — CT-APP-6")
        void download_givenLiveStream_throwsLiveStreamException() {
            YoutubeDownloader sut = downloaderWithFixture("/fixtures/innertube-response-live.json");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest("https://www.youtube.com/watch?v=livelivelv1")))
                    .isInstanceOf(LiveStreamException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(21));
        }

        @Test
        @DisplayName("Invalid URL → UrlParseException (exit 2) before any InnerTube call")
        void download_givenInvalidUrl_throwsUrlParseException() {
            // Interceptor that would fail if called — proves no network call is made
            YoutubeDownloader sut = downloaderReturning(200, "should-not-be-reached");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest("not-a-url")))
                    .isInstanceOf(UrlParseException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(2));
        }

        @Test
        @DisplayName("Null URL → UrlParseException (exit 2)")
        void download_givenNullUrl_throwsUrlParseException() {
            YoutubeDownloader sut = downloaderReturning(200, "should-not-be-reached");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest(null)))
                    .isInstanceOf(UrlParseException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(2));
        }
    }

    // ── 3. HTTP non-200 ─────────────────────────────────────────────

    @Nested
    @DisplayName("HTTP non-200 — CT-EXIT-UNIT-2 (NetworkException exit 10)")
    class HttpNon200 {

        @Test
        @DisplayName("HTTP 500 → NetworkException (exit 10)")
        void download_givenHttp500_throwsNetworkException() {
            YoutubeDownloader sut = downloaderReturning(500, "{}");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest(VALID_URL)))
                    .isInstanceOf(NetworkException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(10))
                    .hasMessageContaining("500");
        }

        @Test
        @DisplayName("HTTP 404 → NetworkException (exit 10)")
        void download_givenHttp404_throwsNetworkException() {
            YoutubeDownloader sut = downloaderReturning(404, "{}");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest(VALID_URL)))
                    .isInstanceOf(NetworkException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(10))
                    .hasMessageContaining("404");
        }

        @Test
        @DisplayName("HTTP 403 → NetworkException (exit 10)")
        void download_givenHttp403_throwsNetworkException() {
            YoutubeDownloader sut = downloaderReturning(403, "{}");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest(VALID_URL)))
                    .isInstanceOf(NetworkException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(10));
        }
    }

    // ── 4. Parse failures ───────────────────────────────────────────

    @Nested
    @DisplayName("Parse failures — CT-EXIT-UNIT-11 equivalent (InnerTubeParseException exit 11)")
    class ParseFailures {

        @Test
        @DisplayName("Broken JSON → InnerTubeParseException (exit 11)")
        void download_givenBrokenJson_throwsInnerTubeParseException() {
            YoutubeDownloader sut = downloaderReturning(200, "{not valid json!!!");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest(VALID_URL)))
                    .isInstanceOf(InnerTubeParseException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(11));
        }

        @Test
        @DisplayName("Missing videoDetails → InnerTubeParseException (exit 11)")
        void download_givenMissingVideoDetails_throwsInnerTubeParseException() {
            String json = "{\"playabilityStatus\":{\"status\":\"OK\"}}";
            YoutubeDownloader sut = downloaderReturning(200, json);

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest(VALID_URL)))
                    .isInstanceOf(InnerTubeParseException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(11))
                    .hasMessageContaining("videoDetails");
        }

        @Test
        @DisplayName("Missing playabilityStatus → InnerTubeParseException (exit 11)")
        void download_givenMissingPlayabilityStatus_throwsInnerTubeParseException() {
            String json = "{\"videoDetails\":{\"videoId\":\"dQw4w9WgXcQ\",\"title\":\"T\","
                    + "\"isLive\":false,\"isPrivate\":false,"
                    + "\"thumbnail\":{\"thumbnails\":[{\"url\":\"u\",\"width\":1,\"height\":1}]}}}";
            YoutubeDownloader sut = downloaderReturning(200, json);

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest(VALID_URL)))
                    .isInstanceOf(InnerTubeParseException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(11))
                    .hasMessageContaining("playabilityStatus");
        }

        @Test
        @DisplayName("Empty body → InnerTubeParseException (exit 11)")
        void download_givenEmptyBody_throwsInnerTubeParseException() {
            YoutubeDownloader sut = downloaderReturning(200, "");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest(VALID_URL)))
                    .isInstanceOf(InnerTubeParseException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(11));
        }
    }

    // ── 5. Flow order verification ──────────────────────────────────

    @Nested
    @DisplayName("Flow order verification")
    class FlowOrder {

        @Test
        @DisplayName("UrlParser called before InnerTubeClient — invalid URL never reaches InnerTube")
        void download_givenInvalidUrl_neverCallsInnerTube() {
            // If InnerTube were called, the interceptor would return valid JSON
            // and no exception would be thrown. UrlParseException proves URL
            // parsing happens first.
            YoutubeDownloader sut = downloaderWithFixture("/fixtures/innertube-response-happy.json");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest("garbage")))
                    .isInstanceOf(UrlParseException.class);
        }

        @Test
        @DisplayName("InnerTubeClient called with parsed VideoId — interceptor receives correct videoId in body")
        void download_givenValidUrl_innerTubeReceivesVideoId() {
            final String[] capturedBody = new String[1];

            OkHttpClient fakeHttp = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        okio.Buffer buf = new okio.Buffer();
                        chain.request().body().writeTo(buf);
                        capturedBody[0] = buf.readUtf8();
                        return new Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(ResponseBody.create(
                                        loadFixture("/fixtures/innertube-response-happy.json"),
                                        JSON_TYPE))
                                .build();
                    })
                    .build();

            OkHttpClient fakeStreamHttp = new OkHttpClient.Builder()
                    .addInterceptor(chain -> new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Length", String.valueOf(FAKE_BYTES.length))
                            .body(ResponseBody.create(FAKE_BYTES, OCTET))
                            .build())
                    .build();

            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeHttp),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp));

            sut.download(metadataOnlyRequest(VALID_URL, tempDir));

            assertThat(capturedBody[0]).contains("\"videoId\":\"dQw4w9WgXcQ\"");
        }

        @Test
        @DisplayName("checkPlayability called after extract — unplayable fixture triggers exception")
        void download_givenUnplayableFixture_checkPlayabilityRunsAfterExtract() {
            // If extract didn't run, checkPlayability wouldn't have a PlayerResponse.
            // If checkPlayability didn't run, no exception would be thrown.
            YoutubeDownloader sut = downloaderWithFixture("/fixtures/innertube-response-unplayable.json");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest("https://www.youtube.com/watch?v=privprivpr1")))
                    .isInstanceOf(VideoUnavailableException.class);
        }

        @Test
        @DisplayName("Result assembled from PlayerResponse — title matches fixture")
        void download_givenHappyFixture_resultAssembledFromResponse() {
            YoutubeDownloader sut = downloaderWithFixture("/fixtures/innertube-response-happy.json");

            DownloadResult result = sut.download(metadataOnlyRequest(VALID_URL, tempDir));

            // Title comes from PlayerResponse.videoDetails.title, proving
            // extract → result assembly chain works
            assertThat(result.title()).startsWith("Rick Astley");
            assertThat(result.videoId().value()).isEqualTo("dQw4w9WgXcQ");
        }
    }

    // ── 6. AC-9.2: library never calls System.exit ──────────────────

    @Nested
    @DisplayName("AC-9.2 — library never calls System.exit")
    class LibraryContract {

        @Test
        @DisplayName("Failure paths throw exceptions, never call System.exit")
        void download_givenFailure_throwsExceptionNotSystemExit() {
            // If System.exit were called, the JVM would terminate and this
            // assertion would never complete. The fact that we catch the
            // exception proves AC-9.2.
            YoutubeDownloader sut = downloaderReturning(500, "{}");

            assertThatThrownBy(() -> sut.download(metadataOnlyRequest(VALID_URL)))
                    .isInstanceOf(YoutubeDownloaderException.class);
        }
    }
}
