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
 * Characterization test for T-4.9 — {@code --thumbnail} CLI flag.
 *
 * <p>Verifies that picocli parses the {@code --thumbnail} flag and the CLI
 * exits successfully. Orchestrator wiring is T-4.10; this test only confirms
 * flag plumbing through to {@link com.srk.myutils.yd.core.DownloadRequest}.
 */
class CliThumbnailTest {

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
    @DisplayName("--thumbnail flag accepted, exit 0")
    void execute_givenThumbnail_exitsZero() {
        int exitCode = cmd.execute("--thumbnail", "--output-dir", tempDir.toString(), "--force", VALID_URL);

        assertThat(exitCode).isZero();
    }
}
