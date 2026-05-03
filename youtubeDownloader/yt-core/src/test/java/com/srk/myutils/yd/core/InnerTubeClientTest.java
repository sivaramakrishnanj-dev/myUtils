package com.srk.myutils.yd.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link InnerTubeClient} — happy-path only.
 *
 * <p>Injects an OkHttpClient with a short-circuit interceptor that returns a
 * canned response without opening any socket (NoNetworkExtension is active).
 * Verifies the request body matches the expected ANDROID context shape and
 * that the response is returned correctly.
 */
class InnerTubeClientTest {

    private static final String CANNED_RESPONSE = "{\"playabilityStatus\":{\"status\":\"OK\"}}";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fetchPlayer_givenValidVideoId_returnsResponseAndSendsCorrectBody() throws Exception {
        // Capture the request for assertion; return a canned response
        final okhttp3.Request[] captured = new okhttp3.Request[1];
        final String[] capturedBody = new String[1];

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    okhttp3.Request req = chain.request();
                    captured[0] = req;
                    if (req.body() != null) {
                        okio.Buffer buf = new okio.Buffer();
                        req.body().writeTo(buf);
                        capturedBody[0] = buf.readUtf8();
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

        InnerTubeClient innerTubeClient = new InnerTubeClient(client);
        InnerTubeResponse response = innerTubeClient.fetchPlayer(VideoId.of("dQw4w9WgXcQ"));

        // Assert response
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(CANNED_RESPONSE);

        // Assert request URL
        assertThat(captured[0].url().toString())
                .isEqualTo("https://www.youtube.com/youtubei/v1/player");

        // Assert request headers (AC-12.2, 04-apis.md § 1.1.1)
        assertThat(captured[0].header("User-Agent"))
                .isEqualTo("com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip");
        assertThat(captured[0].header("X-YouTube-Client-Name")).isEqualTo("3");
        assertThat(captured[0].header("X-YouTube-Client-Version")).isEqualTo("19.09.37");

        // Assert request body matches schema (AC-12.1)
        JsonNode body = MAPPER.readTree(capturedBody[0]);
        assertThat(body.get("videoId").asText()).isEqualTo("dQw4w9WgXcQ");

        JsonNode clientNode = body.get("context").get("client");
        assertThat(clientNode.get("clientName").asText()).isEqualTo("ANDROID");
        assertThat(clientNode.get("clientVersion").asText()).isEqualTo("19.09.37");
        assertThat(clientNode.get("androidSdkVersion").asInt()).isEqualTo(34);
        assertThat(clientNode.get("hl").asText()).isEqualTo("en");
        assertThat(clientNode.get("gl").asText()).isEqualTo("US");
        assertThat(clientNode.get("osName").asText()).isEqualTo("Android");
        assertThat(clientNode.get("osVersion").asText()).isEqualTo("14");
        assertThat(clientNode.get("platform").asText()).isEqualTo("MOBILE");
    }
}
