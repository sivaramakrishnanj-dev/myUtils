package com.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names and writes output images plus their sidecar metadata.
 *
 * <p>Naming is {@code out_<seq>_<base>.<ext>} where {@code seq} is derived by
 * scanning the target directory. That keeps numbering stateless - no counter file
 * to drift or corrupt - and means moving files between folders stays safe.
 */
public final class OutputWriter {

    /** Matches the prefix this tool writes, so outputs can be re-read and re-edited. */
    static final Pattern OUT_PREFIX = Pattern.compile("^out_(\\d+)_(.*)$");

    private static final int MAX_SLUG_LENGTH = 40;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Written(Path image, long bytes, int seq, Path sidecar) {
    }

    private OutputWriter() {
    }

    /** Lowest unused sequence number in {@code dir}, starting at 1. */
    public static int nextSeq(Path dir) {
        int max = 0;
        if (!Files.isDirectory(dir)) {
            return 1;
        }
        try (var entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                Matcher matcher = OUT_PREFIX.matcher(entry.getFileName().toString());
                if (matcher.matches()) {
                    try {
                        max = Math.max(max, Integer.parseInt(matcher.group(1)));
                    } catch (NumberFormatException ignored) {
                        // A file like out_99999999999999_x.png - skip it rather than fail.
                    }
                }
            }
        } catch (IOException e) {
            throw CliException.io("Cannot list " + dir, "Check the directory is readable.", e);
        }
        return max + 1;
    }

    /**
     * Base name for an output derived from an input file: the filename without its
     * extension, with any existing {@code out_<n>_} prefix stripped so editing an
     * output does not produce {@code out_002_out_001_photo}.
     */
    public static String baseNameOf(Path input) {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        Matcher matcher = OUT_PREFIX.matcher(name);
        if (matcher.matches()) {
            name = matcher.group(2);
        }
        String sanitized = name.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return sanitized.isBlank() ? "image" : sanitized;
    }

    /** Kebab-cased opening of the prompt, for generate-mode filenames. */
    public static String slug(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "image";
        }
        String slug = prompt.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH);
            // Drop a trailing partial word, but only if enough of the prompt survives.
            int lastDash = slug.lastIndexOf('-');
            if (lastDash >= 12) {
                slug = slug.substring(0, lastDash);
            }
            slug = slug.replaceAll("-+$", "");
        }
        return slug.isBlank() ? "image" : slug;
    }

    /** Writes one image and its sidecar. Returns absolute paths. */
    public static Written write(Path dir, String base, String mimeType, byte[] bytes, ObjectNode metadata) {
        Path target = ensureDirectory(dir);
        int seq = nextSeq(target);
        String ext = Mime.extensionOf(mimeType);
        Path image = target.resolve(String.format("out_%03d_%s.%s", seq, base, ext));
        Path sidecar = target.resolve(String.format("out_%03d_%s.json", seq, base));

        try {
            Files.write(image, bytes);
        } catch (IOException e) {
            throw CliException.io("Cannot write " + image, "Check the directory is writable.", e);
        }

        metadata.put("image", image.toString());
        metadata.put("createdAt", Instant.now().toString());
        try {
            Files.writeString(sidecar,
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(metadata) + "\n",
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw CliException.io("Cannot write sidecar " + sidecar,
                    "The image was saved; only its metadata failed.", e);
        }

        return new Written(image, bytes.length, seq, sidecar);
    }

    /** Resolves the target directory, creating it if needed. */
    public static Path ensureDirectory(Path dir) {
        Path absolute = dir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(absolute);
        } catch (IOException e) {
            throw CliException.io("Cannot create output directory " + absolute,
                    "Check permissions on the parent directory.", e);
        }
        return absolute;
    }

    /** Predicts the next output path without writing anything, for --dry-run. */
    public static Path preview(Path dir, String base, String mimeType, int offset) {
        Path absolute = dir.toAbsolutePath().normalize();
        int seq = nextSeq(absolute) + offset;
        return absolute.resolve(String.format("out_%03d_%s.%s", seq, base, Mime.extensionOf(mimeType)));
    }

    /**
     * Reads the sidecar for {@code --continue-from}. Accepts either the sidecar
     * {@code .json} or the image it describes.
     */
    public static JsonNode readSidecar(Path path) {
        Path sidecar = path;
        String name = path.getFileName().toString();
        if (!name.endsWith(".json")) {
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            sidecar = path.toAbsolutePath().getParent().resolve(stem + ".json");
        }
        if (!Files.isRegularFile(sidecar)) {
            throw CliException.usage(
                    "No sidecar metadata found at " + sidecar,
                    "--continue-from needs an image produced by this tool (its .json sits beside it).");
        }
        try {
            return MAPPER.readTree(Files.readString(sidecar, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw CliException.io("Cannot read sidecar " + sidecar, "The file is not valid JSON.", e);
        }
    }

    /** Builds the sidecar document for an output about to be written. */
    public static ObjectNode metadata(Config config, String command, String prompt,
                                     String interactionId, List<Path> sourceImages, String mimeType) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("command", command);
        node.put("prompt", prompt);
        node.put("model", config.model);
        node.put("resolution", config.resolution);
        node.put("mimeType", mimeType);
        if (config.aspectRatio != null) {
            node.put("aspectRatio", config.aspectRatio);
        }
        if (config.thinkingLevel != null) {
            node.put("thinkingLevel", config.thinkingLevel);
        }
        if (interactionId != null) {
            node.put("interactionId", interactionId);
        }
        if (!sourceImages.isEmpty()) {
            ArrayNode sources = node.putArray("sourceImages");
            sourceImages.forEach(p -> sources.add(p.toAbsolutePath().normalize().toString()));
        }
        return node;
    }
}
