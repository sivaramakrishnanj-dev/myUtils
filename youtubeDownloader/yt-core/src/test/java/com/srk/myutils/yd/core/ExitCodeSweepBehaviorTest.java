package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * T-5.2 — Exit-code sweep behavior tests (tester).
 *
 * <p>Extends the implementer's {@link ExitCodeSweepTest} with:
 * <ul>
 *   <li>Regression: DownloadContext.deleteRecursively throws FilesystemException (exit 70)</li>
 *   <li>ErrorMapper.map() chain verification for all 11 exit codes with cause-chain preservation</li>
 *   <li>exitCode() method on each exception returns the documented code</li>
 * </ul>
 *
 * @see ExitCodeSweepTest implementer's characterization tests
 */
@DisplayName("T-5.2 Exit-Code Sweep Behavior")
class ExitCodeSweepBehaviorTest {

    // ── 1. Regression: DownloadContext deletion failure → FilesystemException (exit 70) ──

    @Nested
    @DisplayName("DownloadContext.deleteRecursively regression — FilesystemException (exit 70)")
    class DeleteRecursivelyRegression {

        @Test
        @DisplayName("deleteRecursively failure throws FilesystemException with exitCode 70")
        void close_givenUndeletableFile_throwsFilesystemException(@TempDir Path outputDir) throws IOException {
            DownloadContext ctx = DownloadContext.create(outputDir, VideoId.of("dQw4w9WgXcQ"));
            Path file = ctx.tempFile("locked.part");
            Files.writeString(file, "data");

            // Make parent non-writable so deletion fails
            ctx.tempDir().toFile().setWritable(false);
            ctx.markSuccess();

            try {
                Throwable t = catchThrowable(ctx::close);

                // The fix: FilesystemException (not bare RuntimeException)
                assertThat(t).isInstanceOf(FilesystemException.class);
                assertThat(((FilesystemException) t).exitCode()).isEqualTo(70);

                // ErrorMapper maps it correctly
                ErrorReport report = ErrorMapper.map(t);
                assertThat(report.exitCode()).isEqualTo(70);
                assertThat(report.message()).startsWith("Error: filesystem: ");
            } finally {
                ctx.tempDir().toFile().setWritable(true);
            }
        }

        @Test
        @DisplayName("FilesystemException from deleteRecursively wraps original IOException as cause")
        void close_givenUndeletableFile_causeIsIOException(@TempDir Path outputDir) throws IOException {
            DownloadContext ctx = DownloadContext.create(outputDir, VideoId.of("dQw4w9WgXcQ"));
            Path file = ctx.tempFile("locked.part");
            Files.writeString(file, "data");

            ctx.tempDir().toFile().setWritable(false);
            ctx.markSuccess();

            try {
                Throwable t = catchThrowable(ctx::close);

                assertThat(t).isInstanceOf(FilesystemException.class)
                        .hasCauseInstanceOf(IOException.class);
            } finally {
                ctx.tempDir().toFile().setWritable(true);
            }
        }
    }

    // ── 2. ErrorMapper.map() chain: each exception → correct (exitCode, category) ──

    @Nested
    @DisplayName("ErrorMapper.map() chain — all 11 exit codes (cli-exit-codes.md § 3)")
    class ErrorMapperChain {

        static Stream<Arguments> allExceptions() {
            return Stream.of(
                    Arguments.of(new UrlParseException("bad"),                          2,  "args"),
                    Arguments.of(new NetworkException("timeout"),                       10, "network"),
                    Arguments.of(new InnerTubeParseException("missing field"),          11, "innertube"),
                    Arguments.of(new VideoUnavailableException("private"),              20, "unavailable"),
                    Arguments.of(new LiveStreamException("live"),                       21, "live"),
                    Arguments.of(new CipherRequiredException("sig"),                    22, "cipher"),
                    Arguments.of(new NoMatchingFormatException("none"),                 30, "format"),
                    Arguments.of(new CaptionUnavailableException("no tracks"),          40, "captions"),
                    Arguments.of(new OutputExistsException("exists"),                   50, "output"),
                    Arguments.of(new FfmpegException("not found"),                      60, "ffmpeg"),
                    Arguments.of(new FilesystemException("disk full"),                  70, "filesystem")
            );
        }

