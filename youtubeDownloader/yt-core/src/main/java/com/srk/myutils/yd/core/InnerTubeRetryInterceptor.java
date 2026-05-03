package com.srk.myutils.yd.core;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

/**
 * OkHttp interceptor that retries InnerTube requests on transient failures
 * with exponential backoff per AC-12.4.
 *
 * <p>Retryable conditions (02-architecture.md § 4.1):
 * <ul>
 *   <li>{@link IOException} — connection refused, timeout, DNS failure</li>
 *   <li>HTTP 429, 500, 502, 503, 504</li>
 * </ul>
 *
 * <p>Backoff schedule: 500 ms, 1000 ms, 2000 ms (factor 2 from
 * {@code NFR-INNERTUBE-BACKOFF-BASE = 500 ms}). Total ≤ 3.5 s of retry
 * wait, bounded by {@code NFR-INNERTUBE-REQUEST-TIMEOUT = 30 s}.
 *
 * <p>A {@link Sleeper} is injectable for testability so tests avoid real
 * delays.
 */
final class InnerTubeRetryInterceptor implements Interceptor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(InnerTubeRetryInterceptor.class);

    static final int MAX_RETRIES = 3;
    static final long BACKOFF_BASE_MS = 500L;
    private static final Set<Integer> RETRYABLE_STATUSES =
            Set.of(429, 500, 502, 503, 504);

    /**
     * Abstraction over {@link Thread#sleep(long)} for testability.
     */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final Sleeper DEFAULT_SLEEPER = Thread::sleep;

    private final Sleeper sleeper;

    InnerTubeRetryInterceptor() {
        this(DEFAULT_SLEEPER);
    }

    InnerTubeRetryInterceptor(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                long delayMs = BACKOFF_BASE_MS * (1L << (attempt - 1));
                LOGGER.warn("InnerTube retry {}/{} after {}ms", attempt, MAX_RETRIES, delayMs);
                try {
                    sleeper.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during retry backoff", e);
                }
            }

            boolean lastAttempt = (attempt == MAX_RETRIES);

            try {
                Response response = chain.proceed(request);
                if (lastAttempt || !RETRYABLE_STATUSES.contains(response.code())) {
                    return response;
                }
                response.close();
            } catch (IOException e) {
                if (lastAttempt) {
                    throw e;
                }
            }
        }

        // Unreachable — the loop always returns or throws on the last attempt.
        throw new IOException("Retry loop exhausted without terminal state");
    }
}
