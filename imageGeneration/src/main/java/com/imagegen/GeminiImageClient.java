package com.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the Gemini Interactions API over plain REST.
 *
 * <p>Endpoint and request schema per
 * https://ai.google.dev/gemini-api/docs/image-generation (REST tab).
 */
public final class GeminiImageClient {

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final Config config;

    public GeminiImageClient(Config config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** An input image already read into memory and base64-encoded. */
    public record InputImage(String mimeType, String base64) {
    }

    /**
     * Raw API response, the wall-clock time the call took, and the output MIME type
     * actually accepted - which may differ from the requested one if it was
     * auto-corrected.
     */
    public record Response(JsonNode body, long latencyMs, String mimeType) {
    }

    public Response create(String prompt, List<InputImage> images, String previousInteractionId) {
        String mimeType = config.mimeType;
        String payload = buildPayload(prompt, images, previousInteractionId, mimeType);
        long start = System.currentTimeMillis();

        int attempts = config.retries + 1;
        int attempt = 0;
        boolean mimeAlreadyCorrected = false;
        RuntimeException lastFailure = null;

        while (attempt < attempts) {
            attempt++;
            try {
                HttpResponse<String> response = send(payload);
                int status = response.statusCode();

                if (status == 200) {
                    return new Response(parseBody(response.body()),
                            System.currentTimeMillis() - start, mimeType);
                }

                // Models disagree on which output MIME types they accept, and the API
                // names the ones it will take. Take it at its word - once, and only if
                // the user did not ask for a specific type.
                if (status == 400 && !mimeAlreadyCorrected && !config.mimeTypeExplicit) {
                    String supported = supportedMimeFrom(extractErrorMessage(response.body()));
                    if (supported != null && !supported.equals(mimeType)) {
                        Log.warn(config.model + " does not accept " + mimeType + " output; retrying as "
                                + supported + ". Pass --mime to choose explicitly.");
                        mimeType = supported;
                        payload = buildPayload(prompt, images, previousInteractionId, mimeType);
                        mimeAlreadyCorrected = true;
                        attempt--;
                        continue;
                    }
                }

                CliException failure = toException(status, response.body());
                if (failure.exitCode() == ExitCode.API_RETRYABLE && attempt < attempts) {
                    backOff(attempt, "HTTP " + status,
                            suggestedRetryDelayMs(extractErrorMessage(response.body())));
                    lastFailure = failure;
                    continue;
                }
                throw failure;

            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                CliException failure = new CliException(ExitCode.API_RETRYABLE, "API_TRANSPORT",
                        "Request to Gemini failed: " + e.getMessage(),
                        "Network or timeout problem. Retry, or raise --timeout (current "
                                + config.timeoutSeconds + "s).", e);
                if (attempt < attempts) {
                    backOff(attempt, e.getClass().getSimpleName(), 0);
                    lastFailure = failure;
                    continue;
                }
                throw failure;
            }
        }
        throw lastFailure != null ? lastFailure
                : new CliException(ExitCode.API_RETRYABLE, "API_TRANSPORT", "Request failed", "Retry.");
    }

    /**
     * Extracts an output MIME type from a rejection like "The value 'image/png' is not
     * supported for 'response_format.mime_type'. Supported values: 'image/jpeg'."
     *
     * @return the first supported image type named, or {@code null} if the message is
     *         not about the output MIME type
     */
    static String supportedMimeFrom(String message) {
        if (message == null || !message.contains("mime_type")) {
            return null;
        }
        Matcher supported = Pattern.compile("Supported values:([^.]*)").matcher(message);
        if (!supported.find()) {
            return null;
        }
        Matcher quoted = Pattern.compile("'([^']+)'").matcher(supported.group(1));
        while (quoted.find()) {
            String candidate = quoted.group(1).trim();
            if (candidate.startsWith("image/")) {
                return candidate;
            }
        }
        return null;
    }

    private HttpResponse<String> send(String payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(config.timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", config.requireApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    String buildPayload(String prompt, List<InputImage> images, String previousInteractionId) {
        return buildPayload(prompt, images, previousInteractionId, config.mimeType);
    }

    String buildPayload(String prompt, List<InputImage> images, String previousInteractionId, String mimeType) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", config.model);

        ArrayNode input = root.putArray("input");
        if (prompt != null && !prompt.isBlank()) {
            ObjectNode text = input.addObject();
            text.put("type", "text");
            text.put("text", prompt);
        }
        for (InputImage image : images) {
            ObjectNode block = input.addObject();
            block.put("type", "image");
            block.put("mime_type", image.mimeType());
            block.put("data", image.base64());
        }

        ObjectNode responseFormat = root.putObject("response_format");
        responseFormat.put("type", "image");
        responseFormat.put("mime_type", mimeType);
        responseFormat.put("image_size", config.resolution);
        if (config.aspectRatio != null) {
            responseFormat.put("aspect_ratio", config.aspectRatio);
        }

        if (config.thinkingLevel != null) {
            root.putObject("generation_config").put("thinking_level", config.thinkingLevel);
        }
        if (previousInteractionId != null && !previousInteractionId.isBlank()) {
            root.put("previous_interaction_id", previousInteractionId);
        }

        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new CliException(ExitCode.IO, "ENCODE", "Cannot serialise the request", "This is a bug.", e);
        }
    }

