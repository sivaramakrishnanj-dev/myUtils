package com.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Entry point. Generates and edits images through the Gemini Interactions API.
 *
 * <p>stdout carries the result document only; every log line goes to stderr, so an
 * automated caller can parse stdout without filtering.
 */
public final class ImageGenCli {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        String format = "json";
        try {
            Options options = Options.parse(args);
            format = options.format;
            Log.setQuiet(options.quiet);
            if (!format.equals("json") && !format.equals("text")) {
                throw CliException.usage("--format must be 'json' or 'text', got '" + format + "'",
                        "Use --format json (default) or --format text.");
            }
            System.exit(new ImageGenCli().run(options).code());
        } catch (CliException e) {
            emitError(e.code(), e.getMessage(), e.hint(), e.exitCode(), format);
            System.exit(e.exitCode().code());
        } catch (Exception e) {
            emitError("UNEXPECTED", e.getClass().getSimpleName() + ": " + e.getMessage(),
                    "This is a bug in imagegen. Rerun with --debug-dump-response to capture detail.",
                    ExitCode.IO, format);
            System.exit(ExitCode.IO.code());
        }
    }

    private ExitCode run(Options options) {
        if (options.help || "help".equals(options.command)) {
            System.out.println(options.agentHelp ? Help.agentContract() : Help.usage());
            return ExitCode.OK;
        }
        return switch (options.command) {
            case "models" -> {
                System.out.println(Help.models());
                yield ExitCode.OK;
            }
            case "config" -> configCommand(options);
            case "generate", "edit" -> imageCommand(options);
            default -> throw CliException.usage("Unknown command '" + options.command + "'",
                    "Valid commands: generate, edit, models, config, help.");
        };
    }

    // ---------------------------------------------------------------- config

    private ExitCode configCommand(Options options) {
        Path path = options.configPath != null ? options.configPath : Config.defaultConfigPath();
        if (options.init) {
            Config.init(path);
            Log.info("Wrote config template to " + path + " (mode 600)");
            ObjectNode result = MAPPER.createObjectNode();
            result.put("status", "ok");
            result.put("command", "config");
            result.put("action", "init");
            result.put("configPath", path.toString());
            result.put("next", "Put your Gemini API key in the apiKey field of " + path);
            emit(result, options.format);
            return ExitCode.OK;
        }

        Config config = Config.resolve(options, null);
        ObjectNode result = MAPPER.createObjectNode();
        result.put("status", "ok");
        result.put("command", "config");
        result.put("action", "show");
        result.put("configPath", config.configPath.toString());
        result.put("configExists", config.configPresent);
        result.put("apiKeyPresent", config.apiKey != null);
        result.put("apiKeySource", config.apiKeySource);
        result.put("apiKey", Config.redact(config.apiKey));
        result.put("model", config.model);
        result.put("resolution", config.resolution);
        result.put("mimeType", config.mimeType);
        result.put("aspectRatio", config.aspectRatio);
        result.put("thinkingLevel", config.thinkingLevel);
        result.put("outDir", config.outDir == null ? null : config.outDir.toString());
        result.put("timeoutSeconds", config.timeoutSeconds);
        result.put("retries", config.retries);
        emit(result, options.format);
        return ExitCode.OK;
    }

    // ----------------------------------------------------------- generate/edit

    private ExitCode imageCommand(Options options) {
        boolean editing = "edit".equals(options.command);

        JsonNode sidecar = options.continueFrom != null ? OutputWriter.readSidecar(options.continueFrom) : null;
        Config config = Config.resolve(options, sidecar);
        config.requireApiKey();

        if (options.prompt == null || options.prompt.isBlank()) {
            throw CliException.usage("--prompt is required for " + options.command,
                    "Pass -p \"<what you want>\", or --prompt-file <path> for long prompts.");
        }
        if (editing && options.images.isEmpty() && options.continueFrom == null) {
            throw CliException.usage("edit needs at least one --image (or --continue-from)",
                    "Example: imagegen edit -p \"make the sky stormy\" -i photo.jpg");
        }
        if (!editing && !options.images.isEmpty()) {
            throw CliException.usage("generate does not take --image",
                    "Use 'imagegen edit' when you have input images.");
        }

        int count = options.count == null ? 1 : options.count;
        if (count < 1 || count > Config.MAX_COUNT) {
            throw CliException.usage("--count must be between 1 and " + Config.MAX_COUNT,
                    "Each image is a separate API call, so this is capped to limit spend.");
        }

        Path outDir = resolveOutDir(options, sidecar, config);
        String base = resolveBaseName(options, sidecar);
        String previousInteractionId = sidecar == null ? null : sidecar.path("interactionId").asText(null);

        if (options.dryRun) {
            return dryRun(options, config, outDir, base, count, previousInteractionId);
        }

        List<Path> sourcePaths = new ArrayList<>();
        List<GeminiImageClient.InputImage> inputs = new ArrayList<>();
        for (Path image : options.images) {
            sourcePaths.add(image.toAbsolutePath().normalize());
            inputs.add(readImage(image));
        }

        GeminiImageClient client = new GeminiImageClient(config);
        ArrayNode outputs = MAPPER.createArrayNode();
        String interactionId = null;
        String modelText = null;
        JsonNode usage = null;
        int thoughtImages = 0;
        long totalLatency = 0;
        String effectiveMimeType = config.mimeType;

        for (int call = 1; call <= count; call++) {
            Log.info("Calling " + config.model + " (" + config.resolution + ")"
                    + (count > 1 ? " - image " + call + " of " + count : ""));

            GeminiImageClient.Response response = client.create(options.prompt, inputs, previousInteractionId);
            totalLatency += response.latencyMs();
            effectiveMimeType = response.mimeType();
            dumpIfRequested(options, response.body(), call, count);

            ResponseParser.Parsed parsed = ResponseParser.parse(response.body());
            thoughtImages += parsed.thoughtImageCount();
            if (parsed.usage() != null) {
                usage = parsed.usage();
            }
            if (parsed.interactionId() != null) {
                interactionId = parsed.interactionId();
            }
            if (parsed.text() != null) {
                modelText = parsed.text();
            }

            if (parsed.images().isEmpty()) {
                throw new CliException(ExitCode.API_PERMANENT, "NO_IMAGE_RETURNED",
                        "The model returned no image"
                                + (parsed.text() != null ? ". It said: " + parsed.text() : "."),
                        "Often a safety block or a prompt the model declined. Reword the prompt, "
                                + "or rerun with --debug-dump-response <path> to inspect the response.");
            }

            for (ResponseParser.Image image : parsed.images()) {
                byte[] bytes = decode(image.base64());
                String mimeType = image.mimeType() != null ? image.mimeType() : effectiveMimeType;
                ObjectNode metadata = OutputWriter.metadata(config, options.command, options.prompt,
                        parsed.interactionId(), sourcePaths, mimeType);
                OutputWriter.Written written = OutputWriter.write(outDir, base, mimeType, bytes, metadata);
                Log.info("Wrote " + written.image() + " (" + humanBytes(written.bytes()) + ")");

                ObjectNode entry = outputs.addObject();
                entry.put("path", written.image().toString());
                entry.put("bytes", written.bytes());
                entry.put("seq", written.seq());
                entry.put("sidecar", written.sidecar().toString());
                entry.put("mimeType", mimeType);
                ImageInfo.Dimensions dimensions = ImageInfo.of(bytes);
                if (dimensions != null) {
                    entry.put("width", dimensions.width());
                    entry.put("height", dimensions.height());
                }
                if (options.emitBase64) {
                    entry.put("base64", image.base64());
                }
            }
        }

        ObjectNode result = MAPPER.createObjectNode();
        result.put("status", "ok");
        result.put("command", options.command);
        result.put("model", config.model);
        result.put("resolution", config.resolution);
        if (config.aspectRatio != null) {
            result.put("aspectRatio", config.aspectRatio);
        }
        result.put("mimeType", effectiveMimeType);
        if (!effectiveMimeType.equals(config.mimeType)) {
            result.put("mimeTypeRequested", config.mimeType);
            result.put("mimeTypeAutoCorrected", true);
        }
        if (config.thinkingLevel != null) {
            result.put("thinkingLevel", config.thinkingLevel);
        }
        result.put("prompt", options.prompt);
        if (!sourcePaths.isEmpty()) {
            ArrayNode sources = result.putArray("sourceImages");
            sourcePaths.forEach(p -> sources.add(p.toString()));
        }
        if (previousInteractionId != null) {
            result.put("previousInteractionId", previousInteractionId);
        }
        result.set("outputs", outputs);
        if (interactionId != null) {
            result.put("interactionId", interactionId);
        }
        if (modelText != null) {
            result.put("text", modelText);
        }
        if (usage != null) {
            result.set("usage", usage);
        }
        result.put("thoughtImages", thoughtImages);
        result.put("latencyMs", totalLatency);
        result.put("dryRun", false);
        emit(result, options.format);
        return ExitCode.OK;
    }

    private ExitCode dryRun(Options options, Config config, Path outDir, String base, int count,
                            String previousInteractionId) {
        for (Path image : options.images) {
            if (!Files.isRegularFile(image)) {
                throw CliException.usage("Input image not found: " + image,
                        "Check the path. Pass --image once per input file.");
            }
            Mime.ofInput(image);
        }
        Path target = OutputWriter.ensureDirectory(outDir);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("status", "ok");
        result.put("command", options.command);
        result.put("dryRun", true);
        result.put("model", config.model);
        result.put("resolution", config.resolution);
        if (config.aspectRatio != null) {
            result.put("aspectRatio", config.aspectRatio);
        }
        result.put("mimeType", config.mimeType);
        if (config.thinkingLevel != null) {
            result.put("thinkingLevel", config.thinkingLevel);
        }
        result.put("prompt", options.prompt);
        result.put("apiKeySource", config.apiKeySource);
        result.put("outDir", target.toString());
        if (previousInteractionId != null) {
            result.put("previousInteractionId", previousInteractionId);
        }
        if (!options.images.isEmpty()) {
            ArrayNode sources = result.putArray("sourceImages");
            options.images.forEach(p -> sources.add(p.toAbsolutePath().normalize().toString()));
        }
        ArrayNode planned = result.putArray("plannedOutputs");
        for (int i = 0; i < count; i++) {
            planned.add(OutputWriter.preview(target, base, config.mimeType, i).toString());
        }
        result.put("note", "No API call was made and nothing was written.");
        emit(result, options.format);
        Log.info("Dry run OK - would write " + count + " image(s) to " + target);
        return ExitCode.OK;
    }

    // --------------------------------------------------------------- helpers

    /**
     * generate defaults to the working directory; edit defaults to the folder holding
     * the first input (or the image being continued from).
     */
    private Path resolveOutDir(Options options, JsonNode sidecar, Config config) {
        if (options.outDir != null) {
            return options.outDir;
        }
        if (!options.images.isEmpty()) {
            Path parent = options.images.get(0).toAbsolutePath().normalize().getParent();
            return parent != null ? parent : Path.of(".");
        }
        if (sidecar != null) {
            String previous = sidecar.path("image").asText(null);
            if (previous != null) {
                Path parent = Path.of(previous).toAbsolutePath().normalize().getParent();
                if (parent != null) {
                    return parent;
                }
            }
        }
        return config.outDir != null ? config.outDir : Path.of(".");
    }

    private String resolveBaseName(Options options, JsonNode sidecar) {
        if (!options.images.isEmpty()) {
            return OutputWriter.baseNameOf(options.images.get(0));
        }
        if (options.continueFrom != null) {
            return OutputWriter.baseNameOf(options.continueFrom);
        }
        return OutputWriter.slug(options.prompt);
    }

    private GeminiImageClient.InputImage readImage(Path path) {
        if (!Files.isRegularFile(path)) {
            throw CliException.usage("Input image not found: " + path,
                    "Check the path. Pass --image once per input file.");
        }
        String mimeType = Mime.ofInput(path);
        try {
            byte[] bytes = Files.readAllBytes(path);
            Log.info("Read " + path.getFileName() + " (" + humanBytes(bytes.length) + ", " + mimeType + ")");
            return new GeminiImageClient.InputImage(mimeType, Base64.getEncoder().encodeToString(bytes));
        } catch (Exception e) {
            throw CliException.io("Cannot read " + path, "Check the file is readable.", e);
        }
    }

    private static byte[] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            // Some encoders emit line-wrapped or URL-safe base64; try the lenient paths.
            try {
                return Base64.getMimeDecoder().decode(base64);
            } catch (IllegalArgumentException ignored) {
                throw new CliException(ExitCode.API_PERMANENT, "BAD_IMAGE_DATA",
                        "The image data returned by Gemini is not valid base64",
                        "Rerun with --debug-dump-response <path> and inspect the payload.", e);
            }
        }
    }

    private void dumpIfRequested(Options options, JsonNode body, int call, int total) {
        if (options.debugDumpResponse == null) {
            return;
        }
        Path path = options.debugDumpResponse;
        if (total > 1) {
            String name = path.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            Path parent = path.toAbsolutePath().getParent();
            path = parent.resolve(stem + "_" + call + ext);
        }
        try {
            Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body),
                    StandardCharsets.UTF_8);
            Log.info("Dumped raw response to " + path.toAbsolutePath());
        } catch (Exception e) {
            Log.warn("Could not write --debug-dump-response to " + path + ": " + e.getMessage());
        }
    }

    private static void emit(ObjectNode result, String format) {
        if ("text".equals(format)) {
            System.out.println(TextRenderer.render(result));
            return;
        }
        try {
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (Exception e) {
            throw new CliException(ExitCode.IO, "ENCODE", "Cannot serialise the result", "This is a bug.", e);
        }
    }

    private static void emitError(String code, String message, String hint, ExitCode exitCode, String format) {
        Log.error(message + (hint != null ? " | " + hint : ""));
        ObjectNode error = MAPPER.createObjectNode();
        error.put("status", "error");
        error.put("code", code);
        error.put("message", message);
        error.put("hint", hint);
        error.put("exitCode", exitCode.code());
        if ("text".equals(format)) {
            System.out.println("ERROR [" + code + "] " + message + (hint != null ? "\nHint: " + hint : ""));
            return;
        }
        try {
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(error));
        } catch (Exception ignored) {
            System.out.println("{\"status\":\"error\",\"code\":\"" + code + "\"}");
        }
    }

    static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
