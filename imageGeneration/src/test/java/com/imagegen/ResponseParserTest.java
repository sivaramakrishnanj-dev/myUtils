package com.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The published docs never print a raw response body, so these cases pin the
 * documented shape plus the aliases and malformed shapes the parser must survive.
 */
class ResponseParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    void readsTheDocumentedShape() {
        JsonNode body = json("""
            {
              "id": "int_abc123",
              "steps": [
                {"type": "model_output", "content": [
                  {"type": "text", "text": "Here you go"},
                  {"type": "image", "mime_type": "image/png", "data": "QUJD"}
                ]}
              ],
              "usage": {"input_tokens": 12, "output_tokens": 34}
            }
            """);

        ResponseParser.Parsed parsed = ResponseParser.parse(body);

        assertEquals("int_abc123", parsed.interactionId());
        assertEquals(1, parsed.images().size());
        assertEquals("QUJD", parsed.images().get(0).base64());
        assertEquals("image/png", parsed.images().get(0).mimeType());
        assertEquals("Here you go", parsed.text());
        assertEquals(12, parsed.usage().path("input_tokens").asInt());
        assertFalse(parsed.usedFallbackScan());
    }

    @Test
    void unwrapsAnInteractionEnvelope() {
        JsonNode body = json("""
            {"interaction": {"id": "int_x", "steps": [
              {"type": "model_output", "content": [{"type": "image", "data": "QQ=="}]}
            ]}}
            """);
        ResponseParser.Parsed parsed = ResponseParser.parse(body);
        assertEquals("int_x", parsed.interactionId());
        assertEquals(1, parsed.images().size());
    }

    @Test
    void acceptsContentBlocksAlias() {
        JsonNode body = json("""
            {"id": "i", "steps": [
              {"type": "model_output", "content_blocks": [{"type": "image", "data": "QQ=="}]}
            ]}
            """);
        assertEquals(1, ResponseParser.parse(body).images().size());
    }

    @Test
    void countsThoughtImagesButNeverReturnsThemAsOutputs() {
        JsonNode body = json("""
            {"id": "i", "steps": [
              {"type": "thought", "summary": [
                 {"type": "text", "text": "sketching"},
                 {"type": "image", "data": "SKETCH1"},
                 {"type": "image", "data": "SKETCH2"}
              ]},
              {"type": "model_output", "content": [{"type": "image", "data": "FINAL"}]}
            ]}
            """);

        ResponseParser.Parsed parsed = ResponseParser.parse(body);

        assertEquals(2, parsed.thoughtImageCount());
        assertEquals(1, parsed.images().size());
        assertEquals("FINAL", parsed.images().get(0).base64());
    }

    @Test
    void collectsEveryImageWhenOutputIsInterleaved() {
        JsonNode body = json("""
            {"id": "i", "steps": [
              {"type": "model_output", "content": [
                {"type": "text", "text": "step one"},
                {"type": "image", "data": "ONE"},
                {"type": "text", "text": "step two"},
                {"type": "image", "data": "TWO"}
              ]}
            ]}
            """);

        ResponseParser.Parsed parsed = ResponseParser.parse(body);

        assertEquals(2, parsed.images().size());
        assertEquals("ONE", parsed.images().get(0).base64());
        assertEquals("TWO", parsed.images().get(1).base64());
        assertEquals("step one\nstep two", parsed.text());
    }

    @Test
    void fallsBackToAWholeTreeScanWhenTheShapeIsUnfamiliar() {
        JsonNode body = json("""
            {"id": "i", "candidates": [{"parts": [{"type": "image", "data": "DEEP"}]}]}
            """);

        ResponseParser.Parsed parsed = ResponseParser.parse(body);

        assertTrue(parsed.usedFallbackScan());
        assertEquals(1, parsed.images().size());
        assertEquals("DEEP", parsed.images().get(0).base64());
    }

    @Test
    void fallbackScanStillSkipsThoughtSubtrees() {
        JsonNode body = json("""
            {"id": "i", "trace": [{"type": "thought", "blocks": [{"type": "image", "data": "SKETCH"}]}]}
            """);

        ResponseParser.Parsed parsed = ResponseParser.parse(body);

        assertTrue(parsed.images().isEmpty());
    }

    @Test
    void reportsNoImagesRatherThanInventingOne() {
        JsonNode body = json("""
            {"id": "i", "steps": [
              {"type": "model_output", "content": [{"type": "text", "text": "I cannot do that"}]}
            ]}
            """);

        ResponseParser.Parsed parsed = ResponseParser.parse(body);

        assertTrue(parsed.images().isEmpty());
        assertEquals("I cannot do that", parsed.text());
    }

    @Test
    void ignoresImageBlocksWithNoData() {
        JsonNode body = json("""
            {"id": "i", "steps": [
              {"type": "model_output", "content": [{"type": "image", "data": ""}]}
            ]}
            """);
        assertTrue(ResponseParser.parse(body).images().isEmpty());
    }

    @Test
    void survivesAResponseWithNothingUseful() {
        ResponseParser.Parsed parsed = ResponseParser.parse(json("{}"));
        assertNull(parsed.interactionId());
        assertTrue(parsed.images().isEmpty());
        assertNull(parsed.text());
        assertNull(parsed.usage());
    }
}