    private static JsonNode parseBody(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new CliException(ExitCode.API_PERMANENT, "API_BAD_RESPONSE",
                    "Gemini returned a 200 that is not valid JSON",
                    "Rerun with --debug-dump-response <path> to capture it.", e);
        }
    }

    private static CliException toException(int status, String body) {
        String detail = extractErrorMessage(body);
        return switch (status) {
            case 400 -> new CliException(ExitCode.API_PERMANENT, "API_BAD_REQUEST",
                    "Gemini rejected the request (400): " + detail, hintFor400(detail));
            case 401, 403 -> new CliException(ExitCode.CONFIG, "API_UNAUTHORIZED",
                    "Gemini rejected the API key (" + status + "): " + detail,
                    "Verify the key in your config, or export a valid GEMINI_API_KEY.");
            case 404 -> new CliException(ExitCode.API_PERMANENT, "API_NOT_FOUND",
                    "Gemini returned 404: " + detail,
                    "The model id may be wrong or unavailable to your key. Run 'imagegen models'.");
            case 413 -> new CliException(ExitCode.API_PERMANENT, "API_PAYLOAD_TOO_LARGE",
                    "Request body too large (413): " + detail,
                    "Use fewer or smaller input images.");
            // "limit: 0" is not congestion - the plan has no allowance for this model at
            // all, so retrying can never succeed. Image models are not on the free tier.
            case 429 -> hasZeroQuota(detail)
                    ? new CliException(ExitCode.CONFIG, "API_QUOTA_NOT_ENABLED",
                            "Your plan has no quota for this model (429, limit: 0): " + detail,
                            "Image models are not available on the Gemini free tier. Enable billing "
                                    + "on the Google Cloud project behind this API key, then retry. "
                                    + "Retrying as-is will keep failing.")
                    : new CliException(ExitCode.API_RETRYABLE, "API_RATE_LIMITED",
                            "Rate limited by Gemini (429): " + detail,
                            "Wait and retry; raise --retries to back off automatically.");
            default -> status >= 500
                    ? new CliException(ExitCode.API_RETRYABLE, "API_SERVER_ERROR",
                            "Gemini server error (" + status + "): " + detail, "Transient - retry.")
                    : new CliException(ExitCode.API_PERMANENT, "API_ERROR",
                            "Gemini returned HTTP " + status + ": " + detail, "Inspect the message above.");
        };
    }

    /** True when a 429 reports a quota limit of zero rather than exhausted capacity. */
    static boolean hasZeroQuota(String detail) {
        return detail != null && Pattern.compile("limit:\\s*0(?![0-9.])").matcher(detail).find();
    }

    /**
     * The delay the API itself asks for, from a message like "Please retry in 17.85s".
     *
     * @return milliseconds to wait, or 0 if the message does not say
     */
    static long suggestedRetryDelayMs(String detail) {
        if (detail == null) {
            return 0;
        }
        Matcher matcher = Pattern.compile("retry in ([0-9]+(?:\\.[0-9]+)?)s").matcher(detail);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Math.round(Double.parseDouble(matcher.group(1)) * 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Turns the API's own complaint into the flag the caller should change. */
    private static String hintFor400(String detail) {
        String supportedMime = supportedMimeFrom(detail);
        if (supportedMime != null) {
            return "This model only accepts " + supportedMime + " output. Pass --mime " + supportedMime
                    + ", or drop --mime and it will be chosen automatically.";
        }
        if (detail.contains("image_size")) {
            return "This model does not support that --resolution. Try 1K, or run 'imagegen models'.";
        }
        if (detail.contains("aspect_ratio")) {
            return "This model does not support that --aspect-ratio. Omit it to let the model choose.";
        }
        if (detail.contains("thinking_level")) {
            return "This model does not support --thinking. Drop the flag, or use gemini-3.1-flash-image.";
        }
        if (detail.contains("Supported values")) {
            return "The message above names the accepted values - set the matching flag to one of them.";
        }
        return "Check --model, --resolution, --aspect-ratio and --mime are valid for this model.";
    }

    private static String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "<empty response body>";
        }
        try {
            JsonNode error = MAPPER.readTree(body).path("error");
            String message = error.path("message").asText(null);
            if (message != null && !message.isBlank()) {
                String status = error.path("status").asText(null);
                return status != null && !status.isBlank() ? message + " [" + status + "]" : message;
            }
        } catch (Exception ignored) {
            // Not JSON - fall through and return a truncated raw body.
        }
        return body.length() > 400 ? body.substring(0, 400) + "..." : body;
    }

    /**
     * @param suggestedMs delay the API asked for, or 0. When present it wins - guessing
     *                    shorter than the server's own figure just burns an attempt.
     */
    private void backOff(int attempt, String because, long suggestedMs) {
        long base = (long) Math.pow(2, attempt) * 1000L;
        long jitter = ThreadLocalRandom.current().nextLong(250, 750);
        long waitMs = suggestedMs > 0
                ? Math.min(suggestedMs + jitter, 60_000L)
                : Math.min(base + jitter, 30_000L);
        Log.warn("Attempt " + attempt + " failed (" + because + "); retrying in " + waitMs + "ms"
                + (suggestedMs > 0 ? " (server asked for " + suggestedMs + "ms)" : ""));
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
