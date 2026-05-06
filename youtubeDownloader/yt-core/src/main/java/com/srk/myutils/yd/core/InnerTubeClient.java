package com.srk.myutils.yd.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * Issues a single POST to YouTube's InnerTube {@code /youtubei/v1/player}
 * endpoint using the ANDROID client context (ADR-0001).
 *
 * <p>Constructs the request body per AC-12.1 and
 * {@code 06-formal/innertube-player-request.schema.json}. Sets headers per
 * AC-12.2 and {@code 04-apis.md} § 1.1.1. Returns the raw HTTP status and
 * body as an {@link InnerTubeResponse} — parsing into domain types is
 * T-1.7's job.
 *
 * <p>The OkHttpClient is injected via the constructor for testability.
 * Production callers use {@link #create()} which builds a client with
 * NFR-pinned timeouts and the exponential-backoff retry interceptor
 * per AC-12.4.
 *
 * <p>Exactly one POST per {@link #fetchPlayer(VideoId)} call (AC-12.3,
 * INV-9).
 */
public final class InnerTubeClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(InnerTubeClient.class);

    private static final String PLAYER_ENDPOINT =
            "https://www.youtube.com/youtubei/v1/player";

    private static final MediaType JSON = MediaType.get("application/json");

    // AC-12.1 / NFR-ANDROID-* constants
    private static final String CLIENT_NAME = "ANDROID";
    private static final String CLIENT_VERSION = "21.02.35";       // NFR-ANDROID-CLIENT-VERSION
    private static final int ANDROID_SDK_VERSION = 30;             // NFR-ANDROID-SDK-VERSION
    private static final String HL = "en";                         // NFR-INNERTUBE-HL
    private static final String GL = "US";                         // NFR-INNERTUBE-GL
    private static final String OS_NAME = "Android";
    private static final String OS_VERSION = "11";
    private static final String PLATFORM = "MOBILE";

    // AC-12.2 / NFR-ANDROID-USER-AGENT
    private static final String USER_AGENT = HttpConstants.ANDROID_USER_AGENT;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient httpClient;

    /**
     * @param httpClient the OkHttp client to use — inject for testing,
     *                   or use {@link #create()} for production defaults
     */
    public InnerTubeClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Factory that builds an {@code InnerTubeClient} with production-default
     * OkHttp configuration: NFR-pinned timeouts and the exponential-backoff
     * retry interceptor per AC-12.4.
     */
    public static InnerTubeClient create() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))  // NFR-NETWORK-TIMEOUT-CONNECT
                .readTimeout(Duration.ofSeconds(30))     // NFR-NETWORK-TIMEOUT-READ
                .callTimeout(Duration.ofSeconds(30))     // NFR-INNERTUBE-REQUEST-TIMEOUT
                .addInterceptor(new InnerTubeRetryInterceptor())  // AC-12.4
                .build();
        return new InnerTubeClient(client);
    }

    /**
     * Issues one POST to InnerTube {@code /player} for the given video.
     *
     * @param videoId validated video identifier
     * @return raw response (HTTP status + body string)
     * @throws NetworkException on network error or non-2xx HTTP status
     */
    public InnerTubeResponse fetchPlayer(VideoId videoId) {
        String jsonBody = buildRequestBody(videoId);

        LOGGER.info("InnerTube request: videoId={} client={}", videoId.value(), CLIENT_NAME);
        LOGGER.debug("InnerTube request body: {}", jsonBody);

        Request request = new Request.Builder()
                .url(PLAYER_ENDPOINT)
                .post(RequestBody.create(jsonBody, JSON))
                .header("User-Agent", USER_AGENT)
                .header("X-YouTube-Client-Name", "3")
                .header("X-YouTube-Client-Version", CLIENT_VERSION)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String bodyString = (body != null) ? body.string() : "";

            LOGGER.info("InnerTube response: status={} size={}", response.code(), bodyString.length());

            return new InnerTubeResponse(response.code(), bodyString);
        } catch (IOException e) {
            LOGGER.debug("InnerTube request failed for videoId={}: {}", videoId.value(), e.getMessage());
            throw new NetworkException(
                    "POST " + PLAYER_ENDPOINT + " failed for videoId=" + videoId.value(), e);
        }
    }

    /**
     * Builds the JSON request body per AC-12.1 and
     * {@code 06-formal/innertube-player-request.schema.json}.
     */
    static String buildRequestBody(VideoId videoId) {
        ObjectNode clientNode = JsonNodeFactory.instance.objectNode()
                .put("clientName", CLIENT_NAME)
                .put("clientVersion", CLIENT_VERSION)
                .put("androidSdkVersion", ANDROID_SDK_VERSION)
                .put("hl", HL)
                .put("gl", GL)
                .put("osName", OS_NAME)
                .put("osVersion", OS_VERSION)
                .put("platform", PLATFORM);

        ObjectNode contextNode = JsonNodeFactory.instance.objectNode();
        contextNode.set("client", clientNode);

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("videoId", videoId.value());
        root.set("context", contextNode);

        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // Should never happen — we're building a simple tree of literals
            throw new NetworkException("Failed to serialize InnerTube request body", e);
        }
    }
}
