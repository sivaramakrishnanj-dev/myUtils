package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Comprehensive tests for {@link ErrorMapper} — all 11 domain exception categories,
 * non-domain throwables, null input, and message-format compliance with
 * {@code cli-exit-codes.md § 1} (AC-5.1, AC-5.2, INV-11).
 */
class ErrorMapperBehaviorTest {

    // ── 1. Parameterized: every YDE subclass → correct (exitCode, category prefix) ──

    static Stream<Arguments> domainExceptions() {
        return Stream.of(
                Arguments.of(new UrlParseException("bad url"),                          2,  "args"),
                Arguments.of(new NetworkException("dns failed"),                        10, "network"),
                Arguments.of(new InnerTubeParseException("missing field"),              11, "innertube"),
                Arguments.of(new VideoUnavailableException("private video"),            20, "unavailable"),
                Arguments.of(new LiveStreamException("live not supported"),             21, "live"),
                Arguments.of(new CipherRequiredException("cipher required"),            22, "cipher"),
                Arguments.of(new NoMatchingFormatException("no match"),                 30, "format"),
                Arguments.of(new CaptionUnavailableException("no tracks"),              40, "captions"),
                Arguments.of(new OutputExistsException("file exists"),                  50, "output"),
                Arguments.of(new FfmpegException("ffmpeg missing"),                     60, "ffmpeg"),
                Arguments.of(new FilesystemException("disk full"),                      70, "filesystem")
        );
    }

    @ParameterizedTest(name = "{2} → exit {1}")
    @MethodSource("domainExceptions")
    @DisplayName("AC-5.2: domain exception maps to correct exit code and category")
    void map_givenDomainException_returnsCorrectExitCodeAndCategory(
            YoutubeDownloaderException ex, int expectedCode, String expectedCategory) {

        ErrorReport report = ErrorMapper.map(ex);

        assertThat(report.exitCode()).isEqualTo(expectedCode);
        assertThat(report.message()).startsWith("Error: " + expectedCategory + ": ");
    }

    @ParameterizedTest(name = "{2} message contains detail")
    @MethodSource("domainExceptions")
    @DisplayName("AC-5.1: domain exception message contains the exception detail text")
    void map_givenDomainException_messageContainsExceptionDetail(
            YoutubeDownloaderException ex, int expectedCode, String expectedCategory) {

        ErrorReport report = ErrorMapper.map(ex);

        assertThat(report.message()).isEqualTo("Error: " + expectedCategory + ": " + ex.getMessage());
    }

    // ── 2. Non-YDE throwables → (1, "Error: internal: ...") ──

    @Test
    @DisplayName("Non-YDE RuntimeException maps to exit 1 with internal category")
    void map_givenRuntimeException_returnsInternalCategory() {
        ErrorReport report = ErrorMapper.map(new RuntimeException("boom"));

        assertThat(report.exitCode()).isEqualTo(1);
        assertThat(report.message()).startsWith("Error: internal: ");
        assertThat(report.message()).contains("boom");
    }

    @Test
    @DisplayName("Non-YDE IOException maps to exit 1 with internal category")
    void map_givenIOException_returnsInternalCategory() {
        ErrorReport report = ErrorMapper.map(new IOException("disk error"));

        assertThat(report.exitCode()).isEqualTo(1);
        assertThat(report.message()).startsWith("Error: internal: ");
        assertThat(report.message()).contains("disk error");
    }

    @Test
    @DisplayName("NullPointerException maps to exit 1 with internal category")
    void map_givenNullPointerException_returnsInternalCategory() {
        ErrorReport report = ErrorMapper.map(new NullPointerException("npe detail"));

        assertThat(report.exitCode()).isEqualTo(1);
        assertThat(report.message()).startsWith("Error: internal: ");
        assertThat(report.message()).contains("npe detail");
    }

    @Test
    @DisplayName("Non-YDE throwable with null message handled gracefully")
    void map_givenThrowableWithNullMessage_returnsInternalWithNullText() {
        ErrorReport report = ErrorMapper.map(new RuntimeException((String) null));

        assertThat(report.exitCode()).isEqualTo(1);
        assertThat(report.message()).startsWith("Error: internal: ");
    }

    // ── 3. null Throwable → NullPointerException ──

    @Test
    @DisplayName("null Throwable input throws NullPointerException")
    void map_givenNull_throwsNullPointerException() {
        assertThatNullPointerException().isThrownBy(() -> ErrorMapper.map(null));
    }

    // ── 4. Message format compliance with cli-exit-codes.md § 1 ──

    @Test
    @DisplayName("AC-5.1: message format is exactly 'Error: <category>: <detail>'")
    void map_givenDomainException_messageMatchesAC51Format() {
        ErrorReport report = ErrorMapper.map(new NetworkException("host unreachable"));

        assertThat(report.message()).matches("Error: [a-z]+: .+");
    }

    @Test
    @DisplayName("Internal error message includes class name for diagnostics")
    void map_givenNonYDE_messageIncludesClassName() {
        ErrorReport report = ErrorMapper.map(new IllegalStateException("bad state"));

        assertThat(report.message()).contains("IllegalStateException");
        assertThat(report.message()).contains("bad state");
    }
}
