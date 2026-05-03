package com.srk.myutils.yd.core;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for {@link InnerTubeRetryInterceptor}
 * covering AC-12.4, NFR-INNERTUBE-MAX-RETRIES=3, NFR-INNERTUBE-BACKOFF-BASE=500ms.
 *
 * <p>Tests exercise the interceptor directly via a scripted {@link Interceptor.Chain}
 * double and a recording {@link InnerTubeRetryInterceptor.Sleeper}.
 */
@DisplayName("InnerTubeRetryInterceptor — AC-12.4 behavior")
class InnerTubeRetryInterceptorBehaviorTest {

    private static final Request DUMMY_REQUEST = new Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player")
            .build();

    // ── Test doubles ────────────────────────────────────────────────

    /** Records sleep calls for backoff verification. */
    static final class RecordingSleeper implements InnerTubeRetryInterceptor.Sleeper {
        final List<Long> calls = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            calls.add(millis);
        }
    }

    /** Chain double that returns scripted responses / throws scripted exceptions. */
    static final class ScriptedChain implements Interceptor.Chain {
        private final Queue<ChainAction> actions;
        private final AtomicInteger proceedCount = new AtomicInteger();

        ScriptedChain(List<ChainAction> actions) {
            this.actions = new LinkedList<>(actions);
        }

        int proceedCount() { return proceedCount.get(); }

        @Override
        public Request request() { return DUMMY_REQUEST; }

        @Override
        public Response proceed(Request request) throws IOException {
            proceedCount.incrementAndGet();
            ChainAction action = actions.poll();
            if (action == null) {
                throw new IllegalStateException("ScriptedChain exhausted — more proceed() calls than scripted");
            }
            return action.execute(request);
        }

        // Unused Chain methods — minimal stubs
        @Override public okhttp3.Connection connection() { return null; }
        @Override public okhttp3.Call call() { return null; }
        @Override public int connectTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withConnectTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
        @Override public int readTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withReadTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
        @Override public int writeTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withWriteTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
    }

    @FunctionalInterface
    interface ChainAction {
        Response execute(Request request) throws IOException;
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static ChainAction respondWith(int code) {
        return request -> new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Status " + code)
                .body(ResponseBody.create("{}", MediaType.get("application/json")))
                .build();
    }

    private static ChainAction throwIOException(String msg) {
        return request -> { throw new IOException(msg); };
    }

    /** Response wrapper that tracks close() calls. */
    static final class CloseTrackingAction implements ChainAction {
        final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public Response execute(Request request) {
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body(new ResponseBody() {
                        @Override public MediaType contentType() { return MediaType.get("application/json"); }
                        @Override public long contentLength() { return 2; }
                        @Override public okio.BufferedSource source() {
                            return okio.Okio.buffer(okio.Okio.source(
                                    new java.io.ByteArrayInputStream("{}".getBytes())));
                        }
                        @Override public void close() {
                            closed.set(true);
                            super.close();
                        }
                    })
                    .build();
        }
    }

    // ── Constants verification ──────────────────────────────────────

    @Nested
    @DisplayName("Constants — NFR pinning")
    class Constants {

        @Test
        @DisplayName("MAX_RETRIES == 3 (NFR-INNERTUBE-MAX-RETRIES)")
        void maxRetries() {
            assertThat(InnerTubeRetryInterceptor.MAX_RETRIES).isEqualTo(3);
        }

        @Test
        @DisplayName("BACKOFF_BASE_MS == 500 (NFR-INNERTUBE-BACKOFF-BASE)")
        void backoffBase() {
            assertThat(InnerTubeRetryInterceptor.BACKOFF_BASE_MS).isEqualTo(500L);
        }
    }

    // ── Retry behavior ─────────────────────────────────────────────

    @Nested
    @DisplayName("Retry behavior — retryable vs non-retryable statuses")
    class RetryBehavior {

        @Test
        @DisplayName("200 on first attempt → no retry, sleeper never called")
        void intercept_givenSuccess_noRetry() throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(respondWith(200)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            Response response = interceptor.intercept(chain);

            assertThat(response.code()).isEqualTo(200);
            assertThat(chain.proceedCount()).isEqualTo(1);
            assertThat(sleeper.calls).isEmpty();
        }

        @Test
        @DisplayName("500 then 200 → retries once, sleeper called with 500ms")
        void intercept_given500ThenSuccess_retriesOnce() throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(respondWith(500), respondWith(200)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            Response response = interceptor.intercept(chain);

            assertThat(response.code()).isEqualTo(200);
            assertThat(chain.proceedCount()).isEqualTo(2);
            assertThat(sleeper.calls).containsExactly(500L);
        }

        @ParameterizedTest(name = "HTTP {0} triggers retry")
        @ValueSource(ints = {429, 500, 502, 503, 504})
        @DisplayName("Retryable statuses trigger retry")
        void intercept_givenRetryableStatus_retries(int status) throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(respondWith(status), respondWith(200)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            Response response = interceptor.intercept(chain);

            assertThat(response.code()).isEqualTo(200);
            assertThat(chain.proceedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("404 → no retry, response returned as-is")
        void intercept_given404_noRetry() throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(respondWith(404)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            Response response = interceptor.intercept(chain);

            assertThat(response.code()).isEqualTo(404);
            assertThat(chain.proceedCount()).isEqualTo(1);
            assertThat(sleeper.calls).isEmpty();
        }

        @Test
        @DisplayName("403 → no retry, response returned as-is")
        void intercept_given403_noRetry() throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(respondWith(403)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            Response response = interceptor.intercept(chain);

            assertThat(response.code()).isEqualTo(403);
            assertThat(chain.proceedCount()).isEqualTo(1);
            assertThat(sleeper.calls).isEmpty();
        }

        @ParameterizedTest(name = "HTTP {0} → no retry")
        @ValueSource(ints = {200, 201, 301, 302, 400, 401, 403, 404})
        @DisplayName("Non-retryable statuses are returned immediately")
        void intercept_givenNonRetryableStatus_noRetry(int status) throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(respondWith(status)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            Response response = interceptor.intercept(chain);

            assertThat(response.code()).isEqualTo(status);
            assertThat(chain.proceedCount()).isEqualTo(1);
            assertThat(sleeper.calls).isEmpty();
        }

        @Test
        @DisplayName("IOException then 200 → retries on network error")
        void intercept_givenIOExceptionThenSuccess_retries() throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(
                    throwIOException("Connection reset"),
                    respondWith(200)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            Response response = interceptor.intercept(chain);

            assertThat(response.code()).isEqualTo(200);
            assertThat(chain.proceedCount()).isEqualTo(2);
            assertThat(sleeper.calls).containsExactly(500L);
        }
    }

    // ── Retry exhaustion ────────────────────────────────────────────

    @Nested
    @DisplayName("Retry exhaustion — max retries reached")
    class RetryExhaustion {

        @Test
        @DisplayName("Persistent 500 → 4 attempts, returns final 500 response")
        void intercept_givenPersistent500_exhaustsRetries() throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(
                    respondWith(500), respondWith(500),
                    respondWith(500), respondWith(500)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            Response response = interceptor.intercept(chain);

            assertThat(response.code()).isEqualTo(500);
            assertThat(chain.proceedCount()).isEqualTo(4);
            assertThat(sleeper.calls).containsExactly(500L, 1000L, 2000L);
        }

        @Test
        @DisplayName("Persistent IOException → rethrown after 3 retries")
        void intercept_givenPersistentIOException_rethrows() {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(
                    throwIOException("fail 1"), throwIOException("fail 2"),
                    throwIOException("fail 3"), throwIOException("fail 4")));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            assertThatThrownBy(() -> interceptor.intercept(chain))
                    .isInstanceOf(IOException.class)
                    .hasMessage("fail 4");

            assertThat(chain.proceedCount()).isEqualTo(4);
            assertThat(sleeper.calls).containsExactly(500L, 1000L, 2000L);
        }

        @Test
        @DisplayName("Mixed failures then success on last attempt → succeeds")
        void intercept_givenMixedFailuresThenSuccess_succeedsOnLastAttempt() throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(
                    throwIOException("Connection reset"),
                    respondWith(500),
                    respondWith(503),
                    respondWith(200)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            Response response = interceptor.intercept(chain);

            assertThat(response.code()).isEqualTo(200);
            assertThat(chain.proceedCount()).isEqualTo(4);
            assertThat(sleeper.calls).containsExactly(500L, 1000L, 2000L);
        }
    }

    // ── Backoff timing ──────────────────────────────────────────────

    @Nested
    @DisplayName("Backoff timing — exponential with factor 2")
    class BackoffTiming {

        @Test
        @DisplayName("3 retries produce sleep delays [500, 1000, 2000]")
        void intercept_givenThreeRetries_exponentialBackoff() throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(
                    respondWith(500), respondWith(500),
                    respondWith(500), respondWith(500)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            interceptor.intercept(chain);

            assertThat(sleeper.calls).containsExactly(500L, 1000L, 2000L);
        }

        @Test
        @DisplayName("Sleeper not called on first attempt (success)")
        void intercept_givenFirstAttemptSuccess_noSleep() throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(respondWith(200)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            interceptor.intercept(chain);

            assertThat(sleeper.calls).isEmpty();
        }

        @Test
        @DisplayName("Never exceeds 3 sleep calls regardless of outcome")
        void intercept_givenMaxRetries_neverExceedsThreeSleeps() throws IOException {
            var sleeper = new RecordingSleeper();
            var chain = new ScriptedChain(List.of(
                    respondWith(500), respondWith(500),
                    respondWith(500), respondWith(500)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            interceptor.intercept(chain);

            assertThat(sleeper.calls).hasSize(3);
        }
    }

    // ── Intermediate response cleanup ───────────────────────────────

    @Nested
    @DisplayName("Intermediate response cleanup")
    class IntermediateResponseCleanup {

        @Test
        @DisplayName("Intermediate retryable response body is closed before retry")
        void intercept_givenRetryableResponse_closesBeforeRetry() throws IOException {
            var sleeper = new RecordingSleeper();
            var closeTracker = new CloseTrackingAction();
            var chain = new ScriptedChain(List.of(closeTracker, respondWith(200)));
            var interceptor = new InnerTubeRetryInterceptor(sleeper);

            Response response = interceptor.intercept(chain);

            assertThat(response.code()).isEqualTo(200);
            assertThat(closeTracker.closed.get())
                    .as("Intermediate 500 response should be closed")
                    .isTrue();
        }
    }

    // ── InterruptedException handling ───────────────────────────────

    @Nested
    @DisplayName("InterruptedException handling")
    class InterruptedExceptionHandling {

        @Test
        @DisplayName("Sleeper InterruptedException → IOException with cause, thread interrupted")
        void intercept_givenSleeperInterrupted_throwsIOExceptionAndSetsInterruptFlag() {
            InnerTubeRetryInterceptor.Sleeper interruptingSleeper = millis -> {
                throw new InterruptedException("test interrupt");
            };
            var chain = new ScriptedChain(List.of(respondWith(500), respondWith(200)));
            var interceptor = new InnerTubeRetryInterceptor(interruptingSleeper);

            assertThatThrownBy(() -> interceptor.intercept(chain))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Interrupted")
                    .hasCauseInstanceOf(InterruptedException.class);

            assertThat(Thread.currentThread().isInterrupted())
                    .as("Thread interrupt flag should be set")
                    .isTrue();

            // Clear interrupt flag to avoid polluting other tests
            Thread.interrupted();
        }
    }

    // ── Integration: InnerTubeClient.create() wiring ────────────────

    @Nested
    @DisplayName("Integration — InnerTubeClient.create() installs retry interceptor")
    class CreateIntegration {

        @Test
        @DisplayName("create() installs InnerTubeRetryInterceptor")
        void create_installsRetryInterceptor() throws Exception {
            InnerTubeClient client = InnerTubeClient.create();

            Field field = InnerTubeClient.class.getDeclaredField("httpClient");
            field.setAccessible(true);
            OkHttpClient httpClient = (OkHttpClient) field.get(client);

            assertThat(httpClient.interceptors())
                    .hasSize(1)
                    .first()
                    .isInstanceOf(InnerTubeRetryInterceptor.class);
        }

        @Test
        @DisplayName("create() retry interceptor uses default sleeper (Thread::sleep)")
        void create_retryInterceptorUsesDefaultSleeper() throws Exception {
            InnerTubeClient client = InnerTubeClient.create();

            Field httpField = InnerTubeClient.class.getDeclaredField("httpClient");
            httpField.setAccessible(true);
            OkHttpClient httpClient = (OkHttpClient) httpField.get(client);

            InnerTubeRetryInterceptor interceptor =
                    (InnerTubeRetryInterceptor) httpClient.interceptors().get(0);

            Field sleeperField = InnerTubeRetryInterceptor.class.getDeclaredField("sleeper");
            sleeperField.setAccessible(true);
            Object sleeper = sleeperField.get(interceptor);

            // Default constructor uses DEFAULT_SLEEPER (Thread::sleep)
            assertThat(sleeper).isNotNull();
        }
    }
}
