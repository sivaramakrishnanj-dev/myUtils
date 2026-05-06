package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for T-5.3 — AC-5.1 compliance: without {@code --debug},
 * a failure produces exactly one line of error output with no stack trace.
 */
class CliDebugTest {

    private CommandLine cmd;
    private StringWriter stderr;

    @BeforeEach
    void setUp() {
        cmd = new CommandLine(new Cli(FakeDownloaderFactory.happyPath()));
        stderr = new StringWriter();
        cmd.setOut(new PrintWriter(new StringWriter()));
        cmd.setErr(new PrintWriter(stderr));
    }

    @Test
    void execute_givenInvalidUrlWithoutDebug_printsOneLineNoStackTrace() {
        int exitCode = cmd.execute("not-a-youtube-url");

        assertThat(exitCode).isEqualTo(2);
        String errOutput = stderr.toString().trim();
        assertThat(errOutput.lines().count()).isEqualTo(1);
        assertThat(errOutput).doesNotContain("com.srk.myutils");
        assertThat(errOutput).doesNotContain("\tat ");
    }
}
