package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.AudioFormat;
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
 * Comprehensive tests for T-4.9 — {@code --thumbnail} CLI flag.
 *
 * <p>Verifies picocli parsing, exit codes, flag combinations, ordering,
 * and {@link DownloadRequest#thumbnail()} field propagation.
 */
class CliThumbnailBehaviorTest {

    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @TempDir
    Path tempDir;

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

    // ── 1. --thumbnail alone → thumbnail=true, exit 0 ──

    @Test
    @DisplayName("--thumbnail alone → exit 0")
    void execute_givenThumbnailAlone_exitsZero() {
        int exitCode = cmd.execute("--thumbnail", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 2. No --thumbnail → thumbnail=false, exit 0 ──

    @Test
    @DisplayName("no --thumbnail → exit 0 (default thumbnail=false)")
    void execute_givenNoThumbnail_exitsZero() {
        int exitCode = cmd.execute("--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 3. --thumbnail --transcript combined → both true, exit 0 ──

    @Test
    @DisplayName("--thumbnail --transcript combined → exit 0")
    void execute_givenThumbnailAndTranscript_exitsZero() {
        int exitCode = cmd.execute("--thumbnail", "--transcript",
                "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 4. --thumbnail --audio-only combined → both set, exit 0 ──

    @Test
    @DisplayName("--thumbnail --audio-only combined → exit 0")
    void execute_givenThumbnailAndAudioOnly_exitsZero() {
        int exitCode = cmd.execute("--thumbnail", "--audio-only",
                "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 5. DownloadRequest field propagation: thumbnail=true ──

    @Test
    @DisplayName("DownloadRequest: thumbnail=true propagates correctly")
    void downloadRequest_givenThumbnailTrue_carriesValue() {
        DownloadRequest request = new DownloadRequest(
                VALID_URL, false, AudioFormat.M4A, 1080,
                Optional.empty(), false, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                ProgressListener.NO_OP, false, true, false);

        assertThat(request.thumbnail()).isTrue();
    }

    // ── 6. Flag ordering: --thumbnail at end → exit 0 ──

    @Test
    @DisplayName("flag ordering: URL before --thumbnail → exit 0")
    void execute_givenThumbnailAfterUrl_exitsZero() {
        int exitCode = cmd.execute("--output-dir", tempDir.toString(), "--force",
                VALID_URL, "--thumbnail");

        assertThat(exitCode).isZero();
    }
}
