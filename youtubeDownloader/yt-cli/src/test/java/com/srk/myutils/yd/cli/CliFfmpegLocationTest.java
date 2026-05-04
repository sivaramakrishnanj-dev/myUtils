package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;

/**
 * Characterization test for T-3.7 — {@code --ffmpeg-location} CLI flag (AC-13.2 escape hatch).
 *
 * <p>Verifies that picocli parses the flag and the CLI exits successfully.
 * The flag value is carried through to {@link com.srk.myutils.yd.core.DownloadRequest#ffmpegLocation()}
 * via {@code Optional.ofNullable(ffmpegLocation)} in {@link Cli#call()}.
 */
class CliFfmpegLocationTest {

    @TempDir
    Path tempDir;

    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    private CommandLine cmd;
    private StringWriter stdout;
    private StringWriter stderr;

    @BeforeEach
    void setUp() {
        cmd = new CommandLine(new Cli(FakeDownloaderFactory.happyPath()));
        stdout = new StringWriter();
        stderr = new StringWriter();
        cmd.setOut(new PrintWriter(stdout));
        cmd.setErr(new PrintWriter(stderr));
    }

    @Test
    @DisplayName("AC-13.2: --ffmpeg-location /fake/ffmpeg → exit 0, flag accepted")
    void execute_givenFfmpegLocation_exitsZero() {
        int exitCode = cmd.execute("--ffmpeg-location", "/fake/ffmpeg", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("no --ffmpeg-location → exit 0, default (null → empty Optional)")
    void execute_givenNoFfmpegLocation_exitsZero() {
        int exitCode = cmd.execute("--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("--ffmpeg-location without value → picocli error, non-zero exit")
    void execute_givenFfmpegLocationWithoutValue_exitsNonZero() {
        int exitCode = cmd.execute("--ffmpeg-location");

        assertThat(exitCode).isNotZero();
    }

    @Test
    @DisplayName("--ffmpeg-location combined with --quiet → both accepted, exit 0")
    void execute_givenFfmpegLocationAndQuiet_exitsZero() {
        int exitCode = cmd.execute("--ffmpeg-location", "/usr/local/bin/ffmpeg", "--quiet", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }
}
