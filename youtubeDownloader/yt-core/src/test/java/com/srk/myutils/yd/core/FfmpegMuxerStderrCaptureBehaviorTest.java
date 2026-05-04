package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for T-3.4: ffmpeg stderr ring-buffer capture (AC-13.4, NFR-FFMPEG-STDERR-LINES=20).
 *
 * <p>Tests the {@code captureLastLines(InputStream, int)} method directly for unit-level
 * coverage, plus end-to-end through {@code mux()} and {@code transcodeMp3()} via fake scripts.
 */
@DisplayName("T-3.4 — stderr ring-buffer capture")
class FfmpegMuxerStderrCaptureBehaviorTest {

    // ── captureLastLines unit tests (package-private access, same package) ──

    @Test
    @DisplayName("captureLastLines: 5 lines with maxLines=20 → all 5 preserved in order")
    void captureLastLines_givenFewerLinesThanMax_returnsAllInOrder() throws IOException {
        String input = "line 1\nline 2\nline 3\nline 4\nline 5\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

        List<String> result = FfmpegMuxer.captureLastLines(stream, 20);

        assertThat(result).containsExactly("line 1", "line 2", "line 3", "line 4", "line 5");
    }

    @Test
    @DisplayName("captureLastLines: 30 lines with maxLines=20 → last 20 only (lines 11-30)")
    void captureLastLines_givenMoreLinesThanMax_returnsOnlyLastN() throws IOException {
        String input = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> "line " + i)
                .collect(Collectors.joining("\n")) + "\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

        List<String> result = FfmpegMuxer.captureLastLines(stream, 20);

