package com.srk.myutils.yd.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the InnerTube request shape produced by {@link InnerTubeClient}:
 * body fields (AC-12.1), headers (AC-12.2), URL, and the at-most-one-request
 * invariant (AC-12.3, INV-9).
 *
 * <p>Uses an OkHttp interceptor to capture the outgoing request without
 * opening any socket (NoNetworkExtension is active globally).
 */
class InnerTubeClientRequestShapeTest {

    private static final String CANNED_RESPONSE = "{\"playabilityStatus\":{\"status\":\"OK\"}}";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<okhttp3.Request> capturedRequests = new ArrayList<>();
    private String capturedBody;
    private InnerTubeClient client;

    @BeforeEach
    void setUp() {
        capturedRequests.clear();
        capturedBody = null;

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    okhttp3.Request req = chain.request();
                    capturedRequests.add(req);
                    if (req.body() != null) {
                        okio.Buffer buf = new okio.Buffer();
                        req.body().writeTo(buf);
                        capturedBody = buf.readUtf8();
                    }
                    return new Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(CANNED_RESPONSE,
                                    okhttp3.MediaType.get("application/json")))
                            .build();
                })
                .build();

        client = new InnerTubeClient(httpClient);
    }

    // ── Request body shape (AC-12.1) ──────────────────────────────────

    @Nested
    @DisplayName("Request body shape — AC-12.1")
    class RequestBodyShape {

        @Test
        @DisplayName("videoId is present and matches the input (CT-REQ-1)")
        void fetchPlayer_givenVideoId_bodyContainsVideoId() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode body = MAPPER.readTree(capturedBody);
            assertThat(body.get("videoId").asText()).isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        @DisplayName("context.client.clientName == ANDROID (AC-12.1, ADR-0001)")
        void fetchPlayer_bodyHasClientNameAndroid() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode clientNode = MAPPER.readTree(capturedBody).path("context").path("client");
            assertThat(clientNode.get("clientName").asText()).isEqualTo("ANDROID");
        }

        @Test
        @DisplayName("context.client.clientVersion == 21.02.35 (NFR-ANDROID-CLIENT-VERSION)")
        void fetchPlayer_bodyHasClientVersion() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode clientNode = MAPPER.readTree(capturedBody).path("context").path("client");
            assertThat(clientNode.get("clientVersion").asText()).isEqualTo("21.02.35");
        }

        @Test
        @DisplayName("androidSdkVersion == 30 as integer, not string (NFR-ANDROID-SDK-VERSION, CT-REQ-N7)")
        void fetchPlayer_bodyHasAndroidSdkVersionAsInteger() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode sdkNode = MAPPER.readTree(capturedBody)
                    .path("context").path("client").path("androidSdkVersion");
            assertThat(sdkNode.isInt()).as("androidSdkVersion must be an integer, not a string").isTrue();
            assertThat(sdkNode.asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("hl == en (NFR-INNERTUBE-HL)")
        void fetchPlayer_bodyHasHl() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode clientNode = MAPPER.readTree(capturedBody).path("context").path("client");
            assertThat(clientNode.get("hl").asText()).isEqualTo("en");
        }

        @Test
        @DisplayName("gl == US (NFR-INNERTUBE-GL)")
        void fetchPlayer_bodyHasGl() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode clientNode = MAPPER.readTree(capturedBody).path("context").path("client");
            assertThat(clientNode.get("gl").asText()).isEqualTo("US");
        }

        @Test
        @DisplayName("osName == Android")
        void fetchPlayer_bodyHasOsName() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode clientNode = MAPPER.readTree(capturedBody).path("context").path("client");
            assertThat(clientNode.get("osName").asText()).isEqualTo("Android");
        }

        @Test
        @DisplayName("osVersion == 11")
        void fetchPlayer_bodyHasOsVersion() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode clientNode = MAPPER.readTree(capturedBody).path("context").path("client");
            assertThat(clientNode.get("osVersion").asText()).isEqualTo("11");
        }

        @Test
        @DisplayName("platform == MOBILE")
        void fetchPlayer_bodyHasPlatform() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode clientNode = MAPPER.readTree(capturedBody).path("context").path("client");
            assertThat(clientNode.get("platform").asText()).isEqualTo("MOBILE");
        }

        @Test
        @DisplayName("body has exactly two top-level keys: videoId and context (no additionalProperties)")
        void fetchPlayer_bodyHasNoExtraTopLevelKeys() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode body = MAPPER.readTree(capturedBody);
            assertThat(body.size()).as("top-level key count").isEqualTo(2);
            assertThat(body.has("videoId")).isTrue();
            assertThat(body.has("context")).isTrue();
        }

        @Test
        @DisplayName("context.client has exactly 8 required fields (no additionalProperties)")
        void fetchPlayer_clientNodeHasExactlyEightFields() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode clientNode = MAPPER.readTree(capturedBody).path("context").path("client");
            assertThat(clientNode.size()).as("client field count per schema").isEqualTo(8);
        }

        @Test
        @DisplayName("body matches fixture innertube-request-happy.json shape (ignoring x-captured-on metadata)")
        void fetchPlayer_bodyMatchesFixtureShape() throws Exception {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            JsonNode actual = MAPPER.readTree(capturedBody);
            JsonNode expected = MAPPER.readTree(getClass().getResourceAsStream(
                    "/fixtures/innertube-request-happy.json"));
            // x-captured-on is fixture provenance metadata (T-5.5), not part of the request shape
            assertThat(actual.get("videoId")).isEqualTo(expected.get("videoId"));
            assertThat(actual.get("context")).isEqualTo(expected.get("context"));
            assertThat(actual.size()).as("top-level key count matches request shape").isEqualTo(2);
        }

        @Test
        @DisplayName("different videoId is reflected in body")
        void fetchPlayer_givenDifferentVideoId_bodyReflectsIt() throws Exception {
            client.fetchPlayer(VideoId.of("abc12345678"));

            JsonNode body = MAPPER.readTree(capturedBody);
            assertThat(body.get("videoId").asText()).isEqualTo("abc12345678");
        }
    }

    // ── Headers (AC-12.2) ─────────────────────────────────────────────

    @Nested
    @DisplayName("Request headers — AC-12.2")
    class RequestHeaders {

        @Test
        @DisplayName("User-Agent matches NFR-ANDROID-USER-AGENT")
        void fetchPlayer_userAgentHeader() {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(capturedRequests.get(0).header("User-Agent"))
                    .isEqualTo("com.google.android.youtube/21.02.35 (Linux; U; Android 11) gzip");
        }

        @Test
        @DisplayName("Content-Type is application/json")
        void fetchPlayer_contentTypeHeader() {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(capturedRequests.get(0).body().contentType().toString())
                    .startsWith("application/json");
        }

        @Test
        @DisplayName("X-YouTube-Client-Name == 3")
        void fetchPlayer_clientNameHeader() {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(capturedRequests.get(0).header("X-YouTube-Client-Name")).isEqualTo("3");
        }

        @Test
        @DisplayName("X-YouTube-Client-Version == 21.02.35")
        void fetchPlayer_clientVersionHeader() {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(capturedRequests.get(0).header("X-YouTube-Client-Version")).isEqualTo("21.02.35");
        }

        @Test
        @DisplayName("Accept-Language is present (04-apis.md § 1.1.1)")
        void fetchPlayer_acceptLanguageHeader() {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(capturedRequests.get(0).header("Accept-Language"))
                    .isEqualTo("en-US,en;q=0.9");
        }
    }

    // ── URL (AC-1.2, 04-apis.md § 1.1) ───────────────────────────────

    @Nested
    @DisplayName("Request URL — AC-1.2")
    class RequestUrl {

        @Test
        @DisplayName("POST to https://www.youtube.com/youtubei/v1/player")
        void fetchPlayer_postsToCorrectUrl() {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            okhttp3.Request req = capturedRequests.get(0);
            assertThat(req.url().toString())
                    .isEqualTo("https://www.youtube.com/youtubei/v1/player");
            assertThat(req.method()).isEqualTo("POST");
        }
    }

    // ── At-most-one-request invariant (AC-12.3, INV-9) ───────────────

    @Nested
    @DisplayName("Single-request invariant — AC-12.3, INV-9")
    class SingleRequestInvariant {

        @Test
        @DisplayName("fetchPlayer triggers exactly one HTTP request")
        void fetchPlayer_triggersExactlyOneRequest() {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(capturedRequests).hasSize(1);
        }

        @Test
        @DisplayName("two sequential fetchPlayer calls produce two separate requests (no caching)")
        void fetchPlayer_calledTwice_producesTwoRequests() {
            client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));
            client.fetchPlayer(VideoId.of("abc12345678"));

            assertThat(capturedRequests).hasSize(2);
        }
    }
}
