package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test — verifies {@code --version} prints the expected string.
 * Comprehensive CLI tests are the tester's responsibility.
 */
class CliVersionTest {

    @Test
    void version_flag_prints_version_string() {
        var cmd = new CommandLine(new Cli());
        var sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        int exitCode = cmd.execute("--version");

        assertThat(exitCode).isZero();
        assertThat(sw.toString().trim()).isEqualTo("youtube-downloader 1.0.0");
    }
}
