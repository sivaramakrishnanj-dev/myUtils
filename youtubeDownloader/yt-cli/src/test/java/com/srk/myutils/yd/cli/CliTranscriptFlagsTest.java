package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for T-4.6 — {@code --transcript}, {@code --lang},
 * {@code --no-asr} CLI flags (AC-6.1, AC-7.4, AC-8.2).
 *
 * <p>Verifies that picocli parses the three transcript-related flags and the
 * CLI exits successfully. Orchestrator wiring is T-4.10; this test only
 * confirms flag plumbing through to {@link com.srk.myutils.yd.core.DownloadRequest}.
 */
class CliTranscriptFlagsTest {

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

    @Test
    @DisplayName("AC-6.1: --transcript flag accepted, exit 0")
    void execute_givenTranscript_exitsZero() {
        int exitCode = cmd.execute("--transcript", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("AC-8.3: --lang fr with no French track → exit 40")
    void execute_givenLang_exitsZero() {
        int exitCode = cmd.execute("--transcript", "--lang", "fr", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        // Fixture only has English tracks; AC-8.3 → CaptionUnavailableException → exit 40
        assertThat(exitCode).isEqualTo(40);
    }

    @Test
    @DisplayName("AC-7.4: --no-asr flag accepted, exit 0")
    void execute_givenNoAsr_exitsZero() {
        int exitCode = cmd.execute("--transcript", "--no-asr", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("all three transcript flags combined → exit 0")
    void execute_givenAllTranscriptFlags_exitsZero() {
        int exitCode = cmd.execute("--transcript", "--lang", "en", "--no-asr",
                "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("--lang without value → picocli error, non-zero exit")
    void execute_givenLangWithoutValue_exitsNonZero() {
        int exitCode = cmd.execute("--lang");

        assertThat(exitCode).isNotZero();
    }
}
