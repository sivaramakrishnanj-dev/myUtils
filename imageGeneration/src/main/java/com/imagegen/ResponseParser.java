package com.imagegen;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls images, text and metadata out of an Interactions API response.
 *
 * <p>Deliberately tolerant: the published docs describe the response only through
 * SDK accessors and never print a raw body, so field names are treated as likely
 * rather than certain. The parser prefers the documented shape
 * ({@code steps[] -> content[] -> data}), accepts known aliases, and falls back to
 * a whole-tree scan. Interim "thought" images are counted but never returned as
 * outputs - they are the model's scratch work, and the docs say they are not billed.
 */
public final class ResponseParser {

    /** One generated image. */
    public record Image(String base64, String mimeType) {
    }

    public record Parsed(String interactionId,
                         List<Image> images,
                         String text,
                         JsonNode usage,
                         int thoughtImageCount,
                         boolean usedFallbackScan,
                         List<String> thoughtSummaries) {
    }

    private ResponseParser() {
    }

    public static Parsed parse(JsonNode root) {
        JsonNode interaction = root.has("interaction") ? root.get("interaction") : root;

        String id = firstText(interaction, "id", "interaction_id", "interactionId");
        if (id == null) {
            id = firstText(root, "id", "interaction_id", "interactionId");
        }

        List<Image> images = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<String> thoughts = new ArrayList<>();
        int thoughtImages = 0;

        JsonNode steps = interaction.get("steps");
        if (steps != null && steps.isArray()) {
            for (JsonNode step : steps) {
                String type = step.path("type").asText("");
                JsonNode blocks = blocksOf(step);
                if ("thought".equals(type)) {
                    thoughtImages += countImages(blocks);
                    collectText(blocks, thoughts);
                } else if ("model_output".equals(type) || type.isEmpty()) {
                    collectImages(blocks, images);
                    collectText(blocks, texts);
                }
            }
        }

        boolean usedFallback = false;
        if (images.isEmpty()) {
            List<Image> scanned = new ArrayList<>();
            deepScanImages(root, scanned);
            if (!scanned.isEmpty()) {
                usedFallback = true;
                images.addAll(scanned);
                Log.warn("Response did not match the expected steps[] shape; recovered "
                        + scanned.size() + " image(s) by scanning the whole body. "
                        + "Use --debug-dump-response to capture it.");
            }
        }

        JsonNode usage = firstObject(interaction.get("usage"), root.get("usage"),
                interaction.get("usage_metadata"), root.get("usageMetadata"));

        String text = texts.isEmpty() ? null : String.join("\n", texts).trim();
        return new Parsed(id, images, text == null || text.isBlank() ? null : text,
                usage, thoughtImages, usedFallback, thoughts);
    }

    /** Content blocks of a step, under whichever of the known field names is present. */
    private static JsonNode blocksOf(JsonNode step) {
        for (String field : new String[]{"content", "content_blocks", "contentBlocks", "summary"}) {
            JsonNode candidate = step.get(field);
            if (candidate != null && candidate.isArray()) {
                return candidate;
            }
        }
        return null;
    }

    private static void collectImages(JsonNode blocks, List<Image> into) {
        if (blocks == null) {
            return;
        }
        for (JsonNode block : blocks) {
            if (isImageBlock(block)) {
                into.add(toImage(block));
            }
        }
    }

    private static void collectText(JsonNode blocks, List<String> into) {
        if (blocks == null) {
            return;
        }
        for (JsonNode block : blocks) {
            if ("text".equals(block.path("type").asText(""))) {
                String value = block.path("text").asText("");
                if (!value.isBlank()) {
                    into.add(value);
                }
            }
        }
    }

    private static int countImages(JsonNode blocks) {
        if (blocks == null) {
            return 0;
        }
        int count = 0;
        for (JsonNode block : blocks) {
            if (isImageBlock(block)) {
                count++;
            }
        }
        return count;
    }

    /** Whole-tree search, skipping any subtree marked as a thought step. */
    private static void deepScanImages(JsonNode node, List<Image> into) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            if ("thought".equals(node.path("type").asText(""))) {
                return;
            }
            if (isImageBlock(node)) {
                into.add(toImage(node));
                return;
            }
            node.fields().forEachRemaining(entry -> deepScanImages(entry.getValue(), into));
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                deepScanImages(child, into);
            }
        }
    }

    private static boolean isImageBlock(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        if (!"image".equals(node.path("type").asText(""))) {
            return false;
        }
        return !dataOf(node).isBlank();
    }

    private static Image toImage(JsonNode node) {
        String mime = firstText(node, "mime_type", "mimeType");
        return new Image(dataOf(node), mime);
    }

    private static String dataOf(JsonNode node) {
        for (String field : new String[]{"data", "base64", "b64_json", "bytes_base64"}) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private static JsonNode firstObject(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate != null && candidate.isObject() && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return null;
    }
}
