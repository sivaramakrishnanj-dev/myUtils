package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behaviour tests for {@link OutputWriter} — AC-3.1..AC-3.6,
 * NFR-MAX-FILENAME-LENGTH, NFR-MIN-DISK-FREE, INV-14.
 */
class OutputWriterBehaviorTest {

    private static final VideoId VIDEO_ID = VideoId.of("dQw4w9WgXcQ");

    private static VideoDetails details(String title) {
        return new VideoDetails(VIDEO_ID, title, false, false, Optional.empty());
    }

    private static OutputWriter writer(OutputConfig config) {
        return new OutputWriter(config);
    }

    // ── sanitizeTitle (AC-3.3) ──────────────────────────────────────────

    @Nested
    @DisplayName("sanitizeTitle (AC-3.3)")
    class SanitizeTitleTest {

        @Test
        void sanitizeTitle_givenForwardSlash_removesIt() {
            assertThat(OutputWriter.sanitizeTitle("a/b")).isEqualTo("ab");
        }

        @Test
        void sanitizeTitle_givenBackslash_removesIt() {
            assertThat(OutputWriter.sanitizeTitle("a\\b")).isEqualTo("ab");
        }

        @Test
        void sanitizeTitle_givenColon_removesIt() {
            assertThat(OutputWriter.sanitizeTitle("a:b")).isEqualTo("ab");
        }

        @Test
        void sanitizeTitle_givenAsterisk_removesIt() {
            assertThat(OutputWriter.sanitizeTitle("a*b")).isEqualTo("ab");
        }

        @Test
        void sanitizeTitle_givenQuestionMark_removesIt() {
            assertThat(OutputWriter.sanitizeTitle("a?b")).isEqualTo("ab");
        }

        @Test
        void sanitizeTitle_givenDoubleQuote_removesIt() {
            assertThat(OutputWriter.sanitizeTitle("a\"b")).isEqualTo("ab");
        }

        @Test
        void sanitizeTitle_givenLessThan_removesIt() {
            assertThat(OutputWriter.sanitizeTitle("a<b")).isEqualTo("ab");
        }

        @Test
        void sanitizeTitle_givenGreaterThan_removesIt() {
            assertThat(OutputWriter.sanitizeTitle("a>b")).isEqualTo("ab");
        }

        @Test
        void sanitizeTitle_givenPipe_removesIt() {
            assertThat(OutputWriter.sanitizeTitle("a|b")).isEqualTo("ab");
        }

        @Test
        void sanitizeTitle_givenAsciiControlChars_removesAll() {
            // 0x00 (NUL), 0x0A (LF), 0x1F, 0x7F (DEL) — all removed (not replaced)
            assertThat(OutputWriter.sanitizeTitle("a\u0000b\nc\u001Fd\u007Fe"))
                    .isEqualTo("abcde");
        }

        @Test
        void sanitizeTitle_givenWhitespaceRuns_collapsesToSingleSpace() {
            // tabs (0x09) are control chars removed first; only spaces collapse
            assertThat(OutputWriter.sanitizeTitle("hello   world   there"))
                    .isEqualTo("hello world there");
        }

        @Test
        void sanitizeTitle_givenLeadingTrailingDotsAndWhitespace_trims() {
            assertThat(OutputWriter.sanitizeTitle("...  hello  ...")).isEqualTo("hello");
        }

        @Test
        void sanitizeTitle_givenLeadingDots_trims() {
            assertThat(OutputWriter.sanitizeTitle("..title")).isEqualTo("title");
        }

        @Test
        void sanitizeTitle_givenTrailingDots_trims() {
            assertThat(OutputWriter.sanitizeTitle("title..")).isEqualTo("title");
        }

        @Test
        void sanitizeTitle_givenLeadingTrailingWhitespace_trims() {
            assertThat(OutputWriter.sanitizeTitle("  hello  ")).isEqualTo("hello");
        }

        @Test
        void sanitizeTitle_givenEmptyResult_fallsBackToVideo() {
            assertThat(OutputWriter.sanitizeTitle("***")).isEqualTo("video");
        }

        @Test
        void sanitizeTitle_givenNull_fallsBackToVideo() {
            assertThat(OutputWriter.sanitizeTitle(null)).isEqualTo("video");
        }

        @Test
        void sanitizeTitle_givenEmptyString_fallsBackToVideo() {
            assertThat(OutputWriter.sanitizeTitle("")).isEqualTo("video");
        }

        @Test
        void sanitizeTitle_givenOnlyDotsAndSpaces_fallsBackToVideo() {
            assertThat(OutputWriter.sanitizeTitle(". . .")).isEqualTo("video");
        }

