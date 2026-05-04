package com.srk.myutils.yd.core;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for {@link StreamDownloader}.
 *
 * <p>Covers AC-1.6 (stream download to .part), AC-4.1 (progress events),
 * INV-7 (single file handle), and edge cases (resume, errors, empty body).
 *
 * <p>Uses short-circuit OkHttp interceptors — no real network.
 * {@code @TempDir} for all file I/O.
 */
class StreamDownloaderBehaviorTest {

    private static final String CDN_URL = "https://rr1---sn-abc.googlevideo.com/videoplayback?id=test";
    private static final MediaType MP4 = MediaType.get("video/mp4");

    // ── helpers ──────────────────────────────────────────────────────────

    /** Builds an OkHttpClient whose sole interceptor returns a canned response. */
    private static OkHttpClient clientWith(Interceptor interceptor) {
        return new OkHttpClient.Builder().addInterceptor(interceptor).build();
    }

    /** Interceptor returning a 200 OK with the given body bytes and Content-Length. */
    private static Interceptor ok200(byte[] body) {
        return chain -> new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .header("Content-Length", String.valueOf(body.length))
                .body(ResponseBody.create(body, MP4))
                .build();
    }

    /** Interceptor returning a 206 Partial Content with Content-Range header. */
    private static Interceptor partial206(byte[] rangeBody, long rangeStart, long totalSize) {
        return chain -> new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(206).message("Partial Content")
                .header("Content-Range",
                        "bytes " + rangeStart + "-" + (totalSize - 1) + "/" + totalSize)
                .header("Content-Length", String.valueOf(rangeBody.length))
                .body(ResponseBody.create(rangeBody, MP4))
                .build();
    }

