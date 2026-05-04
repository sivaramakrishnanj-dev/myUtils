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
 * Comprehensive behaviour tests for {@link FfmpegMuxer#mux(Path, Path, Path, boolean)} — T-3.2.
 * Covers AC-1.6 (mux video+audio → MP4), INV-8 (no ffmpeg outside MUXING),
 * and 04-apis.md § 2.1.2 (exact command-line shape).
 *
 * <p>Uses fake shell scripts in {@code @TempDir} — no real ffmpeg required (AC-11.3).
 */
@EnabledOnOs({OS.MAC, OS.LINUX})
class FfmpegMuxerMuxBehaviorTest {

    @TempDir
    Path tempDir;

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Creates a fake ffmpeg script that optionally writes to stderr and exits
     * with the given code.
     */
    private Path fakeScript(int exitCode, String stderr) throws IOException {
        Path script = tempDir.resolve("ffmpeg");
        String content = "#!/bin/sh\n"
                + (stderr.isEmpty() ? "" : "echo '" + stderr + "' >&2\n")
                + "exit " + exitCode + "\n";
        Files.writeString(script, content);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    /**
     * Creates a fake ffmpeg script that echoes all received arguments to a
     * capture file, then exits 0. Used to verify the exact command line.
     */
    private Path argsEchoScript(Path captureFile) throws IOException {
        Path script = tempDir.resolve("ffmpeg");
        // Write each arg on its own line so we can parse them back reliably
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
    void mux_givenBogusPath_throwsFfmpegException() throws IOException {
        Path video = createPartFile("video.part");
        Path audio = createPartFile("audio.part");
        Path output = tempDir.resolve("out.mp4");

        assertThatThrownBy(() -> new FfmpegMuxer("/nonexistent/ffmpeg").mux(video, audio, output, false))
                .isInstanceOf(FfmpegException.class)
                .hasMessageContaining("ffmpeg mux failed");
    }

    // ── Exit-code behaviour ─────────────────────────────────────────────

    @Nested
    @DisplayName("Exit-code behaviour")
    class ExitCodeTest {

        @Test
        @DisplayName("AC-1.6: exit 0 → no exception (happy path)")
        void mux_givenExitZero_doesNotThrow() throws IOException {
            Path script = fakeScript(0, "");
            Path video = createPartFile("video.part");
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp4");

            assertThatCode(() -> new FfmpegMuxer(script.toString()).mux(video, audio, output, false))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-13.4: exit 1 → FfmpegException containing stderr")
        void mux_givenExitOne_throwsWithStderr() throws IOException {
            Path script = fakeScript(1, "mux error detail");
            Path video = createPartFile("video.part");
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp4");

            assertThatThrownBy(() -> new FfmpegMuxer(script.toString()).mux(video, audio, output, false))
                    .isInstanceOf(FfmpegException.class)
                    .hasMessageContaining("mux error detail");
        }

        @Test
        @DisplayName("stderr on exit 0 → no exception (warnings acceptable)")
        void mux_givenStderrButExitZero_doesNotThrow() throws IOException {
            Path script = fakeScript(0, "some warning output");
            Path video = createPartFile("video.part");
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp4");

            assertThatCode(() -> new FfmpegMuxer(script.toString()).mux(video, audio, output, false))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-13.4: stderr on exit 1 → FfmpegException contains stderr text")
        void mux_givenStderrAndExitOne_throwsContainingStderr() throws IOException {
            Path script = fakeScript(1, "detailed failure reason");
            Path video = createPartFile("video.part");
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp4");

            assertThatThrownBy(() -> new FfmpegMuxer(script.toString()).mux(video, audio, output, false))
                    .isInstanceOf(FfmpegException.class)
                    .hasMessageContaining("detailed failure reason");
        }
    }

    // ── Command-line shape (04-apis.md § 2.1.2) ─────────────────────────

    @Nested
    @DisplayName("Command-line shape per 04-apis.md § 2.1.2")
    class CommandLineTest {

        @Test
        @DisplayName("NFR-FFMPEG-LOGLEVEL: debug=true → loglevel 'info'")
        void mux_givenDebugTrue_passesLoglevelInfo() throws IOException {
            Path captureFile = tempDir.resolve("args.txt");
            Path script = argsEchoScript(captureFile);
            Path video = createPartFile("video.part");
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp4");

            new FfmpegMuxer(script.toString()).mux(video, audio, output, true);

            List<String> args = Files.readAllLines(captureFile);
            int loglevelIdx = args.indexOf("-loglevel");
            assertThat(loglevelIdx).isGreaterThanOrEqualTo(0);
            assertThat(args.get(loglevelIdx + 1)).isEqualTo("info");
        }

        @Test
        @DisplayName("NFR-FFMPEG-LOGLEVEL: debug=false → loglevel 'error'")
        void mux_givenDebugFalse_passesLoglevelError() throws IOException {
            Path captureFile = tempDir.resolve("args.txt");
            Path script = argsEchoScript(captureFile);
            Path video = createPartFile("video.part");
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp4");

            new FfmpegMuxer(script.toString()).mux(video, audio, output, false);

            List<String> args = Files.readAllLines(captureFile);
            int loglevelIdx = args.indexOf("-loglevel");
            assertThat(loglevelIdx).isGreaterThanOrEqualTo(0);
            assertThat(args.get(loglevelIdx + 1)).isEqualTo("error");
        }

        @Test
        @DisplayName("04-apis.md § 2.1.2: command line contains expected flags and paths")
        void mux_commandLineContainsExpectedArgs() throws IOException {
            Path captureFile = tempDir.resolve("args.txt");
            Path script = argsEchoScript(captureFile);
            Path video = createPartFile("video.part");
            Path audio = createPartFile("audio.part");
            Path output = tempDir.resolve("out.mp4");

            new FfmpegMuxer(script.toString()).mux(video, audio, output, false);

            List<String> args = Files.readAllLines(captureFile);
            // args excludes argv[0] (the script itself); contains everything after
            assertThat(args).containsSubsequence(
                    "-hide_banner",
                    "-loglevel", "error",
                    "-i", video.toString(),
                    "-i", audio.toString(),
                    "-c", "copy",
                    "-map", "0:v:0",
                    "-map", "1:a:0",
                    "-y",
                    output.toString()
            );
        }
    }

    // ── Non-existent input files ────────────────────────────────────────

    @Test
    @DisplayName("Non-existent input files → FfmpegException")
    void mux_givenNonExistentInputs_throwsFfmpegException() {
        Path video = tempDir.resolve("missing-video.part");
        Path audio = tempDir.resolve("missing-audio.part");
        Path output = tempDir.resolve("out.mp4");

        // Use the default "ffmpeg" path — if real ffmpeg is present it will
        // fail on missing inputs; if not present it will fail on binary lookup.
        // Either way → FfmpegException.
        assertThatThrownBy(() -> new FfmpegMuxer().mux(video, audio, output, false))
                .isInstanceOf(FfmpegException.class);
    }

    // ── Real ffmpeg (conditional) ───────────────────────────────────────

    @Test
    @DisplayName("Real ffmpeg with empty .part files → FfmpegException (non-zero exit)")
    void mux_givenRealFfmpegWithEmptyParts_throwsFfmpegException() throws IOException {
        // Skip if ffmpeg is not on PATH
        var muxer = new FfmpegMuxer();
        try {
            muxer.probeVersion();
        } catch (FfmpegException e) {
            assumeThat(false).as("ffmpeg not on PATH — skipping").isTrue();
            return;
        }

        Path video = createPartFile("video.part");
        Path audio = createPartFile("audio.part");
        Path output = tempDir.resolve("out.mp4");

        assertThatThrownBy(() -> muxer.mux(video, audio, output, false))
                .isInstanceOf(FfmpegException.class);
    }
}
