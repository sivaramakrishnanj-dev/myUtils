package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link InnerTubeClient} error handling: HTTP error codes, IO failures,
 * empty bodies, and response wiring.
 *
 * <p>Uses OkHttp interceptors to simulate server responses and failures
 * without opening any socket (NoNetworkExtension is active globally).
 */
class InnerTubeClientErrorHandlingTest {

    private static final MediaType JSON = MediaType.get("application/json");

    private InnerTubeClient clientReturning(int statusCode, String body) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(statusCode)
                        .message("Status " + statusCode)
                        .body(ResponseBody.create(body, JSON))
                        .build())
                .build();
        return new InnerTubeClient(httpClient);
    }

    private InnerTubeClient clientThrowing(IOException exception) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> { throw exception; })
                .build();
        return new InnerTubeClient(httpClient);
    }

    // ── HTTP status handling ──────────────────────────────────────────

    @Nested
    @DisplayName("HTTP status handling")
    class HttpStatusHandling {

        @Test
        @DisplayName("HTTP 200 returns InnerTubeResponse with status and body")
        void fetchPlayer_givenHttp200_returnsResponse() {
            String body = "{\"playabilityStatus\":{\"status\":\"OK\"}}";
            InnerTubeClient client = clientReturning(200, body);

            InnerTubeResponse response = client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(body);
        }

        @ParameterizedTest(name = "HTTP {0} returns InnerTubeResponse (not exception)")
        @ValueSource(ints = {500, 502, 503})
        @DisplayName("HTTP 5xx returns response — retry is T-1.6's job")
        void fetchPlayer_givenHttp5xx_returnsResponse(int statusCode) {
            InnerTubeClient client = clientReturning(statusCode, "server error");

            InnerTubeResponse response = client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(response.httpStatus()).isEqualTo(statusCode);
            assertThat(response.body()).isEqualTo("server error");
        }

        @Test
        @DisplayName("HTTP 404 returns InnerTubeResponse")
        void fetchPlayer_givenHttp404_returnsResponse() {
            InnerTubeClient client = clientReturning(404, "not found");

            InnerTubeResponse response = client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(response.httpStatus()).isEqualTo(404);
            assertThat(response.body()).isEqualTo("not found");
        }

        @Test
        @DisplayName("HTTP 403 returns InnerTubeResponse")
        void fetchPlayer_givenHttp403_returnsResponse() {
            InnerTubeClient client = clientReturning(403, "forbidden");

            InnerTubeResponse response = client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(response.httpStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("HTTP 429 returns InnerTubeResponse")
        void fetchPlayer_givenHttp429_returnsResponse() {
            InnerTubeClient client = clientReturning(429, "rate limited");

            InnerTubeResponse response = client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(response.httpStatus()).isEqualTo(429);
        }
    }

    // ── Empty / null body ─────────────────────────────────────────────

    @Nested
    @DisplayName("Empty body handling")
    class EmptyBodyHandling {

        @Test
        @DisplayName("HTTP 200 with empty body returns empty string (parse is T-1.7)")
        void fetchPlayer_givenEmptyBody_returnsEmptyString() {
            InnerTubeClient client = clientReturning(200, "");

            InnerTubeResponse response = client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(response.body()).isEmpty();
        }

        @Test
        @DisplayName("HTTP 200 with whitespace-only body returns it verbatim")
        void fetchPlayer_givenWhitespaceBody_returnsVerbatim() {
            InnerTubeClient client = clientReturning(200, "  ");

            InnerTubeResponse response = client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("  ");
        }
    }

    // ── IOException handling ──────────────────────────────────────────

    @Nested
    @DisplayName("IOException handling")
    class IoExceptionHandling {

        @Test
        @DisplayName("IOException wraps into InnerTubeException with cause")
        void fetchPlayer_givenIoException_throwsInnerTubeException() {
            IOException cause = new IOException("connection reset");
            InnerTubeClient client = clientThrowing(cause);

            assertThatThrownBy(() -> client.fetchPlayer(VideoId.of("dQw4w9WgXcQ")))
                    .isInstanceOf(InnerTubeException.class)
                    .hasCause(cause)
                    .hasMessageContaining("dQw4w9WgXcQ")
                    .hasMessageContaining("/youtubei/v1/player");
        }

        @Test
        @DisplayName("SocketTimeoutException wraps into InnerTubeException")
        void fetchPlayer_givenSocketTimeout_throwsInnerTubeException() {
            java.net.SocketTimeoutException cause = new java.net.SocketTimeoutException("Read timed out");
            InnerTubeClient client = clientThrowing(cause);

            assertThatThrownBy(() -> client.fetchPlayer(VideoId.of("dQw4w9WgXcQ")))
                    .isInstanceOf(InnerTubeException.class)
                    .hasCause(cause);
        }

        @Test
        @DisplayName("UnknownHostException wraps into InnerTubeException")
        void fetchPlayer_givenUnknownHost_throwsInnerTubeException() {
            java.net.UnknownHostException cause = new java.net.UnknownHostException("www.youtube.com");
            InnerTubeClient client = clientThrowing(cause);

            assertThatThrownBy(() -> client.fetchPlayer(VideoId.of("dQw4w9WgXcQ")))
                    .isInstanceOf(InnerTubeException.class)
                    .hasCause(cause);
        }
    }

    // ── Response wiring (CT-APP-1 partial) ────────────────────────────

    @Nested
    @DisplayName("Response wiring")
    class ResponseWiring {

        @Test
        @DisplayName("response body is returned verbatim (no parsing at this layer)")
        void fetchPlayer_returnsBodyVerbatim() {
            String rawJson = "{\"videoDetails\":{\"videoId\":\"dQw4w9WgXcQ\",\"title\":\"Rick\"}}";
            InnerTubeClient client = clientReturning(200, rawJson);

            InnerTubeResponse response = client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(response.body()).isEqualTo(rawJson);
        }

        @Test
        @DisplayName("large response body is returned completely")
        void fetchPlayer_givenLargeBody_returnsComplete() {
            String largeBody = "x".repeat(200_000);
            InnerTubeClient client = clientReturning(200, largeBody);

            InnerTubeResponse response = client.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

            assertThat(response.body()).hasSize(200_000);
        }
    }
}
