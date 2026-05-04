package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization test for T-3.4: ffmpeg stderr ring-buffer capture.
 * Verifies that when ffmpeg writes more than NFR-FFMPEG-STDERR-LINES (20)
 * lines to stderr, only the last 20 are surfaced in the FfmpegException message.
 */
@EnabledOnOs({OS.MAC, OS.LINUX})
class FfmpegMuxerStderrCaptureTest {

    @TempDir
    Path tempDir;

    @Test
    void mux_givenThirtyStderrLines_exceptionContainsOnlyLastTwenty() throws IOException {
        // Build a fake ffmpeg that writes 30 numbered lines to stderr and exits 1
        String stderrLines = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> "echo 'stderr line " + i + "' >&2")
                .collect(Collectors.joining("\n"));

        Path script = tempDir.resolve("ffmpeg");
        Files.writeString(script, "#!/bin/sh\n" + stderrLines + "\nexit 1\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));

        Path video = tempDir.resolve("video.part");
        Path audio = tempDir.resolve("audio.part");
        Path output = tempDir.resolve("out.mp4");
        Files.createFile(video);
        Files.createFile(audio);

        FfmpegMuxer muxer = new FfmpegMuxer(script.toString());

        assertThatThrownBy(() -> muxer.mux(video, audio, output, false))
                .isInstanceOf(FfmpegException.class)
                .satisfies(ex -> {
                    String msg = ex.getMessage();
                    // First 10 lines (1–10) should be dropped
                    for (int i = 1; i <= 10; i++) {
                        assert !msg.contains("stderr line " + i + "\n")
                                || (i == 1 && !msg.contains("stderr line 1\n"))
                                : "Expected line " + i + " to be dropped";
                    }
                    // Last 20 lines (11–30) should be present
                    for (int i = 11; i <= 30; i++) {
                        assert msg.contains("stderr line " + i)
                                : "Expected line " + i + " to be present";
                    }
                });
    }
}
