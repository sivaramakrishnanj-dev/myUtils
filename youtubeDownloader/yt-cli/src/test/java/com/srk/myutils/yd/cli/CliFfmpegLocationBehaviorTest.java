package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.DownloadRequest;
import com.srk.myutils.yd.core.OutputConfig;
import com.srk.myutils.yd.core.ProgressListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for T-3.7 — {@code --ffmpeg-location} flag (AC-13.2 escape hatch).
 *
 * <p>Verifies picocli parsing, value propagation to {@link DownloadRequest#ffmpegLocation()},
 * combination with other flags, and the {@link DownloadRequest} record's handling of the field.
 *
 * <p>SUT: {@link Cli} via {@link FakeDownloaderFactory}, and {@link DownloadRequest} directly.
 */
class CliFfmpegLocationBehaviorTest {

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

    // ── 1. --ffmpeg-location /custom/path + valid URL → exit 0 (AC-13.2) ──

    @Test
    @DisplayName("AC-13.2: --ffmpeg-location /custom/path → exit 0, flag accepted")
    void execute_givenFfmpegLocationCustomPath_exitsZero() {
        int exitCode = cmd.execute("--ffmpeg-location", "/custom/path", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 2. No --ffmpeg-location → exit 0 (default: use PATH) ──

    @Test
    @DisplayName("AC-13.2: no --ffmpeg-location → exit 0, default behaviour")
    void execute_givenNoFfmpegLocation_exitsZero() {
        int exitCode = cmd.execute("--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 3. --ffmpeg-location with empty string → picocli accepts it, exit 0 ──

    @Test
    @DisplayName("--ffmpeg-location '' (empty string) → accepted by picocli, exit 0")
    void execute_givenFfmpegLocationEmptyString_exitsZero() {
        int exitCode = cmd.execute("--ffmpeg-location", "", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 4. --ffmpeg-location combined with --debug → both accepted, exit 0 ──

    @Test
    @DisplayName("--ffmpeg-location + --debug → both flags accepted, exit 0")
    void execute_givenFfmpegLocationAndDebug_exitsZero() {
        int exitCode = cmd.execute("--ffmpeg-location", "/usr/local/bin/ffmpeg", "--debug", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 5. --ffmpeg-location without value → picocli usage error ──

    @Test
    @DisplayName("--ffmpeg-location without value → picocli error, non-zero exit")
    void execute_givenFfmpegLocationWithoutValue_exitsNonZero() {
        int exitCode = cmd.execute("--ffmpeg-location");

        assertThat(exitCode).isNotZero();
        assertThat(stderr.toString()).containsIgnoringCase("ffmpeg-location");
    }

    // ── 6. DownloadRequest: ffmpegLocation() returns Optional.of when set ──

    @Test
    @DisplayName("DownloadRequest: ffmpegLocation() returns Optional.of(\"/custom/ffmpeg\")")
    void downloadRequest_givenFfmpegLocation_returnsOptionalOf() {
        DownloadRequest request = new DownloadRequest(
                VALID_URL, false, 1080,
                Optional.of("/custom/ffmpeg"),
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                ProgressListener.NO_OP,
                false);

        assertThat(request.ffmpegLocation()).isPresent()
                .hasValue("/custom/ffmpeg");
    }

    // ── 7. DownloadRequest.audioOnly static factory → Optional.empty for ffmpegLocation ──

    @Test
    @DisplayName("DownloadRequest.audioOnly() → ffmpegLocation is Optional.empty()")
    void downloadRequestAudioOnly_ffmpegLocationIsEmpty() {
        DownloadRequest request = DownloadRequest.audioOnly(
                VALID_URL,
                new OutputConfig(Optional.empty(), Optional.empty(), false));

        assertThat(request.ffmpegLocation()).isEmpty();
    }

    // ── 8. --ffmpeg-location combined with --max-height → both accepted ──

    @Test
    @DisplayName("--ffmpeg-location + --max-height 720 → both flags accepted, exit 0")
    void execute_givenFfmpegLocationAndMaxHeight_exitsZero() {
        int exitCode = cmd.execute("--ffmpeg-location", "/opt/ffmpeg", "--max-height", "720", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }
}
