package com.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

/**
 * Effective settings for one invocation, resolved from (highest wins):
 * command-line flag, sidecar of a {@code --continue-from} image, environment,
 * config file, built-in default.
 */
public final class Config {

    public static final String DEFAULT_MODEL = "gemini-3.1-flash-image";
    public static final String DEFAULT_RESOLUTION = "1K";
    /**
     * JPEG, because the default model rejects PNG outright:
     * "The value 'image/png' is not supported for 'response_format.mime_type'."
     * Models differ here, so an unset mime type is also auto-corrected from the
     * API's own error - see {@link GeminiImageClient}.
     */
    public static final String DEFAULT_MIME_TYPE = "image/jpeg";
    public static final int DEFAULT_TIMEOUT_SECONDS = 180;
    public static final int DEFAULT_RETRIES = 2;
    public static final int MAX_COUNT = 8;

    public static final Set<String> RESOLUTIONS = Set.of("512px", "1K", "2K", "4K");
    public static final Set<String> ASPECT_RATIOS = Set.of(
            "1:1", "3:2", "2:3", "3:4", "4:3", "4:5", "5:4", "9:16", "16:9", "21:9");
    public static final Set<String> OUTPUT_MIME_TYPES = Set.of("image/png", "image/jpeg");
    public static final Set<String> THINKING_LEVELS = Set.of("minimal", "high");

    /** Env vars checked for the API key, in order. */
    private static final List<String> KEY_ENV_VARS = List.of("IMAGEGEN_API_KEY", "GEMINI_API_KEY", "GOOGLE_API_KEY");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final String apiKey;
    public final String apiKeySource;
    public final String model;
    public final String resolution;
    public final String aspectRatio;
    public final String mimeType;
    /** True when --mime was passed explicitly, which suppresses auto-correction. */
    public final boolean mimeTypeExplicit;
    public final String thinkingLevel;
    public final int timeoutSeconds;
    public final int retries;
    public final Path outDir;
    public final Path configPath;
    public final boolean configPresent;

    private Config(String apiKey, String apiKeySource, String model, String resolution, String aspectRatio,
                   String mimeType, boolean mimeTypeExplicit, String thinkingLevel, int timeoutSeconds,
                   int retries, Path outDir, Path configPath, boolean configPresent) {
        this.apiKey = apiKey;
        this.apiKeySource = apiKeySource;
        this.model = model;
        this.resolution = resolution;
        this.aspectRatio = aspectRatio;
        this.mimeType = mimeType;
        this.mimeTypeExplicit = mimeTypeExplicit;
        this.thinkingLevel = thinkingLevel;
        this.timeoutSeconds = timeoutSeconds;
        this.retries = retries;
        this.outDir = outDir;
        this.configPath = configPath;
        this.configPresent = configPresent;
    }

