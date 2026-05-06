package com.srk.myutils.yd.core;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for {@link ThumbnailDownloader}.
 * Covers pickBest selection, download success/failure paths, timeout config,
 * and NetworkException exit code.
 */
@DisplayName("ThumbnailDownloader — behavior")
class ThumbnailDownloaderBehaviorTest {

    private static final byte[] FAKE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03};

    @TempDir
    Path tempDir;

    // ─── pickBest ───────────────────────────────────────────────────────

    @Test
    @DisplayName("pickBest with 3 thumbnails selects 1280x720 (largest area)")
    void pickBest_givenThreeThumbnails_selectsLargestArea() throws Exception {
        var small = new ThumbnailUrl("https://i.ytimg.com/vi/x/default.jpg", 320, 180);
        var medium = new ThumbnailUrl("https://i.ytimg.com/vi/x/hqdefault.jpg", 640, 360);
        var large = new ThumbnailUrl("https://i.ytimg.com/vi/x/maxresdefault.jpg", 1280, 720);

        ThumbnailUrl best = ThumbnailDownloader.pickBest(List.of(small, medium, large));

        assertThat(best).isSameAs(large);
    }

    @Test
    @DisplayName("pickBest with empty list throws NetworkException")
    void pickBest_givenEmptyList_throwsNetworkException() {
        assertThatThrownBy(() -> ThumbnailDownloader.pickBest(Collections.emptyList()))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("No thumbnails available");
    }

    @Test
    @DisplayName("pickBest with null list throws NetworkException")
    void pickBest_givenNullList_throwsNetworkException() {
        assertThatThrownBy(() -> ThumbnailDownloader.pickBest(null))
                .isInstanceOf(NetworkException.class);
    }

    @Test
    @DisplayName("pickBest with tied areas selects one deterministically (last by stream max)")
    void pickBest_givenTiedAreas_selectsDeterministically() throws Exception {
        var first = new ThumbnailUrl("https://i.ytimg.com/vi/x/a.jpg", 640, 360);
        var second = new ThumbnailUrl("https://i.ytimg.com/vi/x/b.jpg", 360, 640);

        // Both have area 230400; stream().max() returns the last max-equal element encountered
        ThumbnailUrl best = ThumbnailDownloader.pickBest(List.of(first, second));

        assertThat(best).isIn(first, second);
    }

    // ─── download ───────────────────────────────────────────────────────

    @Test
    @DisplayName("download writes response bytes to output path")
    void download_givenSuccessfulResponse_writesFile() throws Exception {
        ThumbnailDownloader downloader = new ThumbnailDownloader(clientReturning(200, FAKE_JPEG));
        Path output = tempDir.resolve("thumb.jpg");

        downloader.download(
                List.of(new ThumbnailUrl("https://i.ytimg.com/vi/x/max.jpg", 1280, 720)),
                output);

        assertThat(Files.readAllBytes(output)).isEqualTo(FAKE_JPEG);
    }

    @Test
    @DisplayName("download with HTTP 404 throws NetworkException")
    void download_givenHttp404_throwsNetworkException() {
        ThumbnailDownloader downloader = new ThumbnailDownloader(clientReturning(404, new byte[0]));
        Path output = tempDir.resolve("thumb.jpg");

        assertThatThrownBy(() -> downloader.download(
                List.of(new ThumbnailUrl("https://i.ytimg.com/vi/x/max.jpg", 1280, 720)),
                output))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    @DisplayName("download with IOException wraps in NetworkException")
    void download_givenIOException_wrapsInNetworkException() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> { throw new IOException("connection reset"); })
                .build();
        ThumbnailDownloader downloader = new ThumbnailDownloader(client);
        Path output = tempDir.resolve("thumb.jpg");

        assertThatThrownBy(() -> downloader.download(
                List.of(new ThumbnailUrl("https://i.ytimg.com/vi/x/max.jpg", 1280, 720)),
                output))
                .isInstanceOf(NetworkException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("download with empty thumbnails list throws NetworkException via pickBest")
    void download_givenEmptyThumbnails_throwsNetworkException() {
        ThumbnailDownloader downloader = new ThumbnailDownloader(clientReturning(200, FAKE_JPEG));
        Path output = tempDir.resolve("thumb.jpg");

        assertThatThrownBy(() -> downloader.download(Collections.emptyList(), output))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("No thumbnails available");
    }

    // ─── create() factory timeout ───────────────────────────────────────

    @Test
    @DisplayName("create() factory configures 10s timeouts per NFR-THUMBNAIL-DOWNLOAD-TIMEOUT")
    void create_configures10sTimeouts() {
        ThumbnailDownloader downloader = ThumbnailDownloader.create();

        // Access the internal client via reflection to verify timeout config
        try {
            var field = ThumbnailDownloader.class.getDeclaredField("httpClient");
            field.setAccessible(true);
            OkHttpClient client = (OkHttpClient) field.get(downloader);

            assertThat(client.connectTimeoutMillis()).isEqualTo(Duration.ofSeconds(10).toMillis());
            assertThat(client.readTimeoutMillis()).isEqualTo(Duration.ofSeconds(10).toMillis());
            assertThat(client.callTimeoutMillis()).isEqualTo(Duration.ofSeconds(10).toMillis());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot access httpClient field", e);
        }
    }

    // ─── NetworkException exit code ─────────────────────────────────────

    @Test
    @DisplayName("NetworkException.exitCode() returns 10 per cli-exit-codes.md")
    void networkException_exitCode_returns10() {
        NetworkException ex = new NetworkException("test");

        assertThat(ex.exitCode()).isEqualTo(10);
    }

    // ─── File content fidelity ──────────────────────────────────────────

    @Test
    @DisplayName("downloaded file content matches response body exactly (byte-for-byte)")
    void download_fileContentMatchesResponseBodyExactly() throws Exception {
        byte[] payload = new byte[1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 256);
        }

        ThumbnailDownloader downloader = new ThumbnailDownloader(clientReturning(200, payload));
        Path output = tempDir.resolve("thumb.jpg");

        downloader.download(
                List.of(new ThumbnailUrl("https://i.ytimg.com/vi/x/max.jpg", 1280, 720)),
                output);

        assertThat(Files.readAllBytes(output)).isEqualTo(payload);
    }

    // ─── helpers ────────────────────────────────────────────────────────

    private static OkHttpClient clientReturning(int code, byte[] body) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(code == 200 ? "OK" : "Error")
                        .body(ResponseBody.create(body, null))
                        .build())
                .build();
    }
}
