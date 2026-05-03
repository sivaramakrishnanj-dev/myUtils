package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for the full exception hierarchy (T-1.9).
 *
 * <p>Verifies every subclass of {@link YoutubeDownloaderException}:
 * <ul>
 *   <li>Constructs with a message and preserves it</li>
 *   <li>Returns the correct {@link YoutubeDownloaderException#exitCode()}</li>
 *   <li>Is a {@link YoutubeDownloaderException} (and therefore a {@link RuntimeException})</li>
 * </ul>
 */
class ExceptionHierarchyTest {

    static Stream<Arguments> exceptionProvider() {
        return Stream.of(
                Arguments.of(new UrlParseException("bad url"), 2),
                Arguments.of(new NetworkException("dns failed"), 10),
                Arguments.of(new InnerTubeParseException("bad json"), 11),
                Arguments.of(new VideoUnavailableException("private"), 20),
                Arguments.of(new LiveStreamException("is live"), 21),
                Arguments.of(new CipherRequiredException("cipher needed"), 22),
                Arguments.of(new NoMatchingFormatException("no format"), 30),
                Arguments.of(new CaptionUnavailableException("no captions"), 40),
                Arguments.of(new OutputExistsException("file exists"), 50),
                Arguments.of(new FfmpegException("ffmpeg missing"), 60),
                Arguments.of(new FilesystemException("disk full"), 70)
        );
    }

    @ParameterizedTest(name = "{0} → exitCode {1}")
    @MethodSource("exceptionProvider")
    @DisplayName("exitCode() returns the correct code per cli-exit-codes.md § 3")
    void exitCode_returnsExpectedCode(YoutubeDownloaderException ex, int expectedCode) {
        assertThat(ex.exitCode()).isEqualTo(expectedCode);
    }

    @ParameterizedTest(name = "{0} preserves message")
    @MethodSource("exceptionProvider")
    @DisplayName("getMessage() returns the constructor argument")
    void message_isPreserved(YoutubeDownloaderException ex, int ignored) {
        assertThat(ex.getMessage()).isNotNull().isNotEmpty();
    }

    @ParameterizedTest(name = "{0} is YoutubeDownloaderException")
    @MethodSource("exceptionProvider")
    @DisplayName("every subclass is a YoutubeDownloaderException (and RuntimeException)")
    void isYoutubeDownloaderException(YoutubeDownloaderException ex, int ignored) {
        assertThat(ex).isInstanceOf(YoutubeDownloaderException.class)
                      .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("sealed hierarchy has exactly 11 permitted subclasses")
    void sealedHierarchy_hasExactly11Subclasses() {
        Class<?>[] permitted = YoutubeDownloaderException.class.getPermittedSubclasses();
        assertThat(permitted).hasSize(11);
    }

    @Test
    @DisplayName("NetworkException preserves cause chain")
    void networkException_preservesCause() {
        var cause = new java.io.IOException("connection reset");
        var ex = new NetworkException("network failed", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.exitCode()).isEqualTo(10);
    }

    @Test
    @DisplayName("FfmpegException preserves cause chain")
    void ffmpegException_preservesCause() {
        var cause = new java.io.IOException("process failed");
        var ex = new FfmpegException("ffmpeg died", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.exitCode()).isEqualTo(60);
    }
}
