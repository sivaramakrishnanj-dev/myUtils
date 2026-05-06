package com.srk.myutils.yd.core;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * Fetches one caption track as XML from the {@code baseUrl} in the selected
 * {@code CaptionTrack}. Single HTTP GET with {@code NFR-CAPTION-DOWNLOAD-TIMEOUT = 10s}
 * total budget (AC-6.1).
 *
 * <p>The OkHttpClient is injected via the constructor for testability.
 * Production callers use {@link #create()} which builds a client with
 * 10s call/connect/read timeouts per NFR-CAPTION-DOWNLOAD-TIMEOUT.
 */
public final class CaptionDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaptionDownloader.class);

    private static final String USER_AGENT = HttpConstants.ANDROID_USER_AGENT;

    private final OkHttpClient httpClient;

    /**
     * @param httpClient the OkHttp client to use — inject for testing,
     *                   or use {@link #create()} for production defaults
     */
    public CaptionDownloader(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Factory that builds a {@code CaptionDownloader} with production-default
     * OkHttp configuration: 10s call/connect/read timeouts per
     * NFR-CAPTION-DOWNLOAD-TIMEOUT.
     */
    public static CaptionDownloader create() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .callTimeout(Duration.ofSeconds(10))
                .build();
        return new CaptionDownloader(client);
    }

    /**
     * Downloads the caption track XML from the given timedtext URL.
     *
     * @param timedtextUrl the {@code baseUrl} from the selected caption track
     * @return the raw XML string (may be empty if YouTube returns an empty body)
     * @throws NetworkException on IO failure or non-2xx HTTP status
     */
    public String download(String timedtextUrl) throws NetworkException {
        LOGGER.info("Caption download: url={}", timedtextUrl);

        Request request = new Request.Builder()
                .url(timedtextUrl)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int status = response.code();
            ResponseBody body = response.body();
            String bodyString = (body != null) ? body.string() : "";

            LOGGER.info("Caption response: status={} size={}", status, bodyString.length());

            if (status < 200 || status >= 300) {
                throw new NetworkException(
                        "GET " + timedtextUrl + " returned HTTP " + status);
            }

            return bodyString;
        } catch (IOException e) {
            throw new NetworkException(
                    "GET " + timedtextUrl + " failed", e);
        }
    }
}
