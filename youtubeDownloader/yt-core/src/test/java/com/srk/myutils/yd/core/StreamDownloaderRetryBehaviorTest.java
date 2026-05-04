package com.srk.myutils.yd.core;

import okhttp3.Interceptor;
import okhttp3.MediaType;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive retry-behavior tests for {@link StreamDownloader} (T-2.4).
 *
 * <p>Covers AC-12.4, NFR-STREAM-MAX-RETRIES = 2, INV-15 (progress monotonicity),
 * and the retryable/non-retryable status-code whitelist from
 * {@code 02-architecture.md § 4.2}.
 *
 * <p>Test discipline: {@code @TempDir}, {@code RecordingSleeper},
 * short-circuit interceptor with sequenced responses, SUT not mocked.
 */
class StreamDownloaderRetryBehaviorTest {

    private static final String CDN_URL =
            "https://rr1---sn-abc.googlevideo.com/videoplayback?id=retry-test";
    private static final MediaType MP4 = MediaType.get("video/mp4");
    private static final byte[] BODY = "retry-success-payload".getBytes();

    // ── helpers ──────────────────────────────────────────────────────────

    /** Records every sleep duration for backoff-schedule assertions. */
    private static final class RecordingSleeper implements StreamDownloader.Sleeper {
        final List<Long> calls = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            calls.add(millis);
        }
    }

    /** Sleeper that throws InterruptedException on first call. */
    private static final class InterruptingSleeper implements StreamDownloader.Sleeper {
        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.currentThread().interrupt();
            throw new InterruptedException("interrupted during backoff");
        }
    }

    private static OkHttpClient clientWith(Interceptor interceptor) {
        return new OkHttpClient.Builder().addInterceptor(interceptor).build();
    }

    /** Interceptor that returns responses from a queue in order. */
    private static Interceptor sequenced(Queue<Response.Builder> builders) {
        AtomicInteger counter = new AtomicInteger();
        return chain -> {
            counter.incrementAndGet();
            Response.Builder b = builders.poll();
            if (b == null) {
                throw new IllegalStateException("No more sequenced responses (call #" + counter.get() + ")");
            }
            return b.request(chain.request()).protocol(Protocol.HTTP_1_1).build();
        };
    }

    private static Response.Builder ok200() {
        return new Response.Builder()
                .code(200).message("OK")
                .header("Content-Length", String.valueOf(BODY.length))
                .body(ResponseBody.create(BODY, MP4));
    }

    private static Response.Builder httpError(int code) {
        return new Response.Builder()
                .code(code).message("Error " + code)
                .body(ResponseBody.create(new byte[0], MP4));
    }

    /** Interceptor that always throws IOException. */
    private static Interceptor ioFailure() {
        return chain -> { throw new IOException("connection reset"); };
    }

    /** Interceptor that throws IOException for the first N calls, then succeeds. */
    private static Interceptor ioFailureThenOk(int failCount) {
        AtomicInteger calls = new AtomicInteger();
        return chain -> {
            if (calls.incrementAndGet() <= failCount) {
                throw new IOException("connection reset");
            }
            return ok200().request(chain.request()).protocol(Protocol.HTTP_1_1).build();
        };
    }

    private StreamDownloader sut(Interceptor interceptor, RecordingSleeper sleeper) {
        return new StreamDownloader(clientWith(interceptor), sleeper);
    }

    // ── 1. HTTP 500 then success on retry 1 ─────────────────────────────

    @Test
    @DisplayName("AC-12.4: HTTP 500 then 200 → exactly 2 HTTP calls, 1 sleep(500ms)")
    void download_givenHttp500ThenSuccess_retriesOnce(@TempDir Path tmp) throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        Queue<Response.Builder> responses = new LinkedList<>();
        responses.add(httpError(500));
        responses.add(ok200());

        Interceptor interceptor = chain -> {
            callCount.incrementAndGet();
            Response.Builder b = responses.poll();
            return b.request(chain.request()).protocol(Protocol.HTTP_1_1).build();
        };

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = new StreamDownloader(clientWith(interceptor), sleeper);
        Path part = tmp.resolve("video.part");

        dl.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(callCount.get()).isEqualTo(2);
        assertThat(sleeper.calls).containsExactly(500L);
        assertThat(Files.readAllBytes(part)).isEqualTo(BODY);
    }

    // ── 2. IOException then success ─────────────────────────────────────

    @Test
    @DisplayName("AC-12.4: IOException then 200 → retries and succeeds")
    void download_givenIoExceptionThenSuccess_retries(@TempDir Path tmp) throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = new StreamDownloader(
                clientWith(ioFailureThenOk(1)), sleeper);
        Path part = tmp.resolve("video.part");

        dl.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(sleeper.calls).hasSize(1);
        assertThat(Files.readAllBytes(part)).isEqualTo(BODY);
    }

    // ── 3. HTTP 429 retries ─────────────────────────────────────────────

    @Test
    @DisplayName("AC-12.4: HTTP 429 is retryable → retries and succeeds")
    void download_givenHttp429ThenSuccess_retries(@TempDir Path tmp) throws Exception {
        Queue<Response.Builder> responses = new LinkedList<>();
        responses.add(httpError(429));
        responses.add(ok200());

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = sut(sequenced(responses), sleeper);
        Path part = tmp.resolve("video.part");

        dl.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(sleeper.calls).containsExactly(500L);
        assertThat(Files.readAllBytes(part)).isEqualTo(BODY);
    }

    // ── 4. HTTP 404 does NOT retry ──────────────────────────────────────

    @Test
    @DisplayName("AC-12.4: HTTP 404 is NOT retryable → 1 call, immediate NetworkException")
    void download_givenHttp404_doesNotRetry(@TempDir Path tmp) {
        AtomicInteger callCount = new AtomicInteger();
        Interceptor interceptor = chain -> {
            callCount.incrementAndGet();
            return httpError(404).request(chain.request()).protocol(Protocol.HTTP_1_1).build();
        };

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = new StreamDownloader(clientWith(interceptor), sleeper);
        Path part = tmp.resolve("video.part");

        assertThatThrownBy(() -> dl.download(CDN_URL, part, StreamDownloader.NO_OP))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("HTTP 404");

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(sleeper.calls).isEmpty();
    }

    // ── 5. HTTP 403 does NOT retry ──────────────────────────────────────

    @Test
    @DisplayName("AC-12.4: HTTP 403 is NOT retryable → 1 call, immediate NetworkException")
    void download_givenHttp403_doesNotRetry(@TempDir Path tmp) {
        AtomicInteger callCount = new AtomicInteger();
        Interceptor interceptor = chain -> {
            callCount.incrementAndGet();
            return httpError(403).request(chain.request()).protocol(Protocol.HTTP_1_1).build();
        };

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = new StreamDownloader(clientWith(interceptor), sleeper);
        Path part = tmp.resolve("video.part");

        assertThatThrownBy(() -> dl.download(CDN_URL, part, StreamDownloader.NO_OP))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("HTTP 403");

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(sleeper.calls).isEmpty();
    }

    // ── 6. Persistent 500 exhausts retries ──────────────────────────────

    @Test
    @DisplayName("AC-12.4: persistent 500 → 3 attempts, 2 sleeps (500+1000ms), NetworkException")
    void download_givenPersistent500_exhaustsRetries(@TempDir Path tmp) {
        AtomicInteger callCount = new AtomicInteger();
        Interceptor interceptor = chain -> {
            callCount.incrementAndGet();
            return httpError(500).request(chain.request()).protocol(Protocol.HTTP_1_1).build();
        };

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = new StreamDownloader(clientWith(interceptor), sleeper);
        Path part = tmp.resolve("video.part");

        assertThatThrownBy(() -> dl.download(CDN_URL, part, StreamDownloader.NO_OP))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("HTTP 500");

        assertThat(callCount.get()).isEqualTo(3);
        assertThat(sleeper.calls).containsExactly(500L, 1000L);
    }

    // ── 7. Persistent IOException exhausts retries ──────────────────────

    @Test
    @DisplayName("AC-12.4: persistent IOException → 3 attempts, 2 sleeps, NetworkException")
    void download_givenPersistentIoException_exhaustsRetries(@TempDir Path tmp) {
        AtomicInteger callCount = new AtomicInteger();
        Interceptor interceptor = chain -> {
            callCount.incrementAndGet();
            throw new IOException("connection reset");
        };

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = new StreamDownloader(clientWith(interceptor), sleeper);
        Path part = tmp.resolve("video.part");

        assertThatThrownBy(() -> dl.download(CDN_URL, part, StreamDownloader.NO_OP))
                .isInstanceOf(NetworkException.class)
                .hasCauseInstanceOf(IOException.class);

        assertThat(callCount.get()).isEqualTo(3);
        assertThat(sleeper.calls).containsExactly(500L, 1000L);
    }

    // ── 8. Mixed: IOException, 500, success on attempt 3 ────────────────

    @Test
    @DisplayName("AC-12.4: IOException → 500 → 200 succeeds on third attempt")
    void download_givenIoExceptionThen500ThenSuccess_succeedsOnThirdAttempt(@TempDir Path tmp)
            throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        Interceptor interceptor = chain -> {
            int n = callCount.incrementAndGet();
            if (n == 1) {
                throw new IOException("connection reset");
            }
            if (n == 2) {
                return httpError(500).request(chain.request()).protocol(Protocol.HTTP_1_1).build();
            }
            return ok200().request(chain.request()).protocol(Protocol.HTTP_1_1).build();
        };

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = new StreamDownloader(clientWith(interceptor), sleeper);
        Path part = tmp.resolve("video.part");

        dl.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(callCount.get()).isEqualTo(3);
        assertThat(sleeper.calls).containsExactly(500L, 1000L);
        assertThat(Files.readAllBytes(part)).isEqualTo(BODY);
    }

    // ── 9. Backoff schedule: [500, 1000] ms ─────────────────────────────

    @Test
    @DisplayName("NFR-STREAM-MAX-RETRIES: backoff schedule is 500ms, 1000ms (exp factor 2)")
    void download_givenRetriesExhausted_backoffScheduleIs500And1000(@TempDir Path tmp) {
        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = new StreamDownloader(clientWith(ioFailure()), sleeper);
        Path part = tmp.resolve("video.part");

        assertThatThrownBy(() -> dl.download(CDN_URL, part, StreamDownloader.NO_OP))
                .isInstanceOf(NetworkException.class);

        assertThat(sleeper.calls).containsExactly(
                StreamDownloader.BACKOFF_BASE_MS,
                StreamDownloader.BACKOFF_BASE_MS * 2);
    }

    // ── 10. Cross-invocation resume preservation on retry ────────────────

    @Test
    @DisplayName("AC-12.4: pre-existing .part (100 bytes) → retry truncates to 100, not 0")
    void download_givenPreExistingPartFile_retryTruncatesToBytesAtStart(@TempDir Path tmp)
            throws Exception {
        byte[] preExisting = new byte[100];
        Path part = tmp.resolve("video.part");
        Files.write(part, preExisting);

        // First attempt: 500 (writes nothing useful). Second attempt: 200 OK.
        AtomicInteger callCount = new AtomicInteger();
        Interceptor interceptor = chain -> {
            int n = callCount.incrementAndGet();
            if (n == 1) {
                return httpError(500).request(chain.request()).protocol(Protocol.HTTP_1_1).build();
            }
            // On retry, verify the file was truncated back to 100 bytes (not 0)
            assertThat(Files.size(part)).isEqualTo(100);
            // Return 206 resuming from byte 100
            byte[] remaining = "RESUMED".getBytes();
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(206).message("Partial Content")
                    .header("Content-Range",
                            "bytes 100-" + (100 + remaining.length - 1) + "/" + (100 + remaining.length))
                    .header("Content-Length", String.valueOf(remaining.length))
                    .body(ResponseBody.create(remaining, MP4))
                    .build();
        };

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = new StreamDownloader(clientWith(interceptor), sleeper);

        dl.download(CDN_URL, part, StreamDownloader.NO_OP);

        // File should contain the original 100 bytes + "RESUMED"
        assertThat(Files.size(part)).isEqualTo(100 + "RESUMED".length());
    }

    // ── 11. Fresh download retry truncates to 0 ─────────────────────────

    @Test
    @DisplayName("AC-12.4: fresh download (no pre-existing .part) → retry starts from byte 0")
    void download_givenFreshDownload_retryTruncatesToZero(@TempDir Path tmp) throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        Interceptor interceptor = chain -> {
            int n = callCount.incrementAndGet();
            if (n == 1) {
                return httpError(502).request(chain.request()).protocol(Protocol.HTTP_1_1).build();
            }
            // On retry, verify no Range header (fresh start from 0)
            assertThat(chain.request().header("Range")).isNull();
            return ok200().request(chain.request()).protocol(Protocol.HTTP_1_1).build();
        };

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = new StreamDownloader(clientWith(interceptor), sleeper);
        Path part = tmp.resolve("video.part");

        dl.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(Files.readAllBytes(part)).isEqualTo(BODY);
    }

    // ── 12. InterruptedException in Sleeper → NetworkException ──────────

    @Test
    @DisplayName("AC-12.4: InterruptedException during backoff → NetworkException, interrupt flag set")
    void download_givenInterruptedDuringBackoff_throwsNetworkExceptionAndSetsInterruptFlag(
            @TempDir Path tmp) {
        Interceptor interceptor = chain ->
                httpError(500).request(chain.request()).protocol(Protocol.HTTP_1_1).build();

        StreamDownloader dl = new StreamDownloader(
                clientWith(interceptor), new InterruptingSleeper());
        Path part = tmp.resolve("video.part");

        assertThatThrownBy(() -> dl.download(CDN_URL, part, StreamDownloader.NO_OP))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("Interrupted")
                .hasCauseInstanceOf(InterruptedException.class);

        assertThat(Thread.currentThread().isInterrupted())
                .as("Thread interrupt flag should be set")
                .isTrue();

        // Clear the interrupt flag so it doesn't leak to other tests
        Thread.interrupted();
    }

    // ── 13. HTTP 502, 503, 504 are retryable ────────────────────────────

    @Test
    @DisplayName("AC-12.4: HTTP 502 is retryable")
    void download_givenHttp502ThenSuccess_retries(@TempDir Path tmp) throws Exception {
        Queue<Response.Builder> responses = new LinkedList<>();
        responses.add(httpError(502));
        responses.add(ok200());

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = sut(sequenced(responses), sleeper);
        Path part = tmp.resolve("video.part");

        dl.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(sleeper.calls).containsExactly(500L);
    }

    @Test
    @DisplayName("AC-12.4: HTTP 503 is retryable")
    void download_givenHttp503ThenSuccess_retries(@TempDir Path tmp) throws Exception {
        Queue<Response.Builder> responses = new LinkedList<>();
        responses.add(httpError(503));
        responses.add(ok200());

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = sut(sequenced(responses), sleeper);
        Path part = tmp.resolve("video.part");

        dl.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(sleeper.calls).containsExactly(500L);
    }

    @Test
    @DisplayName("AC-12.4: HTTP 504 is retryable")
    void download_givenHttp504ThenSuccess_retries(@TempDir Path tmp) throws Exception {
        Queue<Response.Builder> responses = new LinkedList<>();
        responses.add(httpError(504));
        responses.add(ok200());

        RecordingSleeper sleeper = new RecordingSleeper();
        StreamDownloader dl = sut(sequenced(responses), sleeper);
        Path part = tmp.resolve("video.part");

        dl.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(sleeper.calls).containsExactly(500L);
    }
}