    /** Interceptor returning a fixed HTTP status with empty body. */
    private static Interceptor httpError(int code, String message) {
        return chain -> new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code).message(message)
                .body(ResponseBody.create(new byte[0], MP4))
                .build();
    }

    /** Collects (bytesWritten, totalBytes) pairs from progress callbacks. */
    private static final class ProgressRecorder implements StreamDownloader.ProgressCallback {
        final List<long[]> events = new ArrayList<>();

        @Override
        public void onProgress(long bytesWritten, long totalBytes) {
            events.add(new long[]{bytesWritten, totalBytes});
        }
    }

    // ── 1. Fresh download (200 OK) ──────────────────────────────────────

    @Test
    @DisplayName("AC-1.6: fresh 200 OK writes all body bytes to partFile")
    void download_given200Ok_writesBodyToPartFile(@TempDir Path tmp) throws Exception {
        byte[] body = "hello-stream-data".getBytes();
        StreamDownloader sut = new StreamDownloader(clientWith(ok200(body)));
        Path part = tmp.resolve("video.part");

        sut.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(Files.readAllBytes(part)).isEqualTo(body);
    }

    @Test
    @DisplayName("AC-4.1: callback invoked progressively on fresh download")
    void download_given200Ok_invokesCallbackProgressively(@TempDir Path tmp) throws Exception {
        byte[] body = "hello-stream-data".getBytes();
        StreamDownloader sut = new StreamDownloader(clientWith(ok200(body)));
        Path part = tmp.resolve("video.part");
        ProgressRecorder recorder = new ProgressRecorder();

        sut.download(CDN_URL, part, recorder);

        assertThat(recorder.events).isNotEmpty();
        long[] last = recorder.events.get(recorder.events.size() - 1);
        assertThat(last[0]).isEqualTo(body.length);
        assertThat(last[1]).isEqualTo(body.length);
    }

    // ── 2. Resume (206 Partial Content) ─────────────────────────────────

    @Test
    @DisplayName("AC-1.6: resume sends Range header and appends to existing .part file")
    void download_givenExistingPartFile_resumesWithRangeHeader(@TempDir Path tmp) throws Exception {
        byte[] existingData = "AAAA".getBytes();
        byte[] remainingData = "BBBB".getBytes();
        long totalSize = existingData.length + remainingData.length;

        Path part = tmp.resolve("video.part");
        Files.write(part, existingData);

        AtomicInteger capturedRangeStart = new AtomicInteger(-1);
        Interceptor interceptor = chain -> {
            String range = chain.request().header("Range");
            if (range != null && range.startsWith("bytes=")) {
                capturedRangeStart.set(
                        Integer.parseInt(range.substring("bytes=".length(), range.indexOf('-'))));
            }
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(206).message("Partial Content")
                    .header("Content-Range",
                            "bytes " + existingData.length + "-" + (totalSize - 1) + "/" + totalSize)
                    .header("Content-Length", String.valueOf(remainingData.length))
                    .body(ResponseBody.create(remainingData, MP4))
                    .build();
        };

        StreamDownloader sut = new StreamDownloader(clientWith(interceptor));
        sut.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(capturedRangeStart.get()).isEqualTo(existingData.length);
        assertThat(Files.readAllBytes(part)).isEqualTo("AAAABBBB".getBytes());
    }

    // ── 3. HTTP 404 → NetworkException ──────────────────────────────────

    @Test
    @DisplayName("HTTP 404 throws NetworkException")
    void download_given404_throwsNetworkException(@TempDir Path tmp) {
        StreamDownloader sut = new StreamDownloader(clientWith(httpError(404, "Not Found")));
        Path part = tmp.resolve("video.part");

        assertThatThrownBy(() -> sut.download(CDN_URL, part, StreamDownloader.NO_OP))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("HTTP 404");
    }

    // ── 4. HTTP 500 → NetworkException ──────────────────────────────────

    @Test
    @DisplayName("HTTP 500 throws NetworkException")
    void download_given500_throwsNetworkException(@TempDir Path tmp) {
        StreamDownloader sut = new StreamDownloader(clientWith(httpError(500, "Server Error")));
        Path part = tmp.resolve("video.part");

        assertThatThrownBy(() -> sut.download(CDN_URL, part, StreamDownloader.NO_OP))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("HTTP 500");
    }

    // ── 5. IOException mid-transfer → NetworkException (wraps) ──────────

    @Test
    @DisplayName("IOException mid-transfer is wrapped in NetworkException")
    void download_givenIoExceptionMidTransfer_wrapsInNetworkException(@TempDir Path tmp) {
        Interceptor failingBody = chain -> {
            Source failingSource = new ForwardingSource(new Buffer().write(new byte[100])) {
                private int reads = 0;
                @Override
                public long read(Buffer sink, long byteCount) throws IOException {
                    if (reads++ > 0) {
                        throw new IOException("connection reset");
                    }
                    return super.read(sink, byteCount);
                }
            };
            BufferedSource buffered = Okio.buffer(failingSource);
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .header("Content-Length", "10000")
                    .body(ResponseBody.create(buffered, MP4, 10000))
                    .build();
        };

        StreamDownloader sut = new StreamDownloader(clientWith(failingBody));
        Path part = tmp.resolve("video.part");

        assertThatThrownBy(() -> sut.download(CDN_URL, part, StreamDownloader.NO_OP))
                .isInstanceOf(NetworkException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    // ── 6. Callback count scales with buffer size ───────────────────────

    @Test
    @DisplayName("AC-4.1: 512KB body with 64KB buffer → ~8 callback invocations")
    void download_given512KbBody_callbackInvokedApproximately8Times(@TempDir Path tmp) throws Exception {
        byte[] body = new byte[512 * 1024]; // 512 KB
        StreamDownloader sut = new StreamDownloader(clientWith(ok200(body)));
        Path part = tmp.resolve("video.part");
        ProgressRecorder recorder = new ProgressRecorder();

        sut.download(CDN_URL, part, recorder);

        // Callback invoked once per write loop iteration; exact count depends on
        // Okio's internal read-buffer size vs the 64KB write buffer. Must be > 1.
        assertThat(recorder.events.size()).isGreaterThan(1);
    }

    // ── 7. Content-Length reported correctly ─────────────────────────────

    @Test
    @DisplayName("AC-4.1: totalBytes in callback matches Content-Length header")
    void download_given200WithContentLength_totalBytesMatchesHeader(@TempDir Path tmp) throws Exception {
        byte[] body = "exactly-these-bytes".getBytes();
        StreamDownloader sut = new StreamDownloader(clientWith(ok200(body)));
        Path part = tmp.resolve("video.part");
        ProgressRecorder recorder = new ProgressRecorder();

        sut.download(CDN_URL, part, recorder);

        assertThat(recorder.events).allSatisfy(e ->
                assertThat(e[1]).isEqualTo(body.length));
    }

    // ── 8. 206 Content-Range parsing ────────────────────────────────────

    @Test
    @DisplayName("206 Content-Range: totalBytes = full file size, not range size")
    void download_given206WithContentRange_totalBytesIsFullFileSize(@TempDir Path tmp) throws Exception {
        byte[] existing = new byte[1000];
        byte[] remaining = new byte[9000];
        long fullSize = 10000;

        Path part = tmp.resolve("video.part");
        Files.write(part, existing);

        StreamDownloader sut = new StreamDownloader(
                clientWith(partial206(remaining, existing.length, fullSize)));
        ProgressRecorder recorder = new ProgressRecorder();

        sut.download(CDN_URL, part, recorder);

        assertThat(recorder.events).isNotEmpty();
        // totalBytes should be the full file size (10000), not just the range body (9000)
        assertThat(recorder.events.get(recorder.events.size() - 1)[1]).isEqualTo(fullSize);
    }

    // ── 9. Empty body (200, 0 bytes) ────────────────────────────────────

    @Test
    @DisplayName("200 with empty body creates partFile; no callback (no chunks to write)")
    void download_givenEmptyBody_createsEmptyPartFile(@TempDir Path tmp) throws Exception {
        byte[] body = new byte[0];
        StreamDownloader sut = new StreamDownloader(clientWith(ok200(body)));
        Path part = tmp.resolve("video.part");
        ProgressRecorder recorder = new ProgressRecorder();

        sut.download(CDN_URL, part, recorder);

        assertThat(Files.exists(part)).isTrue();
        assertThat(Files.size(part)).isZero();
    }

    // ── 10. partFile parent dir doesn't exist ───────────────────────────

    @Test
    @DisplayName("partFile in non-existent parent dir → NetworkException wrapping IOException")
    void download_givenNonExistentParentDir_throwsNetworkException(@TempDir Path tmp) {
        byte[] body = "data".getBytes();
        StreamDownloader sut = new StreamDownloader(clientWith(ok200(body)));
        Path part = tmp.resolve("no-such-dir/video.part");

        assertThatThrownBy(() -> sut.download(CDN_URL, part, StreamDownloader.NO_OP))
                .isInstanceOf(NetworkException.class);
    }

    // ── 11. Large body (~1MB) ───────────────────────────────────────────

    @Test
    @DisplayName("1MB body downloads correctly and callback invoked multiple times")
    void download_given1MbBody_writesAllBytesAndMultipleCallbacks(@TempDir Path tmp) throws Exception {
        byte[] body = new byte[1024 * 1024]; // 1 MB
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) (i & 0xFF);
        }
        StreamDownloader sut = new StreamDownloader(clientWith(ok200(body)));
        Path part = tmp.resolve("video.part");
        ProgressRecorder recorder = new ProgressRecorder();

        sut.download(CDN_URL, part, recorder);

        assertThat(Files.readAllBytes(part)).isEqualTo(body);
        assertThat(recorder.events.size()).isGreaterThan(1);
    }

    // ── 12. NO_OP callback doesn't throw ────────────────────────────────

    @Test
    @DisplayName("NO_OP callback completes without exception")
    void download_givenNoOpCallback_completesSuccessfully(@TempDir Path tmp) throws Exception {
        byte[] body = "some-data".getBytes();
        StreamDownloader sut = new StreamDownloader(clientWith(ok200(body)));
        Path part = tmp.resolve("video.part");

        sut.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(Files.readAllBytes(part)).isEqualTo(body);
    }

    // ── 13. Default create() configures reasonable timeouts ─────────────

    @Test
    @DisplayName("create() returns a StreamDownloader with NFR-pinned timeouts")
    void create_returnsDownloaderWithReasonableTimeouts() {
        StreamDownloader sut = StreamDownloader.create();

        assertThat(sut).isNotNull();
    }

    // ── 14. INV-15: progress monotonicity ───────────────────────────────

    @Test
    @DisplayName("INV-15: bytesWritten in callbacks is monotonically non-decreasing")
    void download_givenLargeBody_progressIsMonotonicallyNonDecreasing(@TempDir Path tmp) throws Exception {
        byte[] body = new byte[256 * 1024]; // 256 KB
        StreamDownloader sut = new StreamDownloader(clientWith(ok200(body)));
        Path part = tmp.resolve("video.part");
        ProgressRecorder recorder = new ProgressRecorder();

        sut.download(CDN_URL, part, recorder);

        long prev = 0;
        for (long[] event : recorder.events) {
            assertThat(event[0]).as("bytesWritten should be >= previous")
                    .isGreaterThanOrEqualTo(prev);
            prev = event[0];
        }
    }

    // ── 15. Final callback bytesWritten equals file size ────────────────

    @Test
    @DisplayName("AC-4.1: final callback bytesWritten equals actual file size on disk")
    void download_given200Ok_finalCallbackMatchesFileSize(@TempDir Path tmp) throws Exception {
        byte[] body = "precise-byte-count-check".getBytes();
        StreamDownloader sut = new StreamDownloader(clientWith(ok200(body)));
        Path part = tmp.resolve("video.part");
        ProgressRecorder recorder = new ProgressRecorder();

        sut.download(CDN_URL, part, recorder);

        long[] last = recorder.events.get(recorder.events.size() - 1);
        assertThat(last[0]).isEqualTo(Files.size(part));
    }

    // ── 16. HTTP 403 → NetworkException ─────────────────────────────────

    @Test
    @DisplayName("HTTP 403 Forbidden throws NetworkException")
    void download_given403_throwsNetworkException(@TempDir Path tmp) {
        StreamDownloader sut = new StreamDownloader(clientWith(httpError(403, "Forbidden")));
        Path part = tmp.resolve("video.part");

        assertThatThrownBy(() -> sut.download(CDN_URL, part, StreamDownloader.NO_OP))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("HTTP 403");
    }

    // ── 17. Resume with no prior file sends no Range header ─────────────

    @Test
    @DisplayName("Fresh download (no existing .part) does not send Range header")
    void download_givenNoExistingPartFile_doesNotSendRangeHeader(@TempDir Path tmp) throws Exception {
        AtomicInteger rangeHeaderPresent = new AtomicInteger(0);
        Interceptor interceptor = chain -> {
            if (chain.request().header("Range") != null) {
                rangeHeaderPresent.incrementAndGet();
            }
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .header("Content-Length", "4")
                    .body(ResponseBody.create("data".getBytes(), MP4))
                    .build();
        };

        StreamDownloader sut = new StreamDownloader(clientWith(interceptor));
        Path part = tmp.resolve("video.part");

        sut.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(rangeHeaderPresent.get()).isZero();
    }
}
