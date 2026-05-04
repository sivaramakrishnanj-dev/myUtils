package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive CLI error-handling tests for T-1.12 (AC-5.1..AC-5.5).
 *
 * <p>Verifies the full error pipeline: Cli.call() catches exceptions, maps via
 * {@code ErrorMapper}, prints one-line to stderr, returns the correct exit code,
 * and conditionally prints stack traces based on {@code --debug}.
 *
 * <p>SUT: {@link Cli} — real instance, no mocks. picocli {@link CommandLine} with
 * {@link StringWriter} for stdout/stderr capture. NoNetworkExtension active globally.
 *
 * <p>In M1, only {@code UrlParseException} is live-triggerable via the CLI (from
 * {@code UrlParser}). Other exception categories (network, innertube, etc.) require
 * T-1.14 orchestrator wiring. The ErrorMapper unit tests (T-1.10) already verify
 * all 11 category mappings; this test class validates the CLI dispatch layer for
 * the one category reachable end-to-end.
 *
 * @see CliErrorHandlingTest characterization tests (implementer)
 */
class CliErrorHandlingBehaviorTest {

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

    // ── 1. Invalid URL → exit 2, stderr "Error: args: ..." (AC-5.1, AC-5.2, CT-EXIT-UNIT-1) ──

    @Nested
    @DisplayName("Invalid URLs → exit 2 with error message (AC-5.1, AC-5.2)")
    class InvalidUrlExitCode {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @DisplayName("CT-EXIT-UNIT-1: invalid URL shapes → exit 2, stderr starts with 'Error: args:'")
        @ValueSource(strings = {
                "not-a-url",
                "https://google.com",
                "https://youtube.com",
                "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "ftp://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://www.youtube.com/playlist?list=PLrAXtmErZgOe"
        })
        void execute_givenInvalidUrl_exitsCode2WithArgsError(String url) {
            int exitCode = cmd.execute(url);

            assertThat(exitCode).isEqualTo(2);
            assertThat(stderr.toString()).contains("Error: args:");
        }
    }

    // ── 2. Valid URL → exit 0, no stderr output ──

    @Test
    @DisplayName("Valid URL → exit 0, stderr empty")
    void execute_givenValidUrl_exitsZeroWithNoStderr() {
        int exitCode = cmd.execute("https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(exitCode).isZero();
        assertThat(stderr.toString()).isEmpty();
    }

    // ── 3. --debug + invalid URL → exit 2, stderr has error line AND stack trace (AC-5.5) ──

    @Test
    @DisplayName("AC-5.5: --debug + invalid URL → stderr contains error line AND stack trace")
    void execute_givenDebugAndInvalidUrl_printsErrorAndStackTrace() {
        int exitCode = cmd.execute("--debug", "not-a-youtube-url");

        assertThat(exitCode).isEqualTo(2);
        String errOutput = stderr.toString();
        assertThat(errOutput).contains("Error: args:");
        assertThat(errOutput).contains("UrlParseException");
        assertThat(errOutput).contains("\tat ");
    }

    // ── 4. Without --debug + invalid URL → exit 2, NO stack trace (AC-5.4) ──

    @Test
    @DisplayName("AC-5.4: without --debug, invalid URL → stderr has error line but NO stack trace")
    void execute_givenInvalidUrlWithoutDebug_printsErrorButNoStackTrace() {
        int exitCode = cmd.execute("not-a-youtube-url");

        assertThat(exitCode).isEqualTo(2);
        String errOutput = stderr.toString();
        assertThat(errOutput).contains("Error: args:");
        assertThat(errOutput).doesNotContain("UrlParseException");
        assertThat(errOutput).doesNotContain("\tat ");
    }

    // ── 5. Missing URL → picocli native error; exit 2 ──

    @Test
    @DisplayName("Missing URL → picocli error; exit 2; stderr contains 'Missing required parameter'")
    void execute_givenNoArgs_exitsCode2WithMissingParamMessage() {
        int exitCode = cmd.execute();

        assertThat(exitCode).isEqualTo(2);
        assertThat(stderr.toString()).contains("Missing required parameter");
    }

    // ── 6. --quiet + invalid URL → exit 2, stderr STILL has error (AC-4.4 + AC-5.1) ──

    @Test
    @DisplayName("AC-5.1 + AC-4.4: --quiet + invalid URL → errors still printed to stderr")
    void execute_givenQuietAndInvalidUrl_stillPrintsErrorToStderr() {
        int exitCode = cmd.execute("--quiet", "not-a-youtube-url");

        assertThat(exitCode).isEqualTo(2);
        assertThat(stderr.toString()).contains("Error: args:");
    }

    // ── 7. Message format: single line, "Error: <category>: <detail>" (AC-5.1) ──

    @Test
    @DisplayName("AC-5.1: error message is single line matching 'Error: <category>: <detail>'")
    void execute_givenInvalidUrl_errorMessageMatchesFormat() {
        cmd.execute("not-a-youtube-url");

        String errOutput = stderr.toString().trim();
        assertThat(errOutput).matches("Error: args: .+");
    }

    @Test
    @DisplayName("AC-5.1: exactly one 'Error:' line on stderr (no duplicates)")
    void execute_givenInvalidUrl_exactlyOneErrorLine() {
        cmd.execute("https://google.com");

        long errorLineCount = stderr.toString().lines()
                .filter(line -> line.startsWith("Error:"))
                .count();
        assertThat(errorLineCount).isEqualTo(1);
    }

    // ── 8. Exit code correctness for UrlParseException (CT-EXIT-UNIT-1) ──

    @Test
    @DisplayName("CT-EXIT-UNIT-1: UrlParseException → exit code 2 at CLI layer")
    void execute_givenUrlParseError_exitsExactlyCode2() {
        int exitCode = cmd.execute("https://www.youtube.com/channel/UCxyz");

        assertThat(exitCode).isEqualTo(2);
    }

    // ── 9. --debug + --quiet + invalid URL → error AND stack trace both present ──

    @Test
    @DisplayName("--debug + --quiet + invalid URL → error line + stack trace both present")
    void execute_givenDebugAndQuietAndInvalidUrl_printsErrorAndStackTrace() {
        int exitCode = cmd.execute("--debug", "--quiet", "not-a-youtube-url");

        assertThat(exitCode).isEqualTo(2);
        String errOutput = stderr.toString();
        assertThat(errOutput).contains("Error: args:");
        assertThat(errOutput).contains("\tat ");
    }

    // ── 10. --debug + valid URL → exit 0, no error output ──

    @Test
    @DisplayName("--debug + valid URL → exit 0, no error line on stderr")
    void execute_givenDebugAndValidUrl_exitsZeroNoError() {
        int exitCode = cmd.execute("--debug", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(exitCode).isZero();
        assertThat(stderr.toString()).doesNotContain("Error:");
    }

    // ── 11. Error message contains the cause detail from the exception ──

    @Test
    @DisplayName("AC-5.1: error message includes specific detail from UrlParseException")
    void execute_givenBareYoutubeDomain_errorContainsUrlInDetail() {
        cmd.execute("https://youtube.com");

        assertThat(stderr.toString()).contains("Error: args:")
                .contains("youtube.com");
    }
}
