package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization test for {@link FfmpegMuxer#mux(Path, Path, Path, boolean)} (T-3.2).
 * Uses fake shell scripts — does not require a real ffmpeg binary (AC-11.3).
 */
@EnabledOnOs({OS.MAC, OS.LINUX})
class FfmpegMuxerMuxTest {

    @TempDir
    Path tempDir;

    private Path fakeScript(int exitCode, String stderr) throws IOException {
        Path script = tempDir.resolve("ffmpeg");
        String content = "#!/bin/sh\n"
                + (stderr.isEmpty() ? "" : "echo '" + stderr + "' >&2\n")
                + "exit " + exitCode + "\n";
        Files.writeString(script, content);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    @Test
    void mux_givenSuccessfulFfmpeg_doesNotThrow() throws IOException {
        Path script = fakeScript(0, "");
        Path video = tempDir.resolve("video.part");
        Path audio = tempDir.resolve("audio.part");
        Path output = tempDir.resolve("out.mp4");
        Files.createFile(video);
        Files.createFile(audio);

        assertThatCode(() -> new FfmpegMuxer(script.toString()).mux(video, audio, output, false))
                .doesNotThrowAnyException();
    }

    @Test
    void mux_givenNonZeroExit_throwsFfmpegException() throws IOException {
        Path script = fakeScript(1, "mux error detail");
        Path video = tempDir.resolve("video.part");
        Path audio = tempDir.resolve("audio.part");
        Path output = tempDir.resolve("out.mp4");
        Files.createFile(video);
        Files.createFile(audio);

        assertThatThrownBy(() -> new FfmpegMuxer(script.toString()).mux(video, audio, output, false))
                .isInstanceOf(FfmpegException.class)
                .hasMessageContaining("ffmpeg mux failed");
    }
}
