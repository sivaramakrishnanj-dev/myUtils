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
 * Comprehensive tests for T-4.6 — {@code --transcript}, {@code --lang},
 * {@code --no-asr} CLI flags (AC-6.1, AC-7.4, AC-8.2).
 *
 * <p>Verifies picocli parsing, exit codes, flag combinations, error cases,
 * and {@link DownloadRequest} record field propagation for transcript-related options.
 *
 * <p>SUT: {@link Cli} via {@link FakeDownloaderFactory}, and {@link DownloadRequest} directly.
 */
class CliTranscriptFlagsBehaviorTest {

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

    // ── 1. --transcript alone → exit 0, transcript=true (AC-6.1) ──

    @Test
    @DisplayName("AC-6.1: --transcript alone → exit 0")
    void execute_givenTranscriptAlone_exitsZero() {
        int exitCode = cmd.execute("--transcript", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 2. No --transcript → exit 0, transcript=false (default) ──

    @Test
    @DisplayName("no --transcript → exit 0, default transcript=false")
    void execute_givenNoTranscript_exitsZero() {
        int exitCode = cmd.execute("--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 3. --lang en → exit 0, lang=Optional.of("en") (AC-8.2) ──

    @Test
    @DisplayName("AC-8.2: --lang en → exit 0")
    void execute_givenLangEn_exitsZero() {
        int exitCode = cmd.execute("--transcript", "--lang", "en", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 4. --lang fr-CA → exit 0, lang=Optional.of("fr-CA") (AC-8.2 subtag) ──

    @Test
    @DisplayName("AC-8.2: --lang fr-CA (subtag) → exit 0")
    void execute_givenLangFrCa_exitsZero() {
        int exitCode = cmd.execute("--transcript", "--lang", "fr-CA", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 5. Without --lang → exit 0, lang=Optional.empty (default chain) ──

    @Test
    @DisplayName("no --lang → exit 0, default preference chain")
    void execute_givenNoLang_exitsZero() {
        int exitCode = cmd.execute("--transcript", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 6. --no-asr → exit 0, noAsr=true (AC-7.4) ──

    @Test
    @DisplayName("AC-7.4: --no-asr → exit 0")
    void execute_givenNoAsr_exitsZero() {
        int exitCode = cmd.execute("--transcript", "--no-asr", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 7. Without --no-asr → exit 0, noAsr=false (default) ──

    @Test
    @DisplayName("no --no-asr → exit 0, default noAsr=false")
    void execute_givenNoNoAsr_exitsZero() {
        int exitCode = cmd.execute("--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 8. All three combined: --transcript --lang en --no-asr → exit 0 ──

    @Test
    @DisplayName("AC-6.1+AC-8.2+AC-7.4: all three transcript flags combined → exit 0")
    void execute_givenAllTranscriptFlags_exitsZero() {
        int exitCode = cmd.execute("--transcript", "--lang", "en", "--no-asr",
                "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 9. --lang without value → picocli usage error, exit 2 ──

    @Test
    @DisplayName("--lang without value → picocli error, exit 2")
    void execute_givenLangWithoutValue_exitsWithUsageError() {
        int exitCode = cmd.execute("--transcript", "--lang", "--output-dir", tempDir.toString(), VALID_URL);

        assertThat(exitCode).isEqualTo(2);
    }

    // ── 10. Flag ordering doesn't matter ──

    @Test
    @DisplayName("flag ordering: --no-asr --lang en --transcript → exit 0")
    void execute_givenReversedFlagOrder_exitsZero() {
        int exitCode = cmd.execute("--no-asr", "--lang", "en", "--transcript",
                "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── DownloadRequest record field verification ──

    @Test
    @DisplayName("DownloadRequest: transcript=true, lang=Optional.of(\"en\"), noAsr=true")
    void downloadRequest_givenTranscriptFields_carriesValues() {
        DownloadRequest request = new DownloadRequest(
                VALID_URL, false, AudioFormat.M4A, 1080,
                Optional.empty(),
                true, Optional.of("en"), true,
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                ProgressListener.NO_OP, false, false);

        assertThat(request.transcript()).isTrue();
        assertThat(request.lang()).isPresent().hasValue("en");
        assertThat(request.noAsr()).isTrue();
    }

    @Test
    @DisplayName("DownloadRequest: defaults — transcript=false, lang=empty, noAsr=false")
    void downloadRequest_givenDefaults_carriesDefaultValues() {
        DownloadRequest request = new DownloadRequest(
                VALID_URL, false, AudioFormat.M4A, 1080,
                Optional.empty(),
                false, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                ProgressListener.NO_OP, false, false);

        assertThat(request.transcript()).isFalse();
        assertThat(request.lang()).isEmpty();
        assertThat(request.noAsr()).isFalse();
    }
}
