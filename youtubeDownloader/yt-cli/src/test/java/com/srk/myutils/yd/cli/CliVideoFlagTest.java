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
 * Characterization test for T-5.1 — {@code --video} CLI flag (04-apis.md § 3.1.2).
 *
 * <p>Verifies that picocli accepts the {@code --video} flag and the CLI
 * exits successfully when it is passed alongside {@code --transcript}.
 */
class CliVideoFlagTest {

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
    @DisplayName("--video --transcript accepted, exit 0 (video + transcript combined)")
    void execute_givenVideoAndTranscript_exitsZero() {
        int exitCode = cmd.execute("--video", "--transcript",
                "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }
}
