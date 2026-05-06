package com.srk.myutils.yd.core;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Fetches the highest-resolution thumbnail from
 * {@code videoDetails.thumbnail.thumbnails[]}. Single HTTP GET with
 * NFR-THUMBNAIL-DOWNLOAD-TIMEOUT = 10s. Writes directly to {@code <base>.jpg}.
 */
public final class ThumbnailDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailDownloader.class);

    private static final String USER_AGENT =
            "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip";

    private final OkHttpClient httpClient;

    public ThumbnailDownloader(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Factory with production-default OkHttp configuration:
     * 10s call/connect/read timeouts per NFR-THUMBNAIL-DOWNLOAD-TIMEOUT.
     */
    public static ThumbnailDownloader create() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .callTimeout(Duration.ofSeconds(10))
                .build();
        return new ThumbnailDownloader(client);
    }

    /**
     * Pick the highest-resolution thumbnail and download to {@code outputPath}.
     *
     * @param thumbnails list of available thumbnails from the player response
     * @param outputPath destination file path (e.g. {@code /tmp/video.jpg})
     * @throws NetworkException if the list is empty, HTTP fails, or IO fails
     */
    public void download(List<ThumbnailUrl> thumbnails, Path outputPath) throws NetworkException {
        ThumbnailUrl best = pickBest(thumbnails);

        LOGGER.info("Thumbnail download: url={} size={}x{}", best.url(), best.width(), best.height());

        Request request = new Request.Builder()
                .url(best.url())
                .get()
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int status = response.code();
            if (status < 200 || status >= 300) {
                throw new NetworkException(
                        "GET " + best.url() + " returned HTTP " + status);
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new NetworkException(
                        "GET " + best.url() + " returned empty body");
            }

            try (OutputStream out = Files.newOutputStream(outputPath)) {
                body.byteStream().transferTo(out);
            }

            LOGGER.info("Thumbnail written: path={} bytes={}", outputPath, Files.size(outputPath));
        } catch (IOException e) {
            throw new NetworkException("GET " + best.url() + " failed", e);
        }
    }

    /**
     * Selects the thumbnail with the largest area (width × height).
     */
    static ThumbnailUrl pickBest(List<ThumbnailUrl> thumbnails) throws NetworkException {
        if (thumbnails == null || thumbnails.isEmpty()) {
            throw new NetworkException("No thumbnails available");
        }
        return thumbnails.stream()
                .max(Comparator.comparingLong(t -> (long) t.width() * t.height()))
                .orElseThrow(); // unreachable — list is non-empty
    }
}
