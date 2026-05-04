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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Comprehensive behaviour tests for {@link FfmpegMuxer#transcodeMp3(Path, Path, boolean)} — T-3.3.
 * Covers AC-2.4 (MP3 transcode at NFR-DEFAULT-MP3-BITRATE = 192k),
 * and 04-apis.md § 2.1.3 (exact command-line shape).
 *
 * <p>Uses fake shell scripts in {@code @TempDir} — no real ffmpeg required (AC-11.3).
 */
@EnabledOnOs({OS.MAC, OS.LINUX})
class FfmpegMuxerTranscodeBehaviorTest {

    @TempDir
    Path tempDir;

    // ── Helpers ─────────────────────────────────────────────────────────

    private Path fakeScript(int exitCode, String stderr) throws IOException {
        Path script = tempDir.resolve("ffmpeg");
        String content = "#!/bin/sh\n"
                + (stderr.isEmpty() ? "" : "echo '" + stderr + "' >&2\n")
                + "exit " + exitCode + "\n";
        Files.writeString(script, content);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    private Path argsEchoScript(Path captureFile) throws IOException {
        Path script = tempDir.resolve("ffmpeg");
        String content = "#!/bin/sh\n"
                + "for arg in \"$@\"; do echo \"$arg\" >> '" + captureFile + "'; done\n"
                + "exit 0\n";
        Files.writeString(script, content);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    private Path createPartFile(String name) throws IOException {
        Path file = tempDir.resolve(name);
        Files.createFile(file);
        return file;
    }

    // ── Error path: bogus binary ────────────────────────────────────────

    @Test
    @DisplayName("AC-13.2: bogus ffmpeg path → FfmpegException")
    void transcodeMp3_givenBogusPath_throwsFfmpegException() throws IOException {
        Path audio = createPartFile("audio.part");
        Path output = tempDir.resolve("out.mp3");

        assertThatThrownBy(() -> new FfmpegMuxer("/nonexistent/ffmpeg").transcodeMp3(audio, output, false))
                .isInstanceOf(FfmpegException.class)
                .hasMessageContaining("ffmpeg transcode failed");
    }

    // ── Exit-code behaviour ─────────────────────────────────────────────

    @Nested
    @DisplayName("Exit-code behaviour")
    class ExitCodeTest {

        @Test
        @DisplayName("AC-2.4: exit 0 → no exception (happy path)")
        void transcodeMp3_givenExitZero_doesNotThrow() throws IOException {
            Path script = fakeScript(0, "");
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp3");

            assertThatCode(() -> new FfmpegMuxer(script.toString()).transcodeMp3(audio, output, false))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-13.4: exit 1 → FfmpegException containing stderr")
        void transcodeMp3_givenExitOne_throwsWithStderr() throws IOException {
            Path script = fakeScript(1, "transcode error detail");
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp3");

            assertThatThrownBy(() -> new FfmpegMuxer(script.toString()).transcodeMp3(audio, output, false))
                    .isInstanceOf(FfmpegException.class)
                    .hasMessageContaining("transcode error detail");
        }
    }

    // ── Command-line shape (04-apis.md § 2.1.3) ─────────────────────────

    @Nested
    @DisplayName("Command-line shape per 04-apis.md § 2.1.3")
    class CommandLineTest {

        @Test
        @DisplayName("AC-2.4: command contains -i audio -c:a libmp3lame -b:a 192k -y output")
        void transcodeMp3_commandLineContainsExpectedArgs() throws IOException {
            Path captureFile = tempDir.resolve("args.txt");
            Path script = argsEchoScript(captureFile);
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp3");

            new FfmpegMuxer(script.toString()).transcodeMp3(audio, output, false);

            List<String> args = Files.readAllLines(captureFile);
            assertThat(args).containsSubsequence(
                    "-hide_banner",
                    "-loglevel", "error",
                    "-i", audio.toString(),
                    "-c:a", "libmp3lame",
                    "-b:a", "192k",
                    "-y",
                    output.toString()
            );
        }

        @Test
        @DisplayName("NFR-FFMPEG-LOGLEVEL: debug=true → loglevel 'info'")
        void transcodeMp3_givenDebugTrue_passesLoglevelInfo() throws IOException {
            Path captureFile = tempDir.resolve("args.txt");
            Path script = argsEchoScript(captureFile);
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp3");

            new FfmpegMuxer(script.toString()).transcodeMp3(audio, output, true);

            List<String> args = Files.readAllLines(captureFile);
            int loglevelIdx = args.indexOf("-loglevel");
            assertThat(loglevelIdx).isGreaterThanOrEqualTo(0);
            assertThat(args.get(loglevelIdx + 1)).isEqualTo("info");
        }

        @Test
        @DisplayName("NFR-FFMPEG-LOGLEVEL: debug=false → loglevel 'error'")
        void transcodeMp3_givenDebugFalse_passesLoglevelError() throws IOException {
            Path captureFile = tempDir.resolve("args.txt");
            Path script = argsEchoScript(captureFile);
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp3");

            new FfmpegMuxer(script.toString()).transcodeMp3(audio, output, false);

            List<String> args = Files.readAllLines(captureFile);
            int loglevelIdx = args.indexOf("-loglevel");
            assertThat(loglevelIdx).isGreaterThanOrEqualTo(0);
            assertThat(args.get(loglevelIdx + 1)).isEqualTo("error");
        }
    }

    // ── Constant value (NFR-DEFAULT-MP3-BITRATE) ────────────────────────

    @Test
    @DisplayName("NFR-DEFAULT-MP3-BITRATE: DEFAULT_MP3_BITRATE constant == '192k'")
    void defaultMp3Bitrate_equals192k() {
        assertThat(FfmpegMuxer.DEFAULT_MP3_BITRATE).isEqualTo("192k");
    }

    // ── Real ffmpeg (conditional) ───────────────────────────────────────

    @Test
    @DisplayName("Real ffmpeg with empty audioPart → FfmpegException (non-zero exit)")
    void transcodeMp3_givenRealFfmpegWithEmptyPart_throwsFfmpegException() throws IOException {
        var muxer = new FfmpegMuxer();
        try {
            muxer.probeVersion();
        } catch (FfmpegException e) {
            assumeThat(false).as("ffmpeg not on PATH — skipping").isTrue();
            return;
        }

        Path audio = createPartFile("audio.part");
        Path output = tempDir.resolve("out.mp3");

        assertThatThrownBy(() -> muxer.transcodeMp3(audio, output, false))
                .isInstanceOf(FfmpegException.class);
    }
}
