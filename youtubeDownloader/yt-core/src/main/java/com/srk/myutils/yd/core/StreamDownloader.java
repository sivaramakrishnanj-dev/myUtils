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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Downloads a single media stream (video or audio) from a YouTube CDN URL
 * to a {@code .part} file, emitting byte-progress events via a callback.
 *
 * <p>Supports HTTP {@code Range} for same-run resume: if the {@code .part}
 * file already exists with non-zero length, the request includes
 * {@code Range: bytes=<existing>-} and the server responds with
 * {@code 206 Partial Content}.
 *
 * <p>The OkHttpClient is constructor-injected for testability — same pattern
 * as {@link InnerTubeClient}. Production callers use {@link #create()}.
 *
 * @see <a href="design/04-apis.md">04-apis.md § 1.2</a>
 * @see <a href="design/06-formal/state-machine.md">INV-7</a>
 */
public final class StreamDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamDownloader.class);

    /** Buffer size for streaming writes — 64 KB. */
    private static final int BUFFER_SIZE = 64 * 1024;

    /** Same User-Agent as InnerTube requests (04-apis.md § 1.2.1). */
    private static final String USER_AGENT =
            "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip";

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

    /**
     * @param httpClient the OkHttp client — inject for testing,
     *                   or use {@link #create()} for production defaults
     */
    public StreamDownloader(OkHttpClient httpClient) {
        this.httpClient = httpClient;
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
     * <p>If {@code partFile} already exists with non-zero length, a
     * {@code Range} header is sent to resume from that byte offset.
     *
     * @param url      fully-signed CDN URL from {@code Format.url()}
     * @param partFile destination {@code .part} file path
     * @param callback progress callback; use {@link #NO_OP} to ignore
     * @throws NetworkException on HTTP error or I/O failure
     */
    public void download(String url, Path partFile, ProgressCallback callback) {
        long existingBytes = existingFileSize(partFile);

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT);

        if (existingBytes > 0) {
            reqBuilder.header("Range", "bytes=" + existingBytes + "-");
            LOGGER.info("Stream download resuming from byte {} for {}", existingBytes, partFile.getFileName());
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
                throw new NetworkException("CDN GET " + hostOf(url) + " returned empty body");
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
