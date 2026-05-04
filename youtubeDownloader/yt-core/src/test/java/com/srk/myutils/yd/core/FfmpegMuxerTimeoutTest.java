package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization test for T-3.6: FfmpegMuxer per-invocation timeout.
 * Uses a fake script that sleeps forever and a 1-second timeout to verify
 * that the timeout fires, the process is killed, and {@link FfmpegException}
 * is thrown with the expected message.
 */
@DisabledOnOs(OS.WINDOWS)
class FfmpegMuxerTimeoutTest {

    @TempDir
    Path tempDir;

    @Test
    void mux_givenSleepForeverScript_throwsFfmpegExceptionOnTimeout() throws IOException {
        Path script = tempDir.resolve("ffmpeg");
        Files.writeString(script, "#!/bin/sh\nexec 2>/dev/null\nsleep 2\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));

        Path video = Files.createFile(tempDir.resolve("video.part"));
        Path audio = Files.createFile(tempDir.resolve("audio.part"));
        Path output = tempDir.resolve("out.mp4");

        FfmpegMuxer muxer = new FfmpegMuxer(script.toString(), 1);

        assertThatThrownBy(() -> muxer.mux(video, audio, output, false))
                .isInstanceOf(FfmpegException.class)
                .hasMessageContaining("timeout");

        assertThat(FfmpegMuxer.liveProcesses()).isEmpty();
    }
}
