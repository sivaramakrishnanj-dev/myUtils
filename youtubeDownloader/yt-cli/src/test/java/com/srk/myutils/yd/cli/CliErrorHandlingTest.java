package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for T-1.12 — CLI error-handling pipeline (AC-5.1..AC-5.5).
 *
 * <p>Verifies that {@link Cli#call()} catches exceptions, maps them via
 * {@code ErrorMapper}, prints one error line to stderr, and returns the
 * correct exit code.
 */
class CliErrorHandlingTest {

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
    void execute_givenInvalidUrl_exitsWithCode2AndPrintsErrorToStderr() {
        int exitCode = cmd.execute("not-a-youtube-url");

        assertThat(exitCode).isEqualTo(2);
        assertThat(stderr.toString()).contains("Error: args:");
    }

    @Test
    void execute_givenInvalidUrlWithDebug_printsStackTraceAfterErrorLine() {
        int exitCode = cmd.execute("--debug", "not-a-youtube-url");

        assertThat(exitCode).isEqualTo(2);
        String errOutput = stderr.toString();
        assertThat(errOutput).contains("Error: args:");
        assertThat(errOutput).contains("UrlParseException");
    }
}
