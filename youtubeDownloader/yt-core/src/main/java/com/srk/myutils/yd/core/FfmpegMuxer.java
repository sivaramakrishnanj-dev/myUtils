package com.srk.myutils.yd.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Integrates with a local {@code ffmpeg} binary for muxing and transcoding
 * (ADR 0003). T-3.1 implements only {@link #probeVersion()}; T-3.2..T-3.10
 * add mux, transcode, stderr capture, shutdown, timeout, and CLI wiring.
 */
public final class FfmpegMuxer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FfmpegMuxer.class);

    /**
     * Semantic version parsed from {@code ffmpeg -version} output.
     */
    public record Version(int major, int minor, int patch) implements Comparable<Version> {

        public Version {
            if (major < 0 || minor < 0 || patch < 0) {
                throw new IllegalArgumentException("Version components must be non-negative");
            }
        }

        @Override
        public int compareTo(Version other) {
            int c = Integer.compare(this.major, other.major);
            if (c != 0) return c;
            c = Integer.compare(this.minor, other.minor);
            if (c != 0) return c;
            return Integer.compare(this.patch, other.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    /** NFR-MIN-FFMPEG-VERSION = 4.0 */
    static final Version MIN_VERSION = new Version(4, 0, 0);

    /** NFR-DEFAULT-MP3-BITRATE = 192 kbps */
    static final String DEFAULT_MP3_BITRATE = "192k";

    /** Matches {@code ffmpeg version X.Y.Z} or {@code ffmpeg version N:X.Y.Z} etc. */
    private static final Pattern VERSION_LINE_PATTERN = Pattern.compile("^ffmpeg version (?:\\S+:)?(\\S+)");

    /** Parses "X.Y.Z-suffix", "X.Y.Z", "X.Y" into (major, minor, patch). */
    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private final String ffmpegBinaryPath;

    public FfmpegMuxer() {
        this("ffmpeg");
    }

    public FfmpegMuxer(String ffmpegBinaryPath) {
        this.ffmpegBinaryPath = Objects.requireNonNull(ffmpegBinaryPath, "ffmpegBinaryPath");
    }

    /**
     * Probe {@code ffmpeg -version} and verify it is ≥ {@link #MIN_VERSION}.
     *
     * @return the detected version
     * @throws FfmpegException if ffmpeg is not found, exits non-zero,
     *         outputs an unparseable version, or is below the minimum (AC-13.1, AC-13.2, AC-13.3)
     */
    public Version probeVersion() {
        LOGGER.info("Probing ffmpeg version: {} -version", ffmpegBinaryPath);

        String firstLine;
        int exitCode;
        try {
            Process process = new ProcessBuilder(ffmpegBinaryPath, "-version")
                    .redirectErrorStream(true)
                    .start();
            try {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    firstLine = reader.readLine();
                }

                exitCode = process.waitFor();
            } finally {
                process.destroy();
            }
        } catch (IOException e) {
            LOGGER.error("ffmpeg probe failed: {}", e.getMessage());
            throw new FfmpegException(
                    "ffmpeg not found on PATH or version check failed. "
                            + "Install ffmpeg from https://ffmpeg.org/ and ensure it is on PATH.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FfmpegException(
                    "ffmpeg not found on PATH or version check failed. "
                            + "Install ffmpeg from https://ffmpeg.org/ and ensure it is on PATH.", e);
        }

        if (exitCode != 0 || firstLine == null) {
            throw new FfmpegException(
                    "ffmpeg not found on PATH or version check failed. "
                            + "Install ffmpeg from https://ffmpeg.org/ and ensure it is on PATH.");
        }

        Version version = parseVersion(firstLine);
        LOGGER.info("Detected ffmpeg version: {}", version);

        if (version.compareTo(MIN_VERSION) < 0) {
            throw new FfmpegException(
                    "detected version " + version + ", but version " + MIN_VERSION + " or higher is required.");
        }

        return version;
    }

    /**
     * Mux a video and audio {@code .part} file into a single MP4 container
     * using {@code ffmpeg -c copy} (stream-copy, no re-encode) per ADR 0003
     * and 04-apis.md § 2.1.2.
     *
     * @param videoPart path to the video {@code .part} file (input 0)
     * @param audioPart path to the audio {@code .part} file (input 1)
     * @param output    path to the output {@code .mp4} file
     * @param debug     when {@code true}, sets ffmpeg loglevel to {@code info}
     *                  instead of {@code error} (NFR-FFMPEG-LOGLEVEL)
     * @throws FfmpegException if ffmpeg exits non-zero (exit code 60, AC-1.6, AC-13.4)
     */
    public void mux(Path videoPart, Path audioPart, Path output, boolean debug) {
        Objects.requireNonNull(videoPart, "videoPart");
        Objects.requireNonNull(audioPart, "audioPart");
        Objects.requireNonNull(output, "output");

        List<String> command = List.of(
                ffmpegBinaryPath,
                "-hide_banner",
                "-loglevel", debug ? "info" : "error",
                "-i", videoPart.toString(),
                "-i", audioPart.toString(),
                "-c", "copy",
                "-map", "0:v:0",
                "-map", "1:a:0",
                "-y",
                output.toString()
        );

        LOGGER.info("Muxing video+audio → MP4: {}", String.join(" ", command));

        try {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try {
                String stderr;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    stderr = reader.lines().reduce((a, b) -> a + "\n" + b).orElse("");
                }

                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    LOGGER.error("ffmpeg mux failed (exit {}): {}", exitCode, stderr);
                    throw new FfmpegException(
                            "ffmpeg mux failed (exit " + exitCode + ")"
                                    + (stderr.isEmpty() ? "" : ":\n" + stderr));
                }

                LOGGER.info("Mux complete: {}", output);
            } finally {
                process.destroy();
            }
        } catch (IOException e) {
            LOGGER.error("ffmpeg mux failed: {}", e.getMessage());
            throw new FfmpegException("ffmpeg mux failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FfmpegException("ffmpeg mux interrupted", e);
        }
    }

    /**
     * Transcode an audio {@code .part} file to MP3 using {@code libmp3lame}
     * at {@link #DEFAULT_MP3_BITRATE} per 04-apis.md § 2.1.3 and AC-2.4.
     *
     * @param audioPart path to the audio {@code .part} file
     * @param output    path to the output {@code .mp3} file
     * @param debug     when {@code true}, sets ffmpeg loglevel to {@code info}
     *                  instead of {@code error} (NFR-FFMPEG-LOGLEVEL)
     * @throws FfmpegException if ffmpeg exits non-zero (exit code 60)
     */
    public void transcodeMp3(Path audioPart, Path output, boolean debug) {
        Objects.requireNonNull(audioPart, "audioPart");
        Objects.requireNonNull(output, "output");

        List<String> command = List.of(
                ffmpegBinaryPath,
                "-hide_banner",
                "-loglevel", debug ? "info" : "error",
                "-i", audioPart.toString(),
                "-c:a", "libmp3lame",
                "-b:a", DEFAULT_MP3_BITRATE,
                "-y",
                output.toString()
        );

        LOGGER.info("Transcoding audio → MP3: {}", String.join(" ", command));

        try {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try {
                String stderr;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    stderr = reader.lines().reduce((a, b) -> a + "\n" + b).orElse("");
                }

                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    LOGGER.error("ffmpeg transcode failed (exit {}): {}", exitCode, stderr);
                    throw new FfmpegException(
                            "ffmpeg transcode failed (exit " + exitCode + ")"
                                    + (stderr.isEmpty() ? "" : ":\n" + stderr));
                }

                LOGGER.info("Transcode complete: {}", output);
            } finally {
                process.destroy();
            }
        } catch (IOException e) {
            LOGGER.error("ffmpeg transcode failed: {}", e.getMessage());
            throw new FfmpegException("ffmpeg transcode failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FfmpegException("ffmpeg transcode interrupted", e);
        }
    }

    /**
     * Parse the first line of {@code ffmpeg -version} output into a {@link Version}.
     *
     * @throws FfmpegException if the line does not match the expected pattern
     */
    private static Version parseVersion(String firstLine) {
        Matcher lineMatcher = VERSION_LINE_PATTERN.matcher(firstLine);
        if (!lineMatcher.find()) {
            throw new FfmpegException(
                    "ffmpeg not found on PATH or version check failed. "
                            + "Install ffmpeg from https://ffmpeg.org/ and ensure it is on PATH.");
        }

        String versionToken = lineMatcher.group(1);
        Matcher numberMatcher = VERSION_NUMBER_PATTERN.matcher(versionToken);
        if (!numberMatcher.find()) {
            throw new FfmpegException(
                    "ffmpeg not found on PATH or version check failed. "
                            + "Install ffmpeg from https://ffmpeg.org/ and ensure it is on PATH.");
        }

        int major = Integer.parseInt(numberMatcher.group(1));
        int minor = Integer.parseInt(numberMatcher.group(2));
        int patch = numberMatcher.group(3) != null ? Integer.parseInt(numberMatcher.group(3)) : 0;
        return new Version(major, minor, patch);
    }
}
