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

    /** Raw API response plus the wall-clock time the call took. */
    public record Response(JsonNode body, long latencyMs) {
    }

    public Response create(String prompt, List<InputImage> images, String previousInteractionId) {
        String payload = buildPayload(prompt, images, previousInteractionId);
        long start = System.currentTimeMillis();

        int attempts = config.retries + 1;
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<String> response = send(payload);
                int status = response.statusCode();

                if (status == 200) {
                    return new Response(parseBody(response.body()), System.currentTimeMillis() - start);
                }

                CliException failure = toException(status, response.body());
                if (failure.exitCode() == ExitCode.API_RETRYABLE && attempt < attempts) {
                    backOff(attempt, "HTTP " + status);
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
                    backOff(attempt, e.getClass().getSimpleName());
                    lastFailure = failure;
                    continue;
                }
                throw failure;
            }
        }
        throw lastFailure != null ? lastFailure
                : new CliException(ExitCode.API_RETRYABLE, "API_TRANSPORT", "Request failed", "Retry.");
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
        responseFormat.put("mime_type", config.mimeType);
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
                    "Gemini rejected the request (400): " + detail,
                    "Check --model, --resolution and --aspect-ratio are valid for this model.");
            case 401, 403 -> new CliException(ExitCode.CONFIG, "API_UNAUTHORIZED",
                    "Gemini rejected the API key (" + status + "): " + detail,
                    "Verify the key in your config, or export a valid GEMINI_API_KEY.");
            case 404 -> new CliException(ExitCode.API_PERMANENT, "API_NOT_FOUND",
                    "Gemini returned 404: " + detail,
                    "The model id may be wrong or unavailable to your key. Run 'imagegen models'.");
            case 413 -> new CliException(ExitCode.API_PERMANENT, "API_PAYLOAD_TOO_LARGE",
                    "Request body too large (413): " + detail,
                    "Use fewer or smaller input images.");
            case 429 -> new CliException(ExitCode.API_RETRYABLE, "API_RATE_LIMITED",
                    "Rate limited by Gemini (429): " + detail,
                    "Wait and retry; raise --retries to back off automatically.");
            default -> status >= 500
                    ? new CliException(ExitCode.API_RETRYABLE, "API_SERVER_ERROR",
                            "Gemini server error (" + status + "): " + detail, "Transient - retry.")
                    : new CliException(ExitCode.API_PERMANENT, "API_ERROR",
                            "Gemini returned HTTP " + status + ": " + detail, "Inspect the message above.");
        };
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

    private void backOff(int attempt, String because) {
        long base = (long) Math.pow(2, attempt) * 1000L;
        long jitter = ThreadLocalRandom.current().nextLong(250, 750);
        long waitMs = Math.min(base + jitter, 30_000L);
        Log.warn("Attempt " + attempt + " failed (" + because + "); retrying in " + waitMs + "ms");
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
