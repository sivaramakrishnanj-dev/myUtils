package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.CaptionDownloader;
import com.srk.myutils.yd.core.FormatSelector;
import com.srk.myutils.yd.core.InnerTubeClient;
import com.srk.myutils.yd.core.StreamDownloader;
import com.srk.myutils.yd.core.ThumbnailDownloader;
import com.srk.myutils.yd.core.UrlParser;
import com.srk.myutils.yd.core.YoutubeDownloader;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for T-5.3 — {@code --debug} polish and OBS-1 fix.
 *
 * <p>Verifies that without {@code --debug}, failure output is exactly one line
 * with no stack trace (AC-5.1, AC-5.4). With {@code --debug}, the stack trace
 * is printed to stderr (AC-5.5, AC-10.5).
 *
 * <p>SUT: {@link Cli} — real instance. picocli {@link CommandLine} with
 * {@link StringWriter} for stderr capture.
 */
class CliDebugBehaviorTest {

    private CommandLine cmd;
    private StringWriter stderr;

    @BeforeEach
    void setUp() {
        cmd = new CommandLine(new Cli(FakeDownloaderFactory.happyPath()));
        stderr = new StringWriter();
        cmd.setOut(new PrintWriter(new StringWriter()));
        cmd.setErr(new PrintWriter(stderr));
    }

    // ── 1. Invalid URL without --debug → exit 2, no stack trace frames ──

    @Test
    @DisplayName("AC-5.4: invalid URL without --debug → exit 2, stderr has error but NO stack trace")
    void execute_givenInvalidUrlWithoutDebug_noStackTraceOnStderr() {
        int exitCode = cmd.execute("not-a-youtube-url");

        assertThat(exitCode).isEqualTo(2);
        String errOutput = stderr.toString();
        assertThat(errOutput).contains("Error: args:");
        assertThat(errOutput).doesNotContain("com.srk.myutils");
        assertThat(errOutput).doesNotContain("\tat ");
    }

    // ── 2. Invalid URL with --debug → exit 2, error message AND stack trace ──

    @Test
    @DisplayName("AC-5.5: invalid URL with --debug → stderr has error AND stack trace frames")
    void execute_givenInvalidUrlWithDebug_stackTraceOnStderr() {
        int exitCode = cmd.execute("--debug", "not-a-youtube-url");

        assertThat(exitCode).isEqualTo(2);
        String errOutput = stderr.toString();
        assertThat(errOutput).contains("Error: args:");
        assertThat(errOutput).contains("com.srk.myutils");
        assertThat(errOutput).contains("\tat ");
    }

    // ── 3. Single-line mode: first meaningful line is the error, no frame lines after ──

    @Test
    @DisplayName("AC-5.1: without --debug, first line matches 'Error: args: ...' and no frame lines follow")
    void execute_givenInvalidUrlWithoutDebug_singleErrorLineNoFrames() {
        cmd.execute("not-a-youtube-url");

        String[] lines = stderr.toString().trim().split("\n");
        assertThat(lines[0]).matches("Error: args: .+");
        for (int i = 1; i < lines.length; i++) {
            assertThat(lines[i].trim()).doesNotStartWith("at ");
        }
    }

    // ── 4. Multi-line debug mode: stack trace contains UrlParser.parse frame ──

    @Test
    @DisplayName("AC-5.5: --debug mode stack trace contains 'at com.srk.myutils.yd.core.UrlParser.parse'")
    void execute_givenDebugAndInvalidUrl_stackTraceContainsUrlParserFrame() {
        cmd.execute("--debug", "not-a-youtube-url");

        assertThat(stderr.toString()).contains("at com.srk.myutils.yd.core.UrlParser.parse");
    }

    // ── 5. --debug --quiet still prints stack trace (AC-5.5 + AC-4.4) ──

    @Test
    @DisplayName("AC-5.5 + AC-4.4: --debug --quiet → stack trace still printed (--quiet does not suppress errors)")
    void execute_givenDebugAndQuiet_stackTraceStillPrinted() {
        int exitCode = cmd.execute("--debug", "--quiet", "not-a-youtube-url");

        assertThat(exitCode).isEqualTo(2);
        String errOutput = stderr.toString();
        assertThat(errOutput).contains("Error: args:");
        assertThat(errOutput).contains("\tat ");
    }

    // ── 6. NetworkException without --debug → one line only ──

    @Test
    @DisplayName("AC-5.4: NetworkException without --debug → single error line, no stack trace")
    void execute_givenNetworkFailureWithoutDebug_singleLineNoStackTrace() {
        // Create a downloader whose InnerTubeClient throws IOException → NetworkException
        OkHttpClient throwingHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> { throw new IOException("simulated DNS failure"); })
                .build();
        YoutubeDownloader throwingDownloader = new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(throwingHttp),
                new FormatSelector(),
                new StreamDownloader(throwingHttp),
                req -> null,
                new CaptionDownloader(throwingHttp),
                new ThumbnailDownloader(throwingHttp));

        CommandLine networkCmd = new CommandLine(new Cli(throwingDownloader));
        StringWriter networkStderr = new StringWriter();
        networkCmd.setOut(new PrintWriter(new StringWriter()));
        networkCmd.setErr(new PrintWriter(networkStderr));

        int exitCode = networkCmd.execute("https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(exitCode).isEqualTo(10);
        String errOutput = networkStderr.toString();
        assertThat(errOutput).contains("Error:");
        assertThat(errOutput).doesNotContain("\tat ");
        assertThat(errOutput).doesNotContain("com.srk.myutils");
    }

    // ── 7. Exactly one 'Error:' line when not --debug ──

    @Test
    @DisplayName("AC-5.1: without --debug, stderr contains exactly one line starting with 'Error:'")
    void execute_givenInvalidUrlWithoutDebug_exactlyOneErrorLine() {
        cmd.execute("not-a-youtube-url");

        long errorLineCount = stderr.toString().lines()
                .filter(line -> line.startsWith("Error:"))
                .count();
        assertThat(errorLineCount).isEqualTo(1);
    }

    // ── 8. Regression: CliErrorHandlingBehaviorTest assertions still hold ──

    @Test
    @DisplayName("Regression: no-debug path produces no 'Exception' class name leak on stderr")
    void execute_givenInvalidUrlWithoutDebug_noExceptionClassNameLeak() {
        cmd.execute("not-a-youtube-url");

        String errOutput = stderr.toString();
        // OBS-1 fix: stack trace (which contains exception class names) must not appear
        assertThat(errOutput).doesNotContain("UrlParseException");
        assertThat(errOutput).doesNotContain("Exception\n");
        assertThat(errOutput).doesNotContain("Caused by:");
    }
}
