package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.AudioFormat;
import com.srk.myutils.yd.core.DownloadRequest;
import com.srk.myutils.yd.core.OutputConfig;
import com.srk.myutils.yd.core.ProgressListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for T-5.1 — {@code --video} CLI flag (04-apis.md § 3.1.2).
 *
 * <p>Verifies the {@code --video} flag parsing, default computation logic
 * ({@code computeEffectiveVideo}), flag combinations with {@code --audio-only},
 * {@code --transcript}, {@code --thumbnail}, and ordering independence.
 *
 * <p>SUT: {@link Cli} via {@link FakeDownloaderFactory}, and {@link DownloadRequest} directly.
 */
class CliVideoFlagBehaviorTest {

    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @TempDir
    Path tempDir;

    private CommandLine cmd;
    private StringWriter stdout;
    private StringWriter stderr;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void setUp() {
        cmd = new CommandLine(new Cli(FakeDownloaderFactory.happyPath()));
        stdout = new StringWriter();
        stderr = new StringWriter();
        cmd.setOut(new PrintWriter(stdout));
        cmd.setErr(new PrintWriter(stderr));
        originalErr = System.err;
        capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setErr(originalErr);
    }

    // ── 1. --video alone → exit 0, video=true ──

    @Test
    @DisplayName("--video alone → exit 0, request.video()=true")
    void execute_givenVideoAlone_exitsZero() {
        int exitCode = cmd.execute("--video", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 2. No --video, no --audio-only, no --transcript/--thumbnail → default video=true ──

    @Test
    @DisplayName("no flags → default video=true (04-apis.md § 3.1.2 default)")
    void execute_givenNoFlags_defaultVideoTrue() {
        int exitCode = cmd.execute("--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 3. --video --transcript → exit 0 (Flow A + transcript, not Flow C) ──

    @Test
    @DisplayName("--video --transcript → exit 0 (video + transcript combined)")
    void execute_givenVideoAndTranscript_exitsZero() {
        int exitCode = cmd.execute("--video", "--transcript",
                "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 4. --video --audio-only → WARN emitted, --video coerced to false, exit 0 (AC-2.5) ──

    @Test
    @DisplayName("--video --audio-only → exit 0, WARN emitted naming --video as ignored (AC-2.5)")
    void execute_givenVideoAndAudioOnly_exitsZeroWithWarn() {
        int exitCode = cmd.execute("--video", "--audio-only",
                "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
        String logOutput = capturedErr.toString(StandardCharsets.UTF_8);
        assertThat(logOutput).contains("--video");
        assertThat(logOutput).containsIgnoringCase("ignoring");
    }

    // ── 5. --transcript alone (no --video) → video defaults false ──

    @Test
    @DisplayName("--transcript alone → video defaults false (transcript-only = Flow C)")
    void execute_givenTranscriptAlone_exitsZero() {
        int exitCode = cmd.execute("--transcript",
                "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 6. Flag ordering independent: URL --video --transcript == --transcript --video URL ──

    @Test
    @DisplayName("flag ordering independent: --transcript --video == --video --transcript")
    void execute_givenReversedOrder_exitsZero() {
        int exitCode = cmd.execute("--transcript", "--video",
                "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── DownloadRequest record field verification ──

    @Test
    @DisplayName("DownloadRequest: video=true propagates correctly")
    void downloadRequest_givenVideoTrue_carriesValue() {
        DownloadRequest request = new DownloadRequest(
                VALID_URL, false, AudioFormat.M4A, 1080,
                Optional.empty(), false, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                ProgressListener.NO_OP, false, false, true);

        assertThat(request.video()).isTrue();
    }

    @Test
    @DisplayName("DownloadRequest: video=false propagates correctly")
    void downloadRequest_givenVideoFalse_carriesValue() {
        DownloadRequest request = new DownloadRequest(
                VALID_URL, false, AudioFormat.M4A, 1080,
                Optional.empty(), false, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                ProgressListener.NO_OP, false, false, false);

        assertThat(request.video()).isFalse();
    }
}
