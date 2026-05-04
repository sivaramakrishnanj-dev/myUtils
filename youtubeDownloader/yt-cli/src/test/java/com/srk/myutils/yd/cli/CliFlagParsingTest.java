package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for T-1.11 — CLI flag parsing of URL, --debug, --quiet.
 */
class CliFlagParsingTest {

    @TempDir
    Path tempDir;

    @Test
    void execute_givenValidUrl_exitsZero() {
        var cmd = new CommandLine(new Cli(FakeDownloaderFactory.happyPath()));
        int exitCode = cmd.execute("--output-dir", tempDir.toString(),
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(exitCode).isZero();
    }
}
