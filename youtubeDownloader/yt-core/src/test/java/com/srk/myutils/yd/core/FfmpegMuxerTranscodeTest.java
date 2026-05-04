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
 * Characterization test for {@link FfmpegMuxer#transcodeMp3(Path, Path, boolean)} (T-3.3).
 * Uses fake shell scripts — does not require a real ffmpeg binary (AC-11.3).
 */
@EnabledOnOs({OS.MAC, OS.LINUX})
class FfmpegMuxerTranscodeTest {

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
    void transcodeMp3_givenSuccessfulFfmpeg_doesNotThrow() throws IOException {
        Path script = fakeScript(0, "");
        Path audio = tempDir.resolve("audio.part");
        Path output = tempDir.resolve("out.mp3");
        Files.createFile(audio);

        assertThatCode(() -> new FfmpegMuxer(script.toString()).transcodeMp3(audio, output, false))
                .doesNotThrowAnyException();
    }

    @Test
    void transcodeMp3_givenNonZeroExit_throwsFfmpegException() throws IOException {
        Path script = fakeScript(1, "transcode error detail");
        Path audio = tempDir.resolve("audio.part");
        Path output = tempDir.resolve("out.mp3");
        Files.createFile(audio);

        assertThatThrownBy(() -> new FfmpegMuxer(script.toString()).transcodeMp3(audio, output, false))
                .isInstanceOf(FfmpegException.class)
                .hasMessageContaining("ffmpeg transcode failed");
    }
}
