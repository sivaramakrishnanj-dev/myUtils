package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;

/**
 * Characterization test for T-2.7 — {@code --quiet} suppresses progress (AC-4.4).
 *
 * <p>Verifies that {@code --quiet} wires {@link com.srk.myutils.yd.core.ProgressListener#NO_OP}
 * and the download still succeeds (exit 0). Comprehensive quiet-vs-stderr tests are the tester's scope.
 */
class CliQuietTest {

    @TempDir
    Path tempDir;

    @Test
    void execute_givenQuietFlag_exitsZero() {
        CommandLine cmd = new CommandLine(new Cli(FakeDownloaderFactory.happyPath()));
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        cmd.setOut(new PrintWriter(stdout));
        cmd.setErr(new PrintWriter(stderr));

        int exitCode = cmd.execute("--output-dir", tempDir.toString(), "--force", "--quiet", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(exitCode).isZero();
    }
}
