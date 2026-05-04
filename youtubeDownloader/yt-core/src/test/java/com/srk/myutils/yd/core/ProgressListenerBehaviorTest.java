package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Comprehensive behavior tests for {@link ProgressListener} interface
 * and its integration with {@link StreamDownloader}.
 *
 * <p>Covers AC-4.1 (progress events), AC-9.3 (injectable listener),
 * and backward-compatibility of deprecated {@link StreamDownloader.ProgressCallback}.
 */
@SuppressWarnings("deprecation")
class ProgressListenerBehaviorTest {

    private static final String CDN_URL = "https://rr1---sn-abc.googlevideo.com/videoplayback?id=test";
    private static final MediaType MP4 = MediaType.get("video/mp4");
    private static final byte[] BODY = "test-data-for-progress".getBytes();

    private static OkHttpClient cannedClient() {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK")
                        .header("Content-Length", String.valueOf(BODY.length))
                        .body(ResponseBody.create(BODY, MP4))
                        .build())
                .build();
    }

    // ── 1. NO_OP safety ─────────────────────────────────────────────────

    @Test
    @DisplayName("NO_OP does not throw on zero/zero")
    void noOp_givenZeroZero_doesNotThrow() {
        assertThatCode(() -> ProgressListener.NO_OP.onProgress(0, 0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NO_OP does not throw on negative totalBytes")
    void noOp_givenNegativeTotalBytes_doesNotThrow() {
        assertThatCode(() -> ProgressListener.NO_OP.onProgress(100, -1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NO_OP does not throw on Long.MAX_VALUE")
    void noOp_givenMaxValues_doesNotThrow() {
        assertThatCode(() -> ProgressListener.NO_OP.onProgress(Long.MAX_VALUE, Long.MAX_VALUE))
                .doesNotThrowAnyException();
    }

    // ── 2. Functional interface (lambda) ────────────────────────────────

    @Test
    @DisplayName("Lambda implementation receives correct values — functional interface works")
    void lambda_givenProgress_receivesValues() {
        AtomicInteger count = new AtomicInteger();

        ProgressListener pl = (b, t) -> count.incrementAndGet();

        pl.onProgress(100, 200);
        pl.onProgress(200, 200);

        assertThat(count.get()).isEqualTo(2);
    }

    // ── 3. StreamDownloader accepts ProgressListener variants ───────────

    @Test
    @DisplayName("AC-4.1: StreamDownloader.download accepts NO_OP ProgressListener")
    void download_givenNoOpListener_completesSuccessfully(@TempDir Path tmp) {
        StreamDownloader sut = new StreamDownloader(cannedClient());
        Path part = tmp.resolve("video.part");

        assertThatCode(() -> sut.download(CDN_URL, part, ProgressListener.NO_OP))
                .doesNotThrowAnyException();

        assertThat(Files.exists(part)).isTrue();
    }

    @Test
    @DisplayName("AC-4.1: StreamDownloader.download accepts a lambda ProgressListener")
    void download_givenLambdaListener_invokesIt(@TempDir Path tmp) throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        ProgressListener lambda = (b, t) -> invocations.incrementAndGet();

        StreamDownloader sut = new StreamDownloader(cannedClient());
        Path part = tmp.resolve("video.part");

        sut.download(CDN_URL, part, lambda);

        assertThat(invocations.get()).isGreaterThan(0);
    }

    // ── 4. Backward compat: deprecated StreamDownloader.NO_OP ───────────

    @Test
    @DisplayName("Backward compat: StreamDownloader.NO_OP @Deprecated still works")
    void deprecatedNoOp_stillFunctions() {
        assertThatCode(() -> StreamDownloader.NO_OP.onProgress(50, 100))
                .doesNotThrowAnyException();

        assertThat(StreamDownloader.NO_OP).isSameAs(ProgressListener.NO_OP);
    }

    // ── 5. Backward compat: ProgressCallback bridge ─────────────────────

    @Test
    @DisplayName("Backward compat: ProgressCallback extends ProgressListener — compiles and works")
    void progressCallback_givenLambda_isProgressListener() {
        StreamDownloader.ProgressCallback callback = (b, t) -> {};

        assertThat(callback).isInstanceOf(ProgressListener.class);
        assertThatCode(() -> callback.onProgress(256, 512))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Backward compat: ProgressCallback accepted by StreamDownloader.download")
    void download_givenProgressCallback_completesSuccessfully(@TempDir Path tmp) throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        StreamDownloader.ProgressCallback callback = (b, t) -> invocations.incrementAndGet();

        StreamDownloader sut = new StreamDownloader(cannedClient());
        Path part = tmp.resolve("video.part");

        sut.download(CDN_URL, part, callback);

        assertThat(invocations.get()).isGreaterThan(0);
    }

    // ── 6. AC-9.3: library embedder custom listener without CLI dep ─────

    @Test
    @DisplayName("AC-9.3: custom ProgressListener works without any CLI dependency")
    void customListener_givenCoreOnlyDep_receivesProgressEvents(@TempDir Path tmp) throws Exception {
        // This test lives in yt-core — no CLI on classpath.
        // A library embedder would do exactly this.
        StringBuilder log = new StringBuilder();
        ProgressListener custom = (bytesWritten, totalBytes) ->
                log.append(bytesWritten).append("/").append(totalBytes).append(";");

        StreamDownloader sut = new StreamDownloader(cannedClient());
        Path part = tmp.resolve("video.part");

        sut.download(CDN_URL, part, custom);

        assertThat(log.toString()).contains(String.valueOf(BODY.length));
    }
}