        assertThat(result).hasSize(20);
        assertThat(result.get(0)).isEqualTo("line 11");
        assertThat(result.get(19)).isEqualTo("line 30");
        // Lines 1-10 must not be present
        for (int i = 1; i <= 10; i++) {
            assertThat(result).doesNotContain("line " + i);
        }
    }

    @Test
    @DisplayName("captureLastLines: exactly 20 lines with maxLines=20 → all 20 preserved")
    void captureLastLines_givenExactlyMaxLines_returnsAll() throws IOException {
        String input = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> "line " + i)
                .collect(Collectors.joining("\n")) + "\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

        List<String> result = FfmpegMuxer.captureLastLines(stream, 20);

        assertThat(result).hasSize(20);
        assertThat(result.get(0)).isEqualTo("line 1");
        assertThat(result.get(19)).isEqualTo("line 20");
    }

    @Test
    @DisplayName("captureLastLines: empty stream → empty list")
    void captureLastLines_givenEmptyStream_returnsEmptyList() throws IOException {
        InputStream stream = new ByteArrayInputStream(new byte[0]);

        List<String> result = FfmpegMuxer.captureLastLines(stream, 20);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("captureLastLines: single line → list of 1")
    void captureLastLines_givenSingleLine_returnsSingleElement() throws IOException {
        InputStream stream = new ByteArrayInputStream("only line\n".getBytes(StandardCharsets.UTF_8));

        List<String> result = FfmpegMuxer.captureLastLines(stream, 20);

        assertThat(result).containsExactly("only line");
    }

    @Test
    @DisplayName("captureLastLines: maxLines=0 → returns all lines (no eviction; edge case not used in production)")
    void captureLastLines_givenZeroMaxLines_returnsAllLines() throws IOException {
        String input = "line 1\nline 2\nline 3\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

        List<String> result = FfmpegMuxer.captureLastLines(stream, 0);

        // maxLines=0: ring.size() == 0 is always true before addLast, so pollFirst
        // runs on every iteration but the deque auto-grows — effectively no cap.
        // This edge case is never reached in production (FFMPEG_STDERR_LINES=20).
        assertThat(result).containsExactly("line 1", "line 2", "line 3");
    }

    @Test
    @DisplayName("captureLastLines: negative maxLines → returns all lines (size never equals negative; edge case)")
    void captureLastLines_givenNegativeMaxLines_returnsAllLines() throws IOException {
        String input = "line 1\nline 2\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

        List<String> result = FfmpegMuxer.captureLastLines(stream, -1);

        // ring.size() == -1 is never true, so no eviction occurs — all lines kept.
        assertThat(result).containsExactly("line 1", "line 2");
    }

    // ── FFMPEG_STDERR_LINES constant ──

    @Test
    @DisplayName("FFMPEG_STDERR_LINES constant == 20 (NFR-FFMPEG-STDERR-LINES)")
    void ffmpegStderrLinesConstant_equals20() {
        assertThat(FfmpegMuxer.FFMPEG_STDERR_LINES).isEqualTo(20);
    }

    // ── mux() / transcodeMp3() integration via fake scripts ──

    @TempDir
    Path tempDir;

    private Path fakeScript(int exitCode, int stderrLineCount) throws IOException {
        Path script = tempDir.resolve("ffmpeg");
        StringBuilder sb = new StringBuilder("#!/bin/sh\n");
        for (int i = 1; i <= stderrLineCount; i++) {
            sb.append("echo 'stderr line ").append(i).append("' >&2\n");
        }
        sb.append("exit ").append(exitCode).append("\n");
        Files.writeString(script, sb.toString());
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    @DisplayName("mux: 30 stderr lines + exit 1 → FfmpegException contains last 20, not lines 1-10 (AC-13.4)")
    void mux_givenThirtyStderrLines_exceptionContainsOnlyLastTwenty() throws IOException {
        Path script = fakeScript(1, 30);
        Path video = tempDir.resolve("video.part");
        Path audio = tempDir.resolve("audio.part");
        Path output = tempDir.resolve("out.mp4");
        Files.createFile(video);
        Files.createFile(audio);

        assertThatThrownBy(() -> new FfmpegMuxer(script.toString()).mux(video, audio, output, false))
                .isInstanceOf(FfmpegException.class)
                .satisfies(ex -> {
                    String msg = ex.getMessage();
                    List<String> lines = List.of(msg.split("\n"));
                    // Last 20 stderr lines (11-30) must be present
                    for (int i = 11; i <= 30; i++) {
                        assertThat(lines).contains("stderr line " + i);
                    }
                    // First 10 lines (1-10) must be dropped by the ring buffer
                    for (int i = 1; i <= 10; i++) {
                        assertThat(lines).doesNotContain("stderr line " + i);
                    }
                });
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    @DisplayName("transcodeMp3: 30 stderr lines + exit 1 → FfmpegException contains last 20 only (AC-13.4)")
    void transcodeMp3_givenThirtyStderrLines_exceptionContainsOnlyLastTwenty() throws IOException {
        Path script = fakeScript(1, 30);
        Path audio = tempDir.resolve("audio.part");
        Path output = tempDir.resolve("out.mp3");
        Files.createFile(audio);

        assertThatThrownBy(() -> new FfmpegMuxer(script.toString()).transcodeMp3(audio, output, false))
                .isInstanceOf(FfmpegException.class)
                .satisfies(ex -> {
                    String msg = ex.getMessage();
                    List<String> lines = List.of(msg.split("\n"));
                    for (int i = 11; i <= 30; i++) {
                        assertThat(lines).contains("stderr line " + i);
                    }
                    for (int i = 1; i <= 10; i++) {
                        assertThat(lines).doesNotContain("stderr line " + i);
                    }
                });
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    @DisplayName("mux: exit 0 with stderr output → no exception thrown, stderr drained silently")
    void mux_givenSuccessWithStderr_doesNotThrow() throws IOException {
        Path script = fakeScript(0, 15);
        Path video = tempDir.resolve("video.part");
        Path audio = tempDir.resolve("audio.part");
        Path output = tempDir.resolve("out.mp4");
        Files.createFile(video);
        Files.createFile(audio);

        assertThatCode(() -> new FfmpegMuxer(script.toString()).mux(video, audio, output, false))
                .doesNotThrowAnyException();
    }
}
