package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;

/**
 * Characterization test for T-2.11 — {@code --max-height} flag parsing.
 *
 * <p>Verifies that the CLI accepts {@code --max-height} and exits 0
 * on a valid URL with the happy-path fake downloader.
 */
class CliMaxHeightTest {

    @TempDir
    Path tempDir;

    @Test
    void execute_givenMaxHeightFlag_exitsZero() {
        var cmd = new CommandLine(new Cli(FakeDownloaderFactory.happyPath()));
        int exitCode = cmd.execute("--output-dir", tempDir.toString(), "--force",
                "--max-height", "720",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(exitCode).isZero();
    }
}
