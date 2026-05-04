package com.srk.myutils.yd.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Derives output filenames (AC-3.3 sanitization, AC-3.4 truncation),
 * resolves output paths ({@code --output}, {@code --output-dir}, CWD default),
 * enforces overwrite protection (AC-3.6), and probes free disk space
 * (NFR-MIN-DISK-FREE).
 *
 * <p>This class does <em>not</em> write media bytes — that is
 * {@code StreamDownloader}'s job. It owns only filename derivation,
 * existence checks, and disk-space probes.
 *
 * @see <a href="design/02-architecture.md">02-architecture.md § 1.2.3 OutputWriter</a>
 */
public final class OutputWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutputWriter.class);

    /** NFR-MAX-FILENAME-LENGTH = 200 characters excluding extension (AC-3.4). */
    static final int MAX_FILENAME_LENGTH = 200;

    /** NFR-MIN-DISK-FREE = 2× expected final file size. */
    static final long FREE_SPACE_MULTIPLIER = 2;

    /**
     * Characters forbidden in filenames per AC-3.3:
     * {@code / \ : * ? " < > |} plus ASCII control characters 0x00–0x1F and 0x7F.
     */
    private static final Pattern ILLEGAL_CHARS =
            Pattern.compile("[/\\\\:*?\"<>|\\x00-\\x1F\\x7F]");

    /** Collapse runs of whitespace to a single space (AC-3.3). */
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    /** Leading/trailing dots and whitespace to trim (AC-3.3). */
    private static final Pattern LEADING_TRAILING_DOTS_WS = Pattern.compile("^[.\\s]+|[.\\s]+$");

    private final OutputConfig config;

    public OutputWriter(OutputConfig config) {
        this.config = config;
    }

    /**
     * Derives the full output path for a given video and extension.
     *
     * <ul>
     *   <li>{@code --output <path>}: use it literally with the given extension (AC-3.5)</li>
     *   <li>{@code --output-dir <dir>}: {@code <dir>/<sanitized_title> [<videoId>].<ext>} (AC-3.2)</li>
     *   <li>Neither: {@code CWD/<sanitized_title> [<videoId>].<ext>} (AC-3.1)</li>
     * </ul>
     */
    public Path deriveOutputPath(VideoDetails details, String extension) {
        if (config.outputPath().isPresent()) {
            Path literal = config.outputPath().get();
            // AC-3.5: strip any extension the user supplied, apply ours
            String name = literal.getFileName().toString();
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                name = name.substring(0, dot);
            }
            return literal.resolveSibling(name + "." + extension);
        }

        String sanitized = sanitizeTitle(details.title());
        String videoIdSuffix = " [" + details.videoId().value() + "]";
        String baseName = truncateFilename(sanitized, videoIdSuffix, MAX_FILENAME_LENGTH);
        String fileName = baseName + "." + extension;

        Path dir = config.outputDir().orElse(Path.of(""));
        return dir.resolve(fileName);
    }

    /**
     * Throws {@link OutputExistsException} if {@code target} exists and
     * {@code --force} was not set (AC-3.6, INV-14, exit code 50).
     */
    public void assertNotExistsOrForce(Path target) {
        if (Files.exists(target) && !config.force()) {
            throw new OutputExistsException(
                    "file '" + target + "' already exists (pass --force to overwrite)");
        }
    }

    /**
     * Probes free disk space at {@code target}'s location and throws
     * {@link FilesystemException} if available space is less than
     * {@code NFR-MIN-DISK-FREE × expectedBytes} (exit code 70).
     */
    public void assertSufficientFreeSpace(Path target, long expectedBytes) {
        Path probeDir = target.getParent();
        if (probeDir == null) {
            probeDir = Path.of(".");
        }
        try {
            if (!Files.exists(probeDir)) {
                Files.createDirectories(probeDir);
                LOGGER.info("Created output directory: {}", probeDir);
            }
            FileStore store = Files.getFileStore(probeDir);
            long usable = store.getUsableSpace();
            long required = (expectedBytes > Long.MAX_VALUE / FREE_SPACE_MULTIPLIER)
                    ? Long.MAX_VALUE
                    : FREE_SPACE_MULTIPLIER * expectedBytes;
            if (usable < required) {
                throw new FilesystemException(
                        probeDir + ": insufficient disk space — need "
                                + required + " bytes but only " + usable + " available");
            }
        } catch (IOException e) {
            throw new FilesystemException(
                    probeDir + ": cannot probe free disk space", e);
        }
    }

    /**
     * Sanitizes a video title for use as a filename per AC-3.3.
     *
     * <ol>
     *   <li>Remove characters in {@code / \ : * ? " < > |} and ASCII control chars 0x00–0x1F, 0x7F</li>
     *   <li>Collapse runs of whitespace to a single space</li>
     *   <li>Trim leading and trailing dots and whitespace</li>
     *   <li>If the result is empty, fall back to {@code "video"}</li>
     * </ol>
     */
    public static String sanitizeTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "video";
        }
        String result = ILLEGAL_CHARS.matcher(title).replaceAll("");
        result = WHITESPACE_RUN.matcher(result).replaceAll(" ");
        result = LEADING_TRAILING_DOTS_WS.matcher(result).replaceAll("");
        return result.isEmpty() ? "video" : result;
    }

    /**
     * Truncates a base filename to fit within {@code maxLen} characters,
     * preserving the {@code videoIdSuffix} intact (AC-3.4).
     *
     * <p>The returned string is {@code <truncated-title><videoIdSuffix>},
     * with the title truncated from the right if the combined length
     * exceeds {@code maxLen}.
     */
    public static String truncateFilename(String baseName, String videoIdSuffix, int maxLen) {
        String combined = baseName + videoIdSuffix;
        if (combined.length() <= maxLen) {
            return combined;
        }
        int titleBudget = maxLen - videoIdSuffix.length();
        if (titleBudget <= 0) {
            return videoIdSuffix.substring(0, Math.min(videoIdSuffix.length(), maxLen));
        }
        return baseName.substring(0, titleBudget) + videoIdSuffix;
    }
}
