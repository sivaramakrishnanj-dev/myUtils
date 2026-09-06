package com.imagegen;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Renders a result document as plain lines, for {@code --format text}. */
public final class TextRenderer {

    private TextRenderer() {
    }

    public static String render(JsonNode result) {
        StringBuilder out = new StringBuilder();
        String command = result.path("command").asText("");

        if (result.path("dryRun").asBoolean(false)) {
            out.append("DRY RUN - nothing sent, nothing written\n");
            out.append("  out dir : ").append(result.path("outDir").asText("")).append('\n');
            for (JsonNode planned : result.path("plannedOutputs")) {
                out.append("  would write : ").append(planned.asText()).append('\n');
            }
        } else if (result.has("outputs")) {
            for (JsonNode output : result.path("outputs")) {
                out.append(output.path("path").asText()).append('\n');
                out.append("  ").append(ImageGenCli.humanBytes(output.path("bytes").asLong()));
                if (output.has("width")) {
                    out.append("  ").append(output.path("width").asInt())
                            .append('x').append(output.path("height").asInt());
                }
                out.append("  ").append(result.path("resolution").asText())
                        .append("  ").append(result.path("model").asText()).append('\n');
            }
        }

        if ("config".equals(command)) {
            for (Map.Entry<String, JsonNode> field : result.properties()) {
                if (!field.getKey().equals("status")) {
                    out.append(String.format("%-14s %s%n", field.getKey(),
                            field.getValue().isNull() ? "-" : field.getValue().asText()));
                }
            }
        }

        if (result.has("text")) {
            out.append("model said: ").append(result.path("text").asText()).append('\n');
        }
        if (result.has("latencyMs")) {
            out.append(String.format("%.1fs", result.path("latencyMs").asLong() / 1000.0));
            JsonNode usage = result.get("usage");
            if (usage != null && !usage.isNull()) {
                out.append("  usage: ").append(usage.toString());
            }
            out.append('\n');
        }
        return out.toString().stripTrailing();
    }
}
