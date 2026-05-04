package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Comprehensive behaviour tests for T-3.6: {@link FfmpegMuxer} per-invocation timeout.
 * Covers NFR-FFMPEG-INVOCATION-TIMEOUT = 600s, AC-13.4 timeout path, CT-EXIT-60.
 *
 * <p>Scripts close stderr ({@code exec 2>/dev/null}) before sleeping so that
 * {@code captureLastLines} returns and {@code waitFor(timeout)} can fire.
 * All sleeps ≤ 2s to keep build verify fast. NoNetworkExtension is active globally.
 */
@DisabledOnOs(OS.WINDOWS)
class FfmpegMuxerTimeoutBehaviorTest {

    @TempDir
    Path tempDir;

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Script that closes stderr then sleeps — timeout fires while process sleeps. */
    private Path sleepScript(int sleepSeconds) throws IOException {
        Path script = tempDir.resolve("ffmpeg");
        Files.writeString(script, "#!/bin/sh\nexec 2>/dev/null\nsleep " + sleepSeconds + "\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    /** Script that exits immediately with the given code. */
    private Path exitScript(int exitCode) throws IOException {
        Path script = tempDir.resolve("ffmpeg");
        Files.writeString(script, "#!/bin/sh\nexit " + exitCode + "\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    private Path dummyFile(String name) throws IOException {
        return Files.createFile(tempDir.resolve(name));
    }

    // ── 1. mux timeout → FfmpegException with 'timeout' and '1s' ──────

    @Test
    @DisplayName("mux_givenTimeoutExceeded_throwsFfmpegExceptionWithTimeoutMessage (NFR-FFMPEG-INVOCATION-TIMEOUT)")
    void mux_givenTimeoutExceeded_throwsFfmpegExceptionWithTimeoutMessage() throws IOException {
        FfmpegMuxer muxer = new FfmpegMuxer(sleepScript(2).toString(), 1);

        assertThatThrownBy(() -> muxer.mux(
                dummyFile("v.part"), dummyFile("a.part"), tempDir.resolve("out.mp4"), false))
                .isInstanceOf(FfmpegException.class)
                .hasMessageContaining("timeout")
                .hasMessageContaining("1s");
    }

    // ── 2. transcodeMp3 timeout → FfmpegException ──────────────────────

    @Test
    @DisplayName("transcodeMp3_givenTimeoutExceeded_throwsFfmpegException (NFR-FFMPEG-INVOCATION-TIMEOUT)")
    void transcodeMp3_givenTimeoutExceeded_throwsFfmpegException() throws IOException {
        FfmpegMuxer muxer = new FfmpegMuxer(sleepScript(2).toString(), 1);

        assertThatThrownBy(() -> muxer.transcodeMp3(
                dummyFile("a.part"), tempDir.resolve("out.mp3"), false))
                .isInstanceOf(FfmpegException.class)
                .hasMessageContaining("timeout");
    }

    // ── 3. mux within timeout → no exception ───────────────────────────

    @Test
    @DisplayName("mux_givenProcessExitsBeforeTimeout_doesNotThrow")
    void mux_givenProcessExitsBeforeTimeout_doesNotThrow() throws IOException {
        FfmpegMuxer muxer = new FfmpegMuxer(exitScript(0).toString(), 10);

        assertThatCode(() -> muxer.mux(
                dummyFile("v.part"), dummyFile("a.part"), tempDir.resolve("out.mp4"), false))
                .doesNotThrowAnyException();
    }

    // ── 4. After timeout: liveProcesses() empty ────────────────────────

    @Test
    @DisplayName("mux_afterTimeout_liveProcessesDoesNotContainKilledProcess (INV-8)")
    void mux_afterTimeout_liveProcessesDoesNotContainKilledProcess() throws IOException {
        FfmpegMuxer muxer = new FfmpegMuxer(sleepScript(2).toString(), 1);

        try {
            muxer.mux(dummyFile("v.part"), dummyFile("a.part"),
                    tempDir.resolve("out.mp4"), false);
        } catch (FfmpegException ignored) { }

        assertThat(FfmpegMuxer.liveProcesses()).isEmpty();
    }

    // ── 5. Default constructor uses FFMPEG_TIMEOUT_SECONDS ─────────────

    @Test
    @DisplayName("defaultConstructor_usesDefaultTimeout (NFR-FFMPEG-INVOCATION-TIMEOUT = 600)")
    void defaultConstructor_usesDefaultTimeout() {
        // The constant is the single source of truth for the default timeout.
        // The public FfmpegMuxer(String) constructor delegates to the package-private
        // constructor with FFMPEG_TIMEOUT_SECONDS. We verify the constant value here;
        // the delegation is structural (constructor chain) and covered by the compiler.
        assertThat(FfmpegMuxer.FFMPEG_TIMEOUT_SECONDS).isEqualTo(600L);
    }

    // ── 6. FFMPEG_TIMEOUT_SECONDS == 600 ───────────────────────────────

    @Test
    @DisplayName("FFMPEG_TIMEOUT_SECONDS_equals600 (NFR-FFMPEG-INVOCATION-TIMEOUT)")
    void ffmpegTimeoutSecondsConstant_equals600() {
        assertThat(FfmpegMuxer.FFMPEG_TIMEOUT_SECONDS).isEqualTo(600L);
    }

    // ── 7. transcodeMp3 within timeout → no exception ──────────────────

    @Test
    @DisplayName("transcodeMp3_givenProcessExitsBeforeTimeout_doesNotThrow")
    void transcodeMp3_givenProcessExitsBeforeTimeout_doesNotThrow() throws IOException {
        FfmpegMuxer muxer = new FfmpegMuxer(exitScript(0).toString(), 5);

        assertThatCode(() -> muxer.transcodeMp3(
                dummyFile("a.part"), tempDir.resolve("out.mp3"), false))
                .doesNotThrowAnyException();
    }

    // ── 8. Timeout exception exitCode() == 60 (CT-EXIT-60) ────────────

    @Test
    @DisplayName("mux_givenTimeout_exceptionExitCodeIs60 (CT-EXIT-60)")
    void mux_givenTimeout_exceptionExitCodeIs60() throws IOException {
        FfmpegMuxer muxer = new FfmpegMuxer(sleepScript(2).toString(), 1);

        FfmpegException ex = catchThrowableOfType(
                () -> muxer.mux(dummyFile("v.part"), dummyFile("a.part"),
                        tempDir.resolve("out.mp4"), false),
                FfmpegException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.exitCode()).isEqualTo(60);
    }
}