        @ParameterizedTest(name = "{2} → exit {1}")
        @MethodSource("allExceptions")
        @DisplayName("exitCode() on exception matches ErrorMapper.map().exitCode()")
        void exitCode_matchesErrorMapperOutput(YoutubeDownloaderException ex, int code, String category) {
            assertThat(ex.exitCode()).isEqualTo(code);

            ErrorReport report = ErrorMapper.map(ex);
            assertThat(report.exitCode()).isEqualTo(code);
            assertThat(report.message()).isEqualTo("Error: " + category + ": " + ex.getMessage());
        }

        @ParameterizedTest(name = "{2} message format")
        @MethodSource("allExceptions")
        @DisplayName("AC-5.1: message matches 'Error: <category>: <detail>'")
        void messageFormat_matchesSpec(YoutubeDownloaderException ex, int code, String category) {
            ErrorReport report = ErrorMapper.map(ex);

            assertThat(report.message()).matches("Error: [a-z]+: .+");
        }

        @Test
        @DisplayName("non-domain throwable → exit 1, 'Error: internal: ...'")
        void nonDomainThrowable_mapsToExit1() {
            ErrorReport report = ErrorMapper.map(new IllegalStateException("unexpected"));

            assertThat(report.exitCode()).isEqualTo(1);
            assertThat(report.message()).startsWith("Error: internal: ");
        }

        @Test
        @DisplayName("FilesystemException with cause preserves cause through ErrorMapper")
        void filesystemExceptionWithCause_preservesCause() {
            IOException cause = new IOException("permission denied");
            FilesystemException ex = new FilesystemException("/tmp/out: write failed", cause);

            ErrorReport report = ErrorMapper.map(ex);

            assertThat(report.exitCode()).isEqualTo(70);
            assertThat(report.message()).contains("/tmp/out: write failed");
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    // ── 3. exitCode() method correctness on each exception class ──

    @Nested
    @DisplayName("exitCode() method — each exception returns its documented code")
    class ExitCodeMethod {

        @Test
        @DisplayName("UrlParseException.exitCode() == 2")
        void urlParseException_exitCode2() {
            assertThat(new UrlParseException("x").exitCode()).isEqualTo(2);
        }

        @Test
        @DisplayName("NetworkException.exitCode() == 10")
        void networkException_exitCode10() {
            assertThat(new NetworkException("x").exitCode()).isEqualTo(10);
        }

        @Test
        @DisplayName("InnerTubeParseException.exitCode() == 11")
        void innerTubeParseException_exitCode11() {
            assertThat(new InnerTubeParseException("x").exitCode()).isEqualTo(11);
        }

        @Test
        @DisplayName("VideoUnavailableException.exitCode() == 20")
        void videoUnavailableException_exitCode20() {
            assertThat(new VideoUnavailableException("x").exitCode()).isEqualTo(20);
        }

        @Test
        @DisplayName("LiveStreamException.exitCode() == 21")
        void liveStreamException_exitCode21() {
            assertThat(new LiveStreamException("x").exitCode()).isEqualTo(21);
        }

        @Test
        @DisplayName("CipherRequiredException.exitCode() == 22")
        void cipherRequiredException_exitCode22() {
            assertThat(new CipherRequiredException("x").exitCode()).isEqualTo(22);
        }

        @Test
        @DisplayName("NoMatchingFormatException.exitCode() == 30")
        void noMatchingFormatException_exitCode30() {
            assertThat(new NoMatchingFormatException("x").exitCode()).isEqualTo(30);
        }

        @Test
        @DisplayName("CaptionUnavailableException.exitCode() == 40")
        void captionUnavailableException_exitCode40() {
            assertThat(new CaptionUnavailableException("x").exitCode()).isEqualTo(40);
        }

        @Test
        @DisplayName("OutputExistsException.exitCode() == 50")
        void outputExistsException_exitCode50() {
            assertThat(new OutputExistsException("x").exitCode()).isEqualTo(50);
        }

        @Test
        @DisplayName("FfmpegException.exitCode() == 60")
        void ffmpegException_exitCode60() {
            assertThat(new FfmpegException("x").exitCode()).isEqualTo(60);
        }

        @Test
        @DisplayName("FilesystemException.exitCode() == 70")
        void filesystemException_exitCode70() {
            assertThat(new FilesystemException("x").exitCode()).isEqualTo(70);
        }
    }
}