        @Test
        void sanitizeTitle_givenUnicodeEmoji_preserves() {
            assertThat(OutputWriter.sanitizeTitle("Hello 🌍 World")).isEqualTo("Hello 🌍 World");
        }

        @Test
        void sanitizeTitle_givenCjkCharacters_preserves() {
            assertThat(OutputWriter.sanitizeTitle("你好世界")).isEqualTo("你好世界");
        }

        @Test
        void sanitizeTitle_givenLongTitle_doesNotTruncate() {
            String longTitle = "A".repeat(300);
            assertThat(OutputWriter.sanitizeTitle(longTitle)).hasSize(300);
        }

        @Test
        void sanitizeTitle_givenMixedIllegalAndLegal_removesOnlyIllegal() {
            assertThat(OutputWriter.sanitizeTitle("Hello: World? <Yes>"))
                    .isEqualTo("Hello World Yes");
        }
    }

    // ── truncateFilename (AC-3.4) ───────────────────────────────────────

    @Nested
    @DisplayName("truncateFilename (AC-3.4)")
    class TruncateFilenameTest {

        private static final String SUFFIX = " [dQw4w9WgXcQ]";

        @Test
        void truncateFilename_givenCombinedWithinMax_returnsUnchanged() {
            String base = "Short Title";
            String result = OutputWriter.truncateFilename(base, SUFFIX, 200);

            assertThat(result).isEqualTo(base + SUFFIX);
        }

        @Test
        void truncateFilename_givenCombinedExceedsMax_truncatesTitleFromRight() {
            String base = "A".repeat(200);
            String result = OutputWriter.truncateFilename(base, SUFFIX, 200);

            assertThat(result).hasSize(200);
            assertThat(result).endsWith(SUFFIX);
        }

        @Test
        void truncateFilename_givenCombinedExceedsMax_preservesSuffix() {
            String base = "A".repeat(250);
            String result = OutputWriter.truncateFilename(base, SUFFIX, 200);

            assertThat(result).endsWith(SUFFIX);
            assertThat(result).hasSize(200);
        }

        @Test
        void truncateFilename_givenExactlyMaxLen_returnsUnchanged() {
            int titleLen = 200 - SUFFIX.length();
            String base = "B".repeat(titleLen);
            String result = OutputWriter.truncateFilename(base, SUFFIX, 200);

            assertThat(result).isEqualTo(base + SUFFIX);
            assertThat(result).hasSize(200);
        }

        @Test
        void truncateFilename_givenSuffixAloneLongerThanMax_truncatesSuffix() {
            // Edge: maxLen < suffix length
            String result = OutputWriter.truncateFilename("Title", SUFFIX, 5);

            assertThat(result).hasSize(5);
        }

        @Test
        void truncateFilename_givenEmptyBaseName_returnsSuffixOnly() {
            String result = OutputWriter.truncateFilename("", SUFFIX, 200);

            assertThat(result).isEqualTo(SUFFIX);
        }
    }

    // ── deriveOutputPath ────────────────────────────────────────────────

    @Nested
    @DisplayName("deriveOutputPath")
    class DeriveOutputPathTest {

        @Test
        void deriveOutputPath_givenOutputPath_returnsLiteralWithExtension(@TempDir Path tmp) {
            // AC-3.5: --output "foo.mp4" → use literally with our extension
            Path userPath = tmp.resolve("foo.mp4");
            OutputConfig config = new OutputConfig(Optional.of(userPath), Optional.empty(), false);

            Path result = writer(config).deriveOutputPath(details("Ignored Title"), "mp4");

            assertThat(result).isEqualTo(tmp.resolve("foo.mp4"));
        }

        @Test
        void deriveOutputPath_givenOutputPathWithDifferentExtension_replacesExtension(@TempDir Path tmp) {
            // AC-3.5: user supplies .webm but we want .mp4
            Path userPath = tmp.resolve("myvideo.webm");
            OutputConfig config = new OutputConfig(Optional.of(userPath), Optional.empty(), false);

            Path result = writer(config).deriveOutputPath(details("Ignored"), "mp4");

            assertThat(result.getFileName().toString()).isEqualTo("myvideo.mp4");
        }

        @Test
        void deriveOutputPath_givenOutputPathWithNoExtension_addsExtension(@TempDir Path tmp) {
            Path userPath = tmp.resolve("myvideo");
            OutputConfig config = new OutputConfig(Optional.of(userPath), Optional.empty(), false);

            Path result = writer(config).deriveOutputPath(details("Ignored"), "m4a");

            assertThat(result.getFileName().toString()).isEqualTo("myvideo.m4a");
        }

