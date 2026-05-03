package com.srk.myutils.yd.core;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link InnerTubeRetryInterceptor} — happy path
 * only. Verifies the interceptor returns a successful response on the first
 * attempt without retrying.
 */
class InnerTubeRetryInterceptorTest {

    @Test
    void intercept_givenSuccessOnFirstAttempt_returnsResponseWithoutRetry() throws Exception {
        // No-op sleeper — should never be called on a first-attempt success
        InnerTubeRetryInterceptor.Sleeper noOpSleeper = millis -> {
            throw new AssertionError("Sleeper should not be called on first-attempt success");
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new InnerTubeRetryInterceptor(noOpSleeper))
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create("{\"status\":\"OK\"}",
                                okhttp3.MediaType.get("application/json")))
                        .build())
                .build();

        Request request = new Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).isNotNull();
            assertThat(response.body().string()).isEqualTo("{\"status\":\"OK\"}");
        }
    }
}
