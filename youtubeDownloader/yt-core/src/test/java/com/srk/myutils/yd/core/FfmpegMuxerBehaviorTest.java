package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Comprehensive behaviour tests for {@link FfmpegMuxer#probeVersion()} — T-3.1.
 * Covers AC-13.1, AC-13.2, AC-13.3, NFR-MIN-FFMPEG-VERSION = 4.0.
 *
 * <p>Uses fake shell scripts in {@code @TempDir} to control ffmpeg output
 * without requiring a real ffmpeg binary (offline discipline, AC-11.3).
 */
@EnabledOnOs({OS.MAC, OS.LINUX})
class FfmpegMuxerBehaviorTest {

    @TempDir
    Path tempDir;

    // ── Helpers ─────────────────────────────────────────────────────────

    private Path fakeScript(String output, int exitCode) throws IOException {
        Path script = tempDir.resolve("ffmpeg");
        String content = "#!/bin/sh\necho '" + output + "'\nexit " + exitCode + "\n";
        Files.writeString(script, content);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    // ── Version record comparison (AC-13.3 prerequisite) ────────────────

    @Nested
    @DisplayName("Version.compareTo")
    class VersionComparisonTest {

        @Test
        void compareTo_givenEqualToMinVersion_returnsZero() {
            var version = new FfmpegMuxer.Version(4, 0, 0);
            assertThat(version.compareTo(FfmpegMuxer.MIN_VERSION)).isZero();
        }

        @Test
        void compareTo_givenBelowMinVersion_returnsNegative() {
            var version = new FfmpegMuxer.Version(3, 9, 99);
            assertThat(version.compareTo(FfmpegMuxer.MIN_VERSION)).isNegative();
        }

        @Test
        void compareTo_givenPatchAboveMinVersion_returnsPositive() {
            var version = new FfmpegMuxer.Version(4, 0, 1);
            assertThat(version.compareTo(FfmpegMuxer.MIN_VERSION)).isPositive();
        }

        @Test
        void compareTo_givenMajorAboveMinVersion_returnsPositive() {
            var version = new FfmpegMuxer.Version(5, 0, 0);
            assertThat(version.compareTo(FfmpegMuxer.MIN_VERSION)).isPositive();
        }

        @Test
        void toString_formatsAsDotSeparated() {
            assertThat(new FfmpegMuxer.Version(6, 1, 1)).hasToString("6.1.1");
        }

        @Test
        void constructor_givenNegativeComponent_throwsIllegalArgument() {
            assertThatThrownBy(() -> new FfmpegMuxer.Version(-1, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── Fake-script probeVersion (AC-13.1, AC-13.2, AC-13.3) ───────────

    @Nested
    @DisplayName("probeVersion via fake script")
    class ProbeVersionFakeScriptTest {

        @Test
        @DisplayName("AC-13.1: version 4.0 accepted — returns Version(4,0,0)")
        void probeVersion_givenVersion4_0_returnsVersion() throws IOException {
            Path script = fakeScript("ffmpeg version 4.0 Copyright (c) 2000-2024 the FFmpeg developers", 0);

            var version = new FfmpegMuxer(script.toString()).probeVersion();

            assertThat(version).isEqualTo(new FfmpegMuxer.Version(4, 0, 0));
        }

        @Test
        @DisplayName("AC-13.1: version 4.2.1 parsed correctly")
        void probeVersion_givenVersion4_2_1_returnsVersion() throws IOException {
            Path script = fakeScript("ffmpeg version 4.2.1 Copyright (c) 2000-2024 the FFmpeg developers", 0);

            var version = new FfmpegMuxer(script.toString()).probeVersion();

            assertThat(version).isEqualTo(new FfmpegMuxer.Version(4, 2, 1));
        }

        @Test
        @DisplayName("AC-13.1: distro suffix stripped — 6.1.1-1ubuntu1 → (6,1,1)")
        void probeVersion_givenDistroSuffix_parsesCorrectly() throws IOException {
            Path script = fakeScript("ffmpeg version 6.1.1-1ubuntu1 Copyright (c) 2000-2024", 0);

            var version = new FfmpegMuxer(script.toString()).probeVersion();

            assertThat(version).isEqualTo(new FfmpegMuxer.Version(6, 1, 1));
        }

        @Test
        @DisplayName("AC-13.1: epoch prefix stripped — 7:6.1.1-debian → (6,1,1)")
        void probeVersion_givenEpochPrefix_parsesCorrectly() throws IOException {
            Path script = fakeScript("ffmpeg version 7:6.1.1-debian Copyright (c) 2000-2024", 0);

            var version = new FfmpegMuxer(script.toString()).probeVersion();

            assertThat(version).isEqualTo(new FfmpegMuxer.Version(6, 1, 1));
        }

        @Test
        @DisplayName("AC-13.3: version below MIN_VERSION → FfmpegException with detected + required")
        void probeVersion_givenVersionBelowMin_throwsWithVersionInfo() throws IOException {
            Path script = fakeScript("ffmpeg version 3.4.1 Copyright (c) 2000-2024 the FFmpeg developers", 0);

            assertThatThrownBy(() -> new FfmpegMuxer(script.toString()).probeVersion())
                    .isInstanceOf(FfmpegException.class)
                    .hasMessageContaining("detected version 3.4.1")
                    .hasMessageContaining("4.0.0 or higher is required");
        }

        @Test
        @DisplayName("AC-13.2: non-zero exit → FfmpegException with install URL")
        void probeVersion_givenNonZeroExit_throwsWithInstallUrl() throws IOException {
            Path script = fakeScript("some error output", 1);

            assertThatThrownBy(() -> new FfmpegMuxer(script.toString()).probeVersion())
                    .isInstanceOf(FfmpegException.class)
                    .hasMessageContaining("ffmpeg not found on PATH or version check failed")
                    .hasMessageContaining("https://ffmpeg.org/");
        }

        @Test
        @DisplayName("AC-13.2: unparseable output → FfmpegException with install URL")
        void probeVersion_givenUnparseableOutput_throwsWithInstallUrl() throws IOException {
            Path script = fakeScript("this is not ffmpeg output at all", 0);

            assertThatThrownBy(() -> new FfmpegMuxer(script.toString()).probeVersion())
                    .isInstanceOf(FfmpegException.class)
                    .hasMessageContaining("ffmpeg not found on PATH or version check failed")
                    .hasMessageContaining("https://ffmpeg.org/");
        }

        @Test
        @DisplayName("AC-13.2: bogus binary path → FfmpegException with install URL")
        void probeVersion_givenBogusPath_throwsWithInstallUrl() {
            assertThatThrownBy(() -> new FfmpegMuxer("/nonexistent/ffmpeg").probeVersion())
                    .isInstanceOf(FfmpegException.class)
                    .hasMessageContaining("ffmpeg not found on PATH or version check failed")
                    .hasMessageContaining("https://ffmpeg.org/");
        }

        @Test
        @DisplayName("AC-13.1: two-component version 'n7.0' → (7,0,0) with missing patch defaulting to 0")
        void probeVersion_givenTwoComponentVersion_defaultsPatchToZero() throws IOException {
            Path script = fakeScript("ffmpeg version n7.0 Copyright (c) 2000-2024", 0);

            var version = new FfmpegMuxer(script.toString()).probeVersion();

            assertThat(version).isEqualTo(new FfmpegMuxer.Version(7, 0, 0));
        }
    }

    // ── FfmpegException.exitCode() (AC-5.2) ────────────────────────────

    @Nested
    @DisplayName("FfmpegException properties")
    class FfmpegExceptionTest {

        @Test
        @DisplayName("AC-5.2: exitCode() == 60")
        void exitCode_returns60() {
            assertThat(new FfmpegException("test").exitCode()).isEqualTo(60);
        }

        @Test
        @DisplayName("AC-13.2: exception preserves cause for binary-not-found")
        void probeVersion_givenBogusPath_preservesCause() {
            assertThatThrownBy(() -> new FfmpegMuxer("/nonexistent/ffmpeg").probeVersion())
                    .isInstanceOf(FfmpegException.class)
                    .hasCauseInstanceOf(IOException.class);
        }
    }

    // ── Real ffmpeg (conditional) ───────────────────────────────────────

    @Nested
    @DisplayName("Real ffmpeg on PATH (conditional)")
    class RealFfmpegTest {

        @Test
        @DisplayName("AC-13.1: if ffmpeg is on PATH, probeVersion returns ≥ 4.0.0")
        void probeVersion_givenRealFfmpeg_returnsAtLeastMinVersion() {
            var muxer = new FfmpegMuxer();
            FfmpegMuxer.Version version;
            try {
                version = muxer.probeVersion();
            } catch (FfmpegException e) {
                // ffmpeg not installed — skip
                assumeThat(false).as("ffmpeg not on PATH — skipping").isTrue();
                return;
            }

            assertThat(version.compareTo(FfmpegMuxer.MIN_VERSION))
                    .as("real ffmpeg version %s should be ≥ %s", version, FfmpegMuxer.MIN_VERSION)
                    .isGreaterThanOrEqualTo(0);
        }
    }
}
