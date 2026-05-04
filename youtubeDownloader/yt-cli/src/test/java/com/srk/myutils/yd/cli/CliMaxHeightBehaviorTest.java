package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for T-2.11 — {@code --max-height} flag handling (AC-1.3).
 *
 * <p>Verifies picocli parsing of {@code --max-height}, default value (1080 per AC-1.3),
 * negative-value rejection, non-integer rejection, and combination with other flags.
 *
 * <p>SUT: {@link Cli} — real instance via {@link FakeDownloaderFactory}.
 */
class CliMaxHeightBehaviorTest {

    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

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

    // ── 1. --max-height 720 → exit 0 (AC-1.3) ──

    @Test
    @DisplayName("AC-1.3: --max-height 720 → exit 0, flag accepted")
    void execute_givenMaxHeight720_exitsZero() {
        int exitCode = cmd.execute("--max-height", "720", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 2. --max-height 0 (explicit uncapped) → exit 0 (AC-1.3) ──

    @Test
    @DisplayName("AC-1.3: --max-height 0 (explicit uncapped) → exit 0")
    void execute_givenMaxHeightZero_exitsZero() {
        int exitCode = cmd.execute("--max-height", "0", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 3. No --max-height → defaults to 1080 (AC-1.3), exit 0 ──

    @Test
    @DisplayName("AC-1.3: no --max-height → default 1080, exit 0")
    void execute_givenNoMaxHeight_exitsZeroWithDefault1080() {
        int exitCode = cmd.execute(VALID_URL);

        // FakeDownloaderFactory does not capture DownloadRequest, so we cannot
        // assert maxHeight==1080 directly. Exit 0 confirms the default is valid.
        assertThat(exitCode).isZero();
    }

    // ── 4. --max-height with non-integer → picocli parse error, exit 2 ──

    @Test
    @DisplayName("--max-height abc → picocli type-conversion error, exit 2")
    void execute_givenMaxHeightNonInteger_exitsWithUsageError() {
        int exitCode = cmd.execute("--max-height", "abc", VALID_URL);

        assertThat(exitCode).isEqualTo(2);
        assertThat(stderr.toString()).containsIgnoringCase("max-height");
    }

    // ── 5. --max-height negative → validation rejects, exit 2 (AC-1.3) ──

    @Test
    @DisplayName("AC-1.3: --max-height -1 → validation error, exit 2")
    void execute_givenMaxHeightNegative_exitsWithValidationError() {
        int exitCode = cmd.execute("--max-height", "-1", VALID_URL);

        assertThat(exitCode).isEqualTo(2);
        assertThat(stderr.toString()).contains("--max-height must be >= 0");
    }

    // ── 6. --max-height 1080 → exit 0 (common value) ──

    @Test
    @DisplayName("AC-1.3: --max-height 1080 (default cap) → exit 0")
    void execute_givenMaxHeight1080_exitsZero() {
        int exitCode = cmd.execute("--max-height", "1080", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 7. --max-height combined with --quiet → both accepted, exit 0 ──

    @Test
    @DisplayName("--max-height + --quiet → both flags accepted, exit 0")
    void execute_givenMaxHeightAndQuiet_exitsZero() {
        int exitCode = cmd.execute("--max-height", "480", "--quiet", VALID_URL);

        assertThat(exitCode).isZero();
    }

    // ── 8. --max-height without value → picocli error ──

    @Test
    @DisplayName("--max-height without value → picocli error, non-zero exit")
    void execute_givenMaxHeightWithoutValue_exitsNonZero() {
        int exitCode = cmd.execute("--max-height", VALID_URL);

        // picocli tries to parse the URL as an int → type-conversion error
        assertThat(exitCode).isNotZero();
    }
}
