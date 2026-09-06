package com.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the outgoing request against the documented REST schema. */
class GeminiImageClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Config config(Path dir, String... extra) {
        String[] args = new String[extra.length + 5];
        args[0] = "generate";
        args[1] = "--config";
        args[2] = dir.resolve("config.json").toString();
        args[3] = "--api-key";
        args[4] = "test-key";
        System.arraycopy(extra, 0, args, 5, extra.length);
        return Config.resolve(Options.parse(args), null);
    }

    private static JsonNode payload(Config config, String prompt,
                                    List<GeminiImageClient.InputImage> images, String previousId) {
        try {
            return MAPPER.readTree(new GeminiImageClient(config).buildPayload(prompt, images, previousId));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void textOnlyRequestCarriesModelPromptAndResponseFormat(@TempDir Path dir) {
        JsonNode body = payload(config(dir), "a red bicycle", List.of(), null);

        assertEquals(Config.DEFAULT_MODEL, body.path("model").asText());
        assertEquals(1, body.path("input").size());
        assertEquals("text", body.path("input").get(0).path("type").asText());
        assertEquals("a red bicycle", body.path("input").get(0).path("text").asText());
        assertEquals("image", body.path("response_format").path("type").asText());
        assertEquals("1K", body.path("response_format").path("image_size").asText());
        assertEquals("image/png", body.path("response_format").path("mime_type").asText());
    }

    @Test
    void optionalFieldsAreOmittedRatherThanSentAsNull(@TempDir Path dir) {
        JsonNode body = payload(config(dir), "x", List.of(), null);
        assertFalse(body.path("response_format").has("aspect_ratio"));
        assertFalse(body.has("generation_config"));
        assertFalse(body.has("previous_interaction_id"));
    }

    @Test
    void aspectRatioAndThinkingLevelAppearWhereTheDocsPutThem(@TempDir Path dir) {
        JsonNode body = payload(config(dir, "--aspect-ratio", "16:9", "--thinking", "high", "--resolution", "4K"),
                "x", List.of(), null);
        assertEquals("16:9", body.path("response_format").path("aspect_ratio").asText());
        assertEquals("4K", body.path("response_format").path("image_size").asText());
        assertEquals("high", body.path("generation_config").path("thinking_level").asText());
    }

    @Test
    void inputImagesFollowThePromptAsImageBlocks(@TempDir Path dir) {
        JsonNode body = payload(config(dir), "make it stormy",
                List.of(new GeminiImageClient.InputImage("image/jpeg", "QUJD"),
                        new GeminiImageClient.InputImage("image/png", "REVG")),
                null);

        assertEquals(3, body.path("input").size());
        assertEquals("text", body.path("input").get(0).path("type").asText());

        JsonNode first = body.path("input").get(1);
        assertEquals("image", first.path("type").asText());
        assertEquals("image/jpeg", first.path("mime_type").asText());
        assertEquals("QUJD", first.path("data").asText());
        assertEquals("REVG", body.path("input").get(2).path("data").asText());
    }

    @Test
    void previousInteractionIdIsTopLevel(@TempDir Path dir) {
        JsonNode body = payload(config(dir), "now add rain", List.of(), "int_abc123");
        assertEquals("int_abc123", body.path("previous_interaction_id").asText());
        assertTrue(body.path("input").isArray());
    }
}
