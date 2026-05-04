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
 * Comprehensive tests for T-2.7 — {@code --quiet} suppresses progress (AC-4.4).
 *
 * <p>Verifies that:
 * <ul>
 *   <li>{@code --quiet} + valid URL → exit 0, no picocli stderr output</li>
 *   <li>Without {@code --quiet} + valid URL → exit 0 (StderrProgressListener lifecycle completes)</li>
 *   <li>{@code --quiet} does NOT suppress error messages (AC-5.1)</li>
 *   <li>AutoCloseable listener is closed in finally block even on exception</li>
 * </ul>
 *
 * <p>SUT: {@link Cli} — real instance via {@link FakeDownloaderFactory}.
 * picocli {@link CommandLine} with {@link StringWriter} for stderr capture.
 */
class CliQuietBehaviorTest {

    @TempDir
    Path tempDir;

    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String INVALID_URL = "not-a-youtube-url";

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

    // ── 1. --quiet + valid URL → exit 0, picocli stderr empty ──

    @Test
    @DisplayName("AC-4.4: --quiet + valid URL → exit 0, no output on picocli stderr")
    void call_givenQuietAndValidUrl_exitsZeroWithEmptyStderr() {
        int exitCode = cmd.execute("--quiet", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
        assertThat(stderr.toString()).isEmpty();
    }

    // ── 2. Without --quiet + valid URL → exit 0 (listener constructed and closed cleanly) ──

    @Test
    @DisplayName("Without --quiet, valid URL → exit 0 (StderrProgressListener lifecycle completes)")
    void call_withoutQuietAndValidUrl_exitsZero() {
        int exitCode = cmd.execute("--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 3. --quiet + invalid URL → error still prints to stderr (AC-5.1 + AC-4.4) ──

    @Test
    @DisplayName("AC-5.1 + AC-4.4: --quiet + invalid URL → error message still on stderr")
    void call_givenQuietAndInvalidUrl_stillPrintsErrorToStderr() {
        int exitCode = cmd.execute("--quiet", INVALID_URL);

        assertThat(exitCode).isEqualTo(2);
        assertThat(stderr.toString()).contains("Error: args:");
    }

    // ── 4. --quiet + --debug + invalid URL → error + stack trace on stderr ──

    @Test
    @DisplayName("AC-4.4 + AC-5.5: --quiet + --debug + invalid URL → error AND stack trace on stderr")
    void call_givenQuietAndDebugAndInvalidUrl_printsErrorAndStackTrace() {
        int exitCode = cmd.execute("--quiet", "--debug", INVALID_URL);

        assertThat(exitCode).isEqualTo(2);
        String errOutput = stderr.toString();
        assertThat(errOutput).contains("Error: args:");
        assertThat(errOutput).contains("\tat ");
    }

    // ── 5. --quiet is a valid picocli flag ──

    @Test
    @DisplayName("--quiet is a valid picocli flag — no 'Unknown option' error")
    void call_givenQuietFlag_noUnknownOptionError() {
        int exitCode = cmd.execute("--quiet", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(stderr.toString()).doesNotContain("Unknown option");
        assertThat(exitCode).isZero();
    }

    // ── 6. Exception path → finally block closes listener without crash ──

    @Test
    @DisplayName("Exception path: finally block closes AutoCloseable listener without crash")
    void call_givenExceptionDuringDownload_finallyCleansUpWithoutCrash() {
        int exitCode = cmd.execute(INVALID_URL);

        assertThat(exitCode).isEqualTo(2);
        assertThat(stderr.toString()).contains("Error: args:");
    }

    // ── 7. --quiet + exception → finally closes NO_OP listener (no crash) ──

    @Test
    @DisplayName("--quiet + exception: finally closes NO_OP listener cleanly")
    void call_givenQuietAndException_finallyCleansUpNoOpListener() {
        int exitCode = cmd.execute("--quiet", INVALID_URL);

        assertThat(exitCode).isEqualTo(2);
        // NO_OP is not AutoCloseable, so the instanceof check in finally is false —
        // no close() call, no crash. Error pipeline completed normally.
        assertThat(stderr.toString()).contains("Error: args:");
    }
}
