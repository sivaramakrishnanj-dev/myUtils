package com.srk.myutils.yd.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Represents a single download attempt's temporary workspace under
 * {@code <outputDir>/.yt-tmp/<unique-id>/} (NFR-TEMP-DIR-STRATEGY, INV-6).
 *
 * <p>Implements {@link AutoCloseable} for use in try-with-resources.
 * On {@link #close()}, the temp directory is deleted only if
 * {@link #markSuccess()} was called; otherwise it is retained for
 * resume / debug inspection.
 *
 * <p>Orphan detection: {@link #warnOrphanTempDirs(Path)} logs a WARN
 * if previous {@code .yt-tmp/*} directories exist (AC-10.3).
 */
public final class DownloadContext implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadContext.class);

    static final String TEMP_ROOT_NAME = ".yt-tmp";

    private final Path tempDir;
    private volatile boolean success;

    private DownloadContext(Path tempDir) {
        this.tempDir = tempDir;
    }

    /**
     * Creates a new download context with a unique temp directory under
     * {@code outputDir/.yt-tmp/<uuid>/}. The directory is created on disk
     * immediately. Warns about any pre-existing orphan temp dirs (AC-10.3).
     *
     * @param outputDir the output directory (resolved; never null)
     * @param videoId   the video being downloaded (used for logging only)
     * @return a new context whose {@link #tempDir()} exists on disk
     * @throws FilesystemException if the directory cannot be created
     */
    public static DownloadContext create(Path outputDir, VideoId videoId) {
        Path ytTmpRoot = outputDir.resolve(TEMP_ROOT_NAME);
        warnOrphanTempDirs(ytTmpRoot);

        Path uniqueDir = ytTmpRoot.resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(uniqueDir);
        } catch (IOException e) {
            throw new FilesystemException(
                    uniqueDir + ": cannot create temp directory", e);
        }
        LOGGER.info("Created temp directory for video {}: {}", videoId.value(), uniqueDir);
        return new DownloadContext(uniqueDir);
    }

    /** Returns the unique temp directory for this download attempt. */
    public Path tempDir() {
        return tempDir;
    }

    /**
     * Returns a path for a named temp file inside this context's temp directory.
     *
     * @param name the filename, e.g. {@code "video.part"}
     * @return {@code <tempDir>/<name>}
     */
    public Path tempFile(String name) {
        return tempDir.resolve(name);
    }

    /**
     * Marks this download as successful. A subsequent {@link #close()} will
     * delete the temp directory and its contents.
     */
    public void markSuccess() {
        this.success = true;
    }

    /**
     * If {@link #markSuccess()} was called, deletes the temp directory
     * recursively. Otherwise retains it for resume / debug (INV-6).
     */
    @Override
    public void close() {
        if (!success) {
            LOGGER.info("Retaining temp directory for inspection: {}", tempDir);
            return;
        }
        try {
            deleteRecursively(tempDir);
            // Also remove the .yt-tmp parent if it is now empty
            Path ytTmpRoot = tempDir.getParent();
            if (ytTmpRoot != null && Files.isDirectory(ytTmpRoot) && isEmptyDir(ytTmpRoot)) {
                Files.delete(ytTmpRoot);
            }
            LOGGER.info("Cleaned temp directory: {}", tempDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to clean temp directory {}: {}", tempDir, e.getMessage());
        }
    }

    /**
     * Logs a WARN for each pre-existing child directory under {@code ytTmpRoot}
     * (AC-10.3 — orphan temp dir from a previous crash is a notable event).
     */
    static void warnOrphanTempDirs(Path ytTmpRoot) {
        if (!Files.isDirectory(ytTmpRoot)) {
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(ytTmpRoot)) {
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    LOGGER.warn("Orphan temp directory from a previous run: {}", child);
                }
            }
        } catch (IOException e) {
            LOGGER.debug("Could not scan for orphan temp dirs in {}: {}", ytTmpRoot, e.getMessage());
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new FilesystemException(p + ": failed to delete", e);
                }
            });
        }
    }

    private static boolean isEmptyDir(Path dir) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            return !ds.iterator().hasNext();
        }
    }
}