        @Test
        void deriveOutputPath_givenOutputDir_derivesSanitizedFilename(@TempDir Path tmp) {
            // AC-3.2: --output-dir → <dir>/<sanitized_title> [<videoId>].<ext>
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.of(tmp), false);

            Path result = writer(config).deriveOutputPath(details("Hello World"), "mp4");

            assertThat(result).isEqualTo(tmp.resolve("Hello World [dQw4w9WgXcQ].mp4"));
        }

        @Test
        void deriveOutputPath_givenNeither_usesCwd() {
            // AC-3.1: no --output, no --output-dir → CWD
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), false);

            Path result = writer(config).deriveOutputPath(details("My Video"), "mp4");

            assertThat(result).isEqualTo(Path.of("My Video [dQw4w9WgXcQ].mp4"));
        }

        @Test
        void deriveOutputPath_givenTitleWithIllegalChars_sanitizesBeforeDerivation(@TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.of(tmp), false);

            Path result = writer(config).deriveOutputPath(details("Hello: World?"), "mp4");

            assertThat(result.getFileName().toString())
                    .isEqualTo("Hello World [dQw4w9WgXcQ].mp4");
        }

        @Test
        void deriveOutputPath_givenVeryLongTitle_truncatesToMaxFilenameLength(@TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.of(tmp), false);
            String longTitle = "A".repeat(300);

            Path result = writer(config).deriveOutputPath(details(longTitle), "mp4");

            // Base filename (excluding .mp4) should be ≤ 200
            String fileName = result.getFileName().toString();
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            assertThat(baseName).hasSizeLessThanOrEqualTo(200);
            assertThat(baseName).endsWith("[dQw4w9WgXcQ]");
        }

        @Test
        void deriveOutputPath_givenOutputPathAndOutputDir_outputPathTakesPrecedence(@TempDir Path tmp) {
            // When both are present, --output wins
            Path userPath = tmp.resolve("explicit.mp4");
            Path dir = tmp.resolve("subdir");
            OutputConfig config = new OutputConfig(Optional.of(userPath), Optional.of(dir), false);

            Path result = writer(config).deriveOutputPath(details("Title"), "mp4");

            assertThat(result).isEqualTo(tmp.resolve("explicit.mp4"));
        }
    }

    // ── assertNotExistsOrForce (INV-14, AC-3.6) ────────────────────────

    @Nested
    @DisplayName("assertNotExistsOrForce (INV-14, AC-3.6)")
    class AssertNotExistsOrForceTest {

        @Test
        void assertNotExistsOrForce_givenTargetDoesNotExist_doesNotThrow(@TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), false);
            Path target = tmp.resolve("nonexistent.mp4");

            assertThatCode(() -> writer(config).assertNotExistsOrForce(target))
                    .doesNotThrowAnyException();
        }

        @Test
        void assertNotExistsOrForce_givenTargetExistsAndForceIsFalse_throwsOutputExistsException(
                @TempDir Path tmp) throws IOException {
            Path target = tmp.resolve("existing.mp4");
            Files.createFile(target);
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), false);

            assertThatThrownBy(() -> writer(config).assertNotExistsOrForce(target))
                    .isInstanceOf(OutputExistsException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        void assertNotExistsOrForce_givenOutputExistsException_hasExitCode50(
                @TempDir Path tmp) throws IOException {
            Path target = tmp.resolve("existing.mp4");
            Files.createFile(target);
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), false);

            try {
                writer(config).assertNotExistsOrForce(target);
            } catch (OutputExistsException e) {
                assertThat(e.exitCode()).isEqualTo(50);
                return;
            }
            org.junit.jupiter.api.Assertions.fail("Expected OutputExistsException");
        }

        @Test
        void assertNotExistsOrForce_givenTargetExistsAndForceIsTrue_doesNotThrow(
                @TempDir Path tmp) throws IOException {
            Path target = tmp.resolve("existing.mp4");
            Files.createFile(target);
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), true);

            assertThatCode(() -> writer(config).assertNotExistsOrForce(target))
                    .doesNotThrowAnyException();
        }

        @Test
        void assertNotExistsOrForce_givenTargetIsDirectory_throwsOutputExistsException(
                @TempDir Path tmp) throws IOException {
            Path target = tmp.resolve("adir");
            Files.createDirectory(target);
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), false);

            assertThatThrownBy(() -> writer(config).assertNotExistsOrForce(target))
                    .isInstanceOf(OutputExistsException.class);
        }
    }

    // ── assertSufficientFreeSpace (NFR-MIN-DISK-FREE, AC-3.2) ──────────

    @Nested
    @DisplayName("assertSufficientFreeSpace (NFR-MIN-DISK-FREE, AC-3.2)")
    class AssertSufficientFreeSpaceTest {

        @Test
        void assertSufficientFreeSpace_givenSufficientSpace_doesNotThrow(@TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), false);
            Path target = tmp.resolve("output.mp4");

            // 1 byte expected → needs 2 bytes free; any real disk has that
            assertThatCode(() -> writer(config).assertSufficientFreeSpace(target, 1))
                    .doesNotThrowAnyException();
        }

        @Test
        void assertSufficientFreeSpace_givenZeroExpectedBytes_doesNotThrow(@TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), false);
            Path target = tmp.resolve("output.mp4");

            assertThatCode(() -> writer(config).assertSufficientFreeSpace(target, 0))
                    .doesNotThrowAnyException();
        }

        @Test
        void assertSufficientFreeSpace_givenInsufficientSpace_throwsFilesystemException(
                @TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), false);
            Path target = tmp.resolve("output.mp4");

            // Request absurdly large space — no real disk has Long.MAX_VALUE / 2 bytes
            assertThatThrownBy(() -> writer(config).assertSufficientFreeSpace(target, Long.MAX_VALUE / 2))
                    .isInstanceOf(FilesystemException.class)
                    .hasMessageContaining("insufficient disk space");
        }

        @Test
        void assertSufficientFreeSpace_givenFilesystemException_hasExitCode70(@TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), false);
            Path target = tmp.resolve("output.mp4");

            try {
                writer(config).assertSufficientFreeSpace(target, Long.MAX_VALUE / 2);
            } catch (FilesystemException e) {
                assertThat(e.exitCode()).isEqualTo(70);
                return;
            }
            org.junit.jupiter.api.Assertions.fail("Expected FilesystemException");
        }

        @Test
        void assertSufficientFreeSpace_givenParentDoesNotExist_createsIt(@TempDir Path tmp) {
            // AC-3.2: mkdir -p equivalent
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), false);
            Path target = tmp.resolve("deep/nested/dir/output.mp4");

            assertThatCode(() -> writer(config).assertSufficientFreeSpace(target, 1))
                    .doesNotThrowAnyException();

            assertThat(target.getParent()).exists().isDirectory();
        }

        @Test
        void assertSufficientFreeSpace_givenVeryLargeExpectedBytes_throwsFilesystemException(
                @TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.empty(), false);
            Path target = tmp.resolve("output.mp4");

            // Even Long.MAX_VALUE / 4 * 2 overflows or exceeds any disk
            assertThatThrownBy(() -> writer(config).assertSufficientFreeSpace(target, Long.MAX_VALUE / 3))
                    .isInstanceOf(FilesystemException.class);
        }
    }

    // ── Integration: sanitize + truncate + derive combined ──────────────

    @Nested
    @DisplayName("Integration: combined sanitize + truncate + derive")
    class IntegrationTest {

        @Test
        void deriveOutputPath_givenEmptyTitleAfterSanitization_usesVideoFallback(@TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.of(tmp), false);

            Path result = writer(config).deriveOutputPath(details("***"), "mp4");

            assertThat(result.getFileName().toString())
                    .isEqualTo("video [dQw4w9WgXcQ].mp4");
        }

        @Test
        void deriveOutputPath_givenTitleWithAllIllegalChars_usesVideoFallback(@TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.of(tmp), false);

            Path result = writer(config).deriveOutputPath(details("/\\:*?\"<>|"), "mp4");

            assertThat(result.getFileName().toString())
                    .isEqualTo("video [dQw4w9WgXcQ].mp4");
        }

        @Test
        void deriveOutputPath_givenSrtExtension_producesSrtFile(@TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.of(tmp), false);

            Path result = writer(config).deriveOutputPath(details("My Video"), "srt");

            assertThat(result.getFileName().toString())
                    .isEqualTo("My Video [dQw4w9WgXcQ].srt");
        }

        @Test
        void deriveOutputPath_givenTxtExtension_producesTxtFile(@TempDir Path tmp) {
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.of(tmp), false);

            Path result = writer(config).deriveOutputPath(details("My Video"), "txt");

            assertThat(result.getFileName().toString())
                    .isEqualTo("My Video [dQw4w9WgXcQ].txt");
        }
    }
}