    public static Path defaultConfigPath() {
        String override = System.getenv("IMAGEGEN_CONFIG");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".config", "imagegen", "config.json");
    }

    /**
     * @param sidecar metadata of the image being continued from, or {@code null}
     */
    public static Config resolve(Options options, JsonNode sidecar) {
        Path path = options.configPath != null ? options.configPath : defaultConfigPath();
        JsonNode file = readConfigFile(path);
        boolean present = file != null;

        String model = firstNonBlank(options.model, text(sidecar, "model"), text(file, "model"), DEFAULT_MODEL);
        String resolution = firstNonBlank(options.resolution, text(sidecar, "resolution"),
                text(file, "resolution"), DEFAULT_RESOLUTION);
        String aspectRatio = firstNonBlank(options.aspectRatio, text(sidecar, "aspectRatio"),
                text(file, "aspectRatio"), null);
        String mimeType = firstNonBlank(options.mimeType, text(sidecar, "mimeType"),
                text(file, "mimeType"), DEFAULT_MIME_TYPE);
        String thinkingLevel = firstNonBlank(options.thinkingLevel, text(sidecar, "thinkingLevel"),
                text(file, "thinkingLevel"), null);

        int timeout = firstPositive(options.timeoutSeconds, integer(file, "timeoutSeconds"), DEFAULT_TIMEOUT_SECONDS);
        int retries = options.retries != null ? options.retries
                : (integer(file, "retries") != null ? integer(file, "retries") : DEFAULT_RETRIES);

        Path outDir = options.outDir;
        if (outDir == null) {
            String configured = text(file, "outDir");
            if (configured != null) {
                outDir = Path.of(expandHome(configured));
            }
        }

        String key = null;
        String source = null;
        if (options.apiKey != null && !options.apiKey.isBlank()) {
            key = options.apiKey.trim();
            source = "--api-key";
        }
        if (key == null) {
            for (String env : KEY_ENV_VARS) {
                String candidate = System.getenv(env);
                if (candidate != null && !candidate.isBlank()) {
                    key = candidate.trim();
                    source = "env:" + env;
                    break;
                }
            }
        }
        if (key == null) {
            String candidate = text(file, "apiKey");
            if (candidate != null) {
                key = candidate.trim();
                source = "config:" + path;
            }
        }

        validate(model, resolution, aspectRatio, mimeType, thinkingLevel, retries, timeout);

        return new Config(key, source, model, resolution, aspectRatio, mimeType,
                options.mimeType != null, thinkingLevel, timeout, retries, outDir, path, present);
    }

    /** Fails with a CONFIG error if no key was found anywhere. */
    public String requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw CliException.config(
                    "No Gemini API key found",
                    "Run 'imagegen config --init' then put your key in " + configPath
                            + ", or export GEMINI_API_KEY, or pass --api-key.");
        }
        return apiKey;
    }

    private static void validate(String model, String resolution, String aspectRatio, String mimeType,
                                 String thinkingLevel, int retries, int timeout) {
        if (model.isBlank()) {
            throw CliException.usage("--model cannot be empty", "Try --model " + DEFAULT_MODEL);
        }
        if (!RESOLUTIONS.contains(resolution)) {
            String hint = RESOLUTIONS.stream().anyMatch(r -> r.equalsIgnoreCase(resolution))
                    ? "The API rejects lowercase 'k' - write it as " + resolution.toUpperCase() + "."
                    : "Valid values: 512px, 1K, 2K, 4K.";
            throw CliException.usage("Unsupported --resolution '" + resolution + "'", hint);
        }
        if (aspectRatio != null && !ASPECT_RATIOS.contains(aspectRatio)) {
            throw CliException.usage("Unsupported --aspect-ratio '" + aspectRatio + "'",
                    "Valid values: " + String.join(", ", ASPECT_RATIOS.stream().sorted().toList()) + ".");
        }
        if (!OUTPUT_MIME_TYPES.contains(mimeType)) {
            throw CliException.usage("Unsupported --mime '" + mimeType + "'",
                    "Valid values: image/png, image/jpeg.");
        }
        if (thinkingLevel != null && !THINKING_LEVELS.contains(thinkingLevel)) {
            throw CliException.usage("Unsupported --thinking '" + thinkingLevel + "'",
                    "Valid values: minimal (default), high.");
        }
        if (retries < 0 || retries > 5) {
            throw CliException.usage("--retries must be between 0 and 5", "Default is " + DEFAULT_RETRIES + ".");
        }
        if (timeout < 5 || timeout > 900) {
            throw CliException.usage("--timeout must be between 5 and 900 seconds",
                    "Default is " + DEFAULT_TIMEOUT_SECONDS + ". 4K renders can take a while.");
        }
    }

    /** Writes a template config with owner-only permissions. Never overwrites an existing file. */
    public static Path init(Path path) {
        try {
            if (Files.exists(path)) {
                throw CliException.config("Config already exists at " + path,
                        "Edit it directly, or pass --config <path> to create a different one.");
            }
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ObjectNode template = MAPPER.createObjectNode();
            template.put("apiKey", "PASTE_YOUR_GEMINI_API_KEY_HERE");
            template.put("model", DEFAULT_MODEL);
            template.put("resolution", DEFAULT_RESOLUTION);
            template.put("mimeType", DEFAULT_MIME_TYPE);
            template.putNull("aspectRatio");
            template.putNull("thinkingLevel");
            template.putNull("outDir");
            template.put("timeoutSeconds", DEFAULT_TIMEOUT_SECONDS);
            template.put("retries", DEFAULT_RETRIES);

            Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(template) + "\n",
                    StandardCharsets.UTF_8);
            restrictPermissions(path);
            return path;
        } catch (CliException e) {
            throw e;
        } catch (Exception e) {
            throw CliException.io("Cannot write config to " + path, "Check directory permissions.", e);
        }
    }

    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (Exception ignored) {
            // Non-POSIX filesystem; the key is simply left at the default mode.
        }
    }

    /** Shows only the last 4 characters so a key can be identified but not reused. */
    public static String redact(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.length() <= 4) {
            return "****";
        }
        return "..." + key.substring(key.length() - 4);
    }

    private static JsonNode readConfigFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return MAPPER.readTree(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw CliException.config("Config at " + path + " is not valid JSON: " + e.getMessage(),
                    "Fix the JSON, or move it aside and run 'imagegen config --init'.");
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private static Integer integer(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isInt() ? value.asInt() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static int firstPositive(Integer a, Integer b, int fallback) {
        if (a != null) {
            return a;
        }
        if (b != null) {
            return b;
        }
        return fallback;
    }

    private static String expandHome(String raw) {
        if (raw.startsWith("~")) {
            return System.getProperty("user.home") + raw.substring(1);
        }
        return raw;
    }
}
