package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive CLI flag-parsing tests for T-1.11 (AC-5.1, AC-5.4, AC-5.5).
 *
 * <p>Tests the picocli-level parsing of {@code <URL>}, {@code --debug}, and
 * {@code --quiet} flags. Does NOT test error-mapping or stack-trace suppression
 * (those are T-1.12 / T-1.13 scope).
 *
 * <p>SUT: {@link Cli} — real instance, no mocks.
 */
class CliFlagParsingBehaviorTest {

    private CommandLine cmd;
    private Cli cli;
    private StringWriter stdout;
    private StringWriter stderr;

    @BeforeEach
    void setUp() {
        cli = new Cli(FakeDownloaderFactory.happyPath());
        cmd = new CommandLine(cli);
        stdout = new StringWriter();
        stderr = new StringWriter();
        cmd.setOut(new PrintWriter(stdout));
        cmd.setErr(new PrintWriter(stderr));
    }

    // ── 1. Valid URL invocation (AC-1.1 four shapes) ──

    @Nested
    @DisplayName("Valid URL shapes → exit 0")
    class ValidUrlShapes {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("AC-1.1: accepted URL shapes exit 0")
        @ValueSource(strings = {
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://youtu.be/dQw4w9WgXcQ",
                "https://www.youtube.com/shorts/dQw4w9WgXcQ",
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        })
        void execute_givenValidUrlShape_exitsZero(String url) {
            int exitCode = cmd.execute(url);

            assertThat(exitCode).isZero();
        }
    }

    // ── 2. Missing URL → picocli error ──

    @Test
    @DisplayName("Missing URL → non-zero exit; stderr contains 'Missing required parameter'")
    void execute_givenNoArgs_exitsNonZeroWithMissingParamMessage() {
        int exitCode = cmd.execute();

        assertThat(exitCode).isNotZero();
        assertThat(stderr.toString()).contains("Missing required parameter");
    }

    // ── 3. Invalid URL → UrlParseException bubbles; non-zero exit ──

    @Test
    @DisplayName("Invalid URL (non-YouTube) → non-zero exit")
    void execute_givenNonYoutubeUrl_exitsNonZero() {
        int exitCode = cmd.execute("https://google.com");

        assertThat(exitCode).isNotZero();
    }

    // ── 4. --debug flag ──

    @Test
    @DisplayName("AC-5.4: --debug flag sets isDebug() true; exit 0")
    void execute_givenDebugFlag_setsDebugTrue() {
        int exitCode = cmd.execute("--debug", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(exitCode).isZero();
        assertThat(cli.isDebug()).isTrue();
    }

    // ── 5. --quiet flag ──

    @Test
    @DisplayName("AC-5.5: --quiet flag sets isQuiet() true; exit 0")
    void execute_givenQuietFlag_setsQuietTrue() {
        int exitCode = cmd.execute("--quiet", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(exitCode).isZero();
        assertThat(cli.isQuiet()).isTrue();
    }

    // ── 6. Both --debug and --quiet ──

    @Test
    @DisplayName("--debug and --quiet together → both flags set; exit 0")
    void execute_givenDebugAndQuiet_setsBothFlags() {
        int exitCode = cmd.execute("--debug", "--quiet", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(exitCode).isZero();
        assertThat(cli.isDebug()).isTrue();
        assertThat(cli.isQuiet()).isTrue();
    }

    // ── 7. Neither flag → defaults false ──

    @Test
    @DisplayName("No flags → isDebug() and isQuiet() both false")
    void execute_givenNoFlags_defaultsBothFalse() {
        int exitCode = cmd.execute("https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(exitCode).isZero();
        assertThat(cli.isDebug()).isFalse();
        assertThat(cli.isQuiet()).isFalse();
    }

    // ── 8. Flags in any order ──

    @Test
    @DisplayName("--debug --quiet URL in any order parseable")
    void execute_givenFlagsBeforeUrl_parsesCorrectly() {
        int exitCode = cmd.execute("--quiet", "--debug", "https://youtu.be/dQw4w9WgXcQ");

        assertThat(exitCode).isZero();
        assertThat(cli.isDebug()).isTrue();
        assertThat(cli.isQuiet()).isTrue();
    }

    // ── 9. Unknown flag → picocli usage error ──

    @Test
    @DisplayName("Unknown flag --foo → non-zero exit; stderr non-empty")
    void execute_givenUnknownFlag_exitsNonZero() {
        int exitCode = cmd.execute("--foo", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(exitCode).isNotZero();
        assertThat(stderr.toString()).isNotEmpty();
    }

    // ── 10. URL before flags vs flags before URL ──

    @Test
    @DisplayName("URL before flags — picocli handles positional + trailing options")
    void execute_givenUrlBeforeFlags_parsesCorrectly() {
        int exitCode = cmd.execute("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "--debug");

        assertThat(exitCode).isZero();
        assertThat(cli.isDebug()).isTrue();
    }

    // ── 11. --help with URL arg → help short-circuits, exit 0 ──

    @Test
    @DisplayName("--help with URL → help short-circuits; exit 0; no URL processing")
    void execute_givenHelpWithUrl_exitsZeroWithUsage() {
        int exitCode = cmd.execute("--help", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(exitCode).isZero();
        assertThat(stdout.toString()).contains("Usage:");
    }

    // ── 12. --version with URL arg → version short-circuits ──

    @Test
    @DisplayName("--version with URL → version short-circuits; exit 0")
    void execute_givenVersionWithUrl_exitsZeroWithVersion() {
        int exitCode = cmd.execute("--version", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(exitCode).isZero();
        assertThat(stdout.toString().trim()).contains("youtube-downloader 1.0.0");
    }

    // ── Additional edge cases ──

    @Test
    @DisplayName("URL with extra query params (AC-1.1) → exit 0")
    void execute_givenUrlWithExtraQueryParams_exitsZero() {
        int exitCode = cmd.execute("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf");

        assertThat(exitCode).isZero();
    }
}
