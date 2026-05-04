package com.srk.myutils.yd.core;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Set;

/**
 * Downloads a single media stream (video or audio) from a YouTube CDN URL
 * to a {@code .part} file, emitting byte-progress events via a callback.
 *
 * <p>Supports HTTP {@code Range} for cross-invocation resume: if the
 * {@code .part} file already exists with non-zero length from a previous
 * process run, the request includes {@code Range: bytes=<existing>-} and
 * the server responds with {@code 206 Partial Content}.
 *
 * <p>Within a single invocation, transient failures (IOException, HTTP 429,
 * HTTP 5xx) trigger up to {@link #MAX_RETRIES} retries with byte-0 restart
 * semantics per {@code NFR-STREAM-MAX-RETRIES} and {@code AC-12.4}. On
 * retry the file is truncated back to its size at invocation start,
 * preserving cross-run resume data while discarding partial current-run
 * bytes.
 *
 * <p>The OkHttpClient is constructor-injected for testability — same pattern
 * as {@link InnerTubeClient}. Production callers use {@link #create()}.
 *
 * @see <a href="design/04-apis.md">04-apis.md § 1.2</a>
 * @see <a href="design/06-formal/state-machine.md">INV-7, INV-15</a>
 */
public final class StreamDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamDownloader.class);

    /** Buffer size for streaming writes — 64 KB. */
    private static final int BUFFER_SIZE = 64 * 1024;

    /** Same User-Agent as InnerTube requests (04-apis.md § 1.2.1). */
    private static final String USER_AGENT =
            "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip";

    /** NFR-STREAM-MAX-RETRIES = 2 (1 initial + 2 retries = 3 total attempts). */
    static final int MAX_RETRIES = 2;

    /** Backoff base in ms — same 500 ms base as InnerTube retries. */
    static final long BACKOFF_BASE_MS = 500L;

    /** HTTP status codes that are retryable (same whitelist as AC-12.4). */
    private static final Set<Integer> RETRYABLE_STATUSES =
            Set.of(429, 500, 502, 503, 504);

    /**
     * Abstraction over {@link Thread#sleep(long)} for testability.
     * Same pattern as {@link InnerTubeRetryInterceptor.Sleeper}.
     */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final Sleeper DEFAULT_SLEEPER = Thread::sleep;

    /**
     * Callback for byte-progress events during a stream download.
     * Implementations must be safe for frequent invocation (once per 64 KB chunk).
     */
    public interface ProgressCallback {
        /**
         * @param bytesWritten total bytes written to the {@code .part} file so far
         * @param totalBytes   total expected bytes, or {@code -1} if unknown
         */
        void onProgress(long bytesWritten, long totalBytes);
    }

    /** A no-op callback for callers that do not need progress events. */
    public static final ProgressCallback NO_OP = (b, t) -> {};

    private final OkHttpClient httpClient;
    private final Sleeper sleeper;

    /**
     * @param httpClient the OkHttp client — inject for testing,
     *                   or use {@link #create()} for production defaults
     */
    public StreamDownloader(OkHttpClient httpClient) {
        this(httpClient, DEFAULT_SLEEPER);
    }

    StreamDownloader(OkHttpClient httpClient, Sleeper sleeper) {
        this.httpClient = httpClient;
        this.sleeper = sleeper;
    }

    /**
     * Factory that builds a {@code StreamDownloader} with production-default
     * OkHttp configuration: NFR-pinned timeouts, no call timeout (streams
     * can be large), idle-read timeout of 30 s.
     */
    public static StreamDownloader create() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))   // NFR-NETWORK-TIMEOUT-CONNECT
                .readTimeout(Duration.ofSeconds(30))      // NFR-NETWORK-TIMEOUT-READ
                .build();
        return new StreamDownloader(client);
    }

    /**
     * Downloads the stream at {@code url} to {@code partFile}, invoking
     * {@code callback} after each chunk write.
     *
     * <p>If {@code partFile} already exists with non-zero length (from a
     * previous process run), a {@code Range} header is sent to resume from
     * that byte offset.
     *
     * <p>On retryable failure (IOException, HTTP 429/5xx), the file is
     * truncated to its size at invocation start and the download restarts
     * from byte 0 (relative to the current invocation). Up to
     * {@link #MAX_RETRIES} retries with exponential backoff.
     *
     * @param url      fully-signed CDN URL from {@code Format.url()}
     * @param partFile destination {@code .part} file path
     * @param callback progress callback; use {@link #NO_OP} to ignore
     * @throws NetworkException on HTTP error or I/O failure after retries exhausted
     */
    public void download(String url, Path partFile, ProgressCallback callback) {
        long bytesAtStart = existingFileSize(partFile);
        NetworkException lastFailure = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                long delayMs = BACKOFF_BASE_MS * (1L << (attempt - 1));
                LOGGER.warn("Stream retry {}/{} after {}ms for {}",
                        attempt, MAX_RETRIES, delayMs, partFile.getFileName());
                try {
                    sleeper.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NetworkException(
                            "Interrupted during stream retry backoff for " + hostOf(url), e);
                }
                truncateToSize(partFile, bytesAtStart);
            }

            try {
                doDownload(url, partFile, bytesAtStart, callback);
                return; // success
            } catch (NetworkException e) {
                if (!isRetryable(e)) {
                    throw e;
                }
                lastFailure = e;
            }
        }

        // All retries exhausted — rethrow the last failure directly so
        // callers see the original message and cause chain unchanged.
        LOGGER.error("Stream download failed for {} after {} attempts",
                hostOf(url), MAX_RETRIES + 1);
        throw lastFailure;
    }

    /**
     * Executes a single download attempt.
     */
    private void doDownload(String url, Path partFile, long existingBytes,
                            ProgressCallback callback) {
        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT);

        if (existingBytes > 0) {
            reqBuilder.header("Range", "bytes=" + existingBytes + "-");
            LOGGER.info("Stream download resuming from byte {} for {}",
                    existingBytes, partFile.getFileName());
        }

        LOGGER.info("Stream download started: host={}", hostOf(url));

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            int code = response.code();
            if (code != 200 && code != 206) {
                throw new NetworkException(
                        "CDN GET " + hostOf(url) + " returned HTTP " + code);
            }

            long totalBytes = computeTotalBytes(response, existingBytes);
            ResponseBody body = response.body();
            if (body == null) {
                throw new NetworkException(
                        "CDN GET " + hostOf(url) + " returned empty body");
            }

            writeBody(body.byteStream(), partFile, existingBytes, totalBytes, callback);

            LOGGER.info("Stream download finished: file={} bytes={}",
                    partFile.getFileName(), existingFileSize(partFile));
        } catch (NetworkException e) {
            throw e;
        } catch (IOException e) {
            throw new NetworkException(
                    "Stream download failed for " + hostOf(url) + ": " + e.getMessage(), e);
        }
    }

    /**
     * Determines whether a {@link NetworkException} represents a retryable
     * failure. Retryable: IOException-caused failures and HTTP 429/5xx.
     * Not retryable: HTTP 403, 404, and other client errors.
     */
    private static boolean isRetryable(NetworkException e) {
        // IOException-wrapped failures are always retryable
        if (e.getCause() instanceof IOException) {
            return true;
        }
        // Check for retryable HTTP status codes in the message
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        for (int status : RETRYABLE_STATUSES) {
            if (msg.contains("HTTP " + status)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Truncates the file to the given size, or deletes and recreates if
     * {@code targetSize == 0}. Used on retry to discard partial current-run
     * bytes while preserving cross-invocation resume data.
     */
    private static void truncateToSize(Path file, long targetSize) {
        try {
            if (!Files.exists(file)) {
                return;
            }
            if (targetSize == 0) {
                Files.delete(file);
            } else {
                try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                    channel.truncate(targetSize);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to truncate {} to {} bytes before retry: {}",
                    file.getFileName(), targetSize, e.getMessage());
        }
    }

    /**
     * Streams bytes from {@code in} to {@code partFile}, appending if resuming.
     * Calls {@code callback} after each buffer write.
     */
    private void writeBody(InputStream in, Path partFile, long existingBytes,
                           long totalBytes, ProgressCallback callback) throws IOException {
        StandardOpenOption[] opts = existingBytes > 0
                ? new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.APPEND}
                : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                                           StandardOpenOption.TRUNCATE_EXISTING};

        try (OutputStream out = Files.newOutputStream(partFile, opts)) {
            byte[] buf = new byte[BUFFER_SIZE];
            long written = existingBytes;
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                written += read;
                callback.onProgress(written, totalBytes);
            }
        }
    }

    /**
     * Computes total bytes from Content-Length (200) or Content-Range (206).
     * Returns {@code -1} if unknown.
     */
    private long computeTotalBytes(Response response, long existingBytes) {
        if (response.code() == 206) {
            String contentRange = response.header("Content-Range");
            if (contentRange != null) {
                // Content-Range: bytes 1000-9999/10000
                int slashIdx = contentRange.lastIndexOf('/');
                if (slashIdx != -1) {
                    String totalStr = contentRange.substring(slashIdx + 1).trim();
                    if (!"*".equals(totalStr)) {
                        try {
                            return Long.parseLong(totalStr);
                        } catch (NumberFormatException ignored) {
                            // fall through to -1
                        }
                    }
                }
            }
        }

        // For 200 responses, Content-Length is the total
        String contentLength = response.header("Content-Length");
        if (contentLength != null) {
            try {
                return Long.parseLong(contentLength) + existingBytes;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }

        return -1;
    }

    private static long existingFileSize(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    private static String hostOf(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception e) {
            return url.length() > 60 ? url.substring(0, 60) + "..." : url;
        }
    }
}
