package com.srk.myutils.yd.core;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for {@link CaptionDownloader} — AC-6.1,
 * NFR-CAPTION-DOWNLOAD-TIMEOUT, NFR-ANDROID-USER-AGENT.
 *
 * <p>Uses OkHttp short-circuit interceptors (no real sockets).
 */
class CaptionDownloaderBehaviorTest {

    private static final MediaType XML = MediaType.get("application/xml");
    private static final String TIMEDTEXT_URL =
            "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&lang=en";

    private CaptionDownloader downloaderReturning(int code, String body) {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("Status " + code)
                        .body(ResponseBody.create(body, XML))
                        .build())
                .build();
        return new CaptionDownloader(client);
    }

    private CaptionDownloader downloaderThrowing(IOException ex) {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> { throw ex; })
                .build();
        return new CaptionDownloader(client);
    }

    // ── Happy path ───────────────────────────────────────────────────

    @Test
    @DisplayName("AC-6.1: download returns XML body on HTTP 200")
    void download_givenHttp200WithXml_returnsXmlString() {
        String xml = "<transcript><text start=\"0\" dur=\"5\">Hi</text></transcript>";
        CaptionDownloader sut = downloaderReturning(200, xml);

        String result = sut.download(TIMEDTEXT_URL);

        assertThat(result).isEqualTo(xml);
    }

    // ── Empty body ───────────────────────────────────────────────────

    @Test
    @DisplayName("AC-6.1: download returns empty string on HTTP 200 with empty body")
    void download_givenHttp200EmptyBody_returnsEmptyString() {
        CaptionDownloader sut = downloaderReturning(200, "");

        String result = sut.download(TIMEDTEXT_URL);

        assertThat(result).isEmpty();
    }

    // ── HTTP error codes → NetworkException ──────────────────────────

    @Test
    @DisplayName("AC-6.1: HTTP 404 throws NetworkException")
    void download_givenHttp404_throwsNetworkException() {
        CaptionDownloader sut = downloaderReturning(404, "Not Found");

        assertThatThrownBy(() -> sut.download(TIMEDTEXT_URL))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    @DisplayName("AC-6.1: HTTP 500 throws NetworkException")
    void download_givenHttp500_throwsNetworkException() {
        CaptionDownloader sut = downloaderReturning(500, "Server Error");

        assertThatThrownBy(() -> sut.download(TIMEDTEXT_URL))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("HTTP 500");
    }

    // ── IOException wrapping ─────────────────────────────────────────

    @Test
    @DisplayName("AC-6.1: IOException mid-request wraps into NetworkException")
    void download_givenIoException_throwsNetworkExceptionWrapping() {
        IOException cause = new IOException("connection reset");
        CaptionDownloader sut = downloaderThrowing(cause);

        assertThatThrownBy(() -> sut.download(TIMEDTEXT_URL))
                .isInstanceOf(NetworkException.class)
                .hasCause(cause);
    }

    // ── User-Agent header ────────────────────────────────────────────

    @Test
    @DisplayName("NFR-ANDROID-USER-AGENT: request carries correct User-Agent")
    void download_setsUserAgentToNfrAndroidUserAgent() {
        AtomicReference<String> capturedUa = new AtomicReference<>();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    capturedUa.set(chain.request().header("User-Agent"));
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create("", XML))
                            .build();
                })
                .build();
        CaptionDownloader sut = new CaptionDownloader(client);

        sut.download(TIMEDTEXT_URL);

        assertThat(capturedUa.get())
                .isEqualTo("com.google.android.youtube/21.02.35 (Linux; U; Android 11) gzip");
    }

    // ── URL pass-through ─────────────────────────────────────────────

    @Test
    @DisplayName("AC-6.1: timedtext URL is passed through unchanged to the request")
    void download_passesUrlUnchanged() {
        AtomicReference<String> capturedUrl = new AtomicReference<>();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    capturedUrl.set(chain.request().url().toString());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create("", XML))
                            .build();
                })
                .build();
        CaptionDownloader sut = new CaptionDownloader(client);

        sut.download(TIMEDTEXT_URL);

        assertThat(capturedUrl.get()).isEqualTo(TIMEDTEXT_URL);
    }

    // ── create() factory timeouts ────────────────────────────────────

    @Test
    @DisplayName("NFR-CAPTION-DOWNLOAD-TIMEOUT: create() configures 10s timeouts")
    void create_configures10SecondTimeouts() {
        CaptionDownloader sut = CaptionDownloader.create();

        // Access the internal client via reflection to verify timeouts
        try {
            var field = CaptionDownloader.class.getDeclaredField("httpClient");
            field.setAccessible(true);
            OkHttpClient client = (OkHttpClient) field.get(sut);

            assertThat(client.connectTimeoutMillis()).isEqualTo(10_000);
            assertThat(client.readTimeoutMillis()).isEqualTo(10_000);
            assertThat(client.callTimeoutMillis()).isEqualTo(10_000);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot access httpClient field", e);
        }
    }

    // ── NetworkException exit code ───────────────────────────────────

    @Test
    @DisplayName("AC-5.2: NetworkException.exitCode() == 10")
    void networkException_exitCodeIs10() {
        CaptionDownloader sut = downloaderReturning(503, "Unavailable");

        try {
            sut.download(TIMEDTEXT_URL);
        } catch (NetworkException e) {
            assertThat(e.exitCode()).isEqualTo(10);
            return;
        }
        throw new AssertionError("Expected NetworkException");
    }
}
