package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for {@link Cli} — M0 scope (T-0.5).
 * Covers --help, -h, -V, no-args, unknown-flag, and @Command metadata rendering.
 */
class CliTest {

    private CommandLine cmd;
    private StringWriter stdout;
    private StringWriter stderr;

    @BeforeEach
    void setUp() {
        cmd = new CommandLine(new Cli());
        stdout = new StringWriter();
        stderr = new StringWriter();
        cmd.setOut(new PrintWriter(stdout));
        cmd.setErr(new PrintWriter(stderr));
    }

    @Test
    void execute_givenHelpFlag_exitsZeroAndPrintsUsage() {
        int exitCode = cmd.execute("--help");

        assertThat(exitCode).isZero();
        assertThat(stdout.toString()).contains("Usage:");
    }

    @Test
    void execute_givenShortHelpFlag_exitsZeroAndPrintsUsage() {
        int exitCode = cmd.execute("-h");

        assertThat(exitCode).isZero();
        assertThat(stdout.toString()).contains("Usage:");
    }

    @Test
    void execute_givenShortVersionFlag_exitsZeroAndPrintsVersion() {
        int exitCode = cmd.execute("-V");

        assertThat(exitCode).isZero();
        assertThat(stdout.toString().trim()).isEqualTo("youtube-downloader 1.0.0");
    }

    @Test
    void execute_givenNoArgs_exitsZero() {
        int exitCode = cmd.execute();

        assertThat(exitCode).isZero();
    }

    @Test
    void execute_givenUnknownFlag_exitsNonZeroAndWritesStderr() {
        int exitCode = cmd.execute("--nonexistent-flag");

        assertThat(exitCode).isNotZero();
        assertThat(stderr.toString()).isNotEmpty();
    }

    @Test
    void execute_givenHelpFlag_outputContainsCommandDescription() {
        cmd.execute("--help");

        assertThat(stdout.toString())
                .contains("Download video, audio, transcript, or thumbnail from a YouTube URL.");
    }

    @Test
    void execute_givenHelpFlag_outputContainsCommandName() {
        cmd.execute("--help");

        assertThat(stdout.toString()).contains("youtube-downloader");
    }
}
