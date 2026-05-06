package com.srk.myutils.yd.core.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the youtube-downloader fat-jar.
 * Run with {@code mvn verify -P integration}; skipped by default {@code mvn test}.
 *
 * <p>AC-11 integration path. T-5.4.
 */
@Tag("integration")
class YoutubeDownloaderIT {

    /**
     * Resolves the fat-jar path. Failsafe runs from the module directory (yt-core/),
     * so we navigate up to the project root first.
     */
    private static final Path FAT_JAR = Path.of("../yt-cli/target/youtube-downloader-1.0.0.jar");

    @Test
    void helpExitsZero() throws Exception {
        Process process = new ProcessBuilder("java", "-jar", fatJarPath(), "--help")
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        assertThat(finished).as("process should finish within 10s").isTrue();
        assertThat(process.exitValue()).isZero();
    }

    @Test
    void versionExitsZero() throws Exception {
        Process process = new ProcessBuilder("java", "-jar", fatJarPath(), "--version")
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        assertThat(finished).as("process should finish within 10s").isTrue();
        assertThat(process.exitValue()).isZero();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(output).contains("youtube-downloader 1.0.0");
    }

    @Test
    void invalidUrlProducesNonZeroExit() throws Exception {
        Process process = new ProcessBuilder("java", "-jar", fatJarPath(), "not-a-url")
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        assertThat(finished).as("process should finish within 10s").isTrue();
        assertThat(process.exitValue()).isNotZero();
    }

    /**
     * CT-EXIT-2b: invalid URL produces exit code 2 specifically (AC-5.2, cli-exit-codes.md).
     */
    @Test
    void invalidUrl_exitsWithCode2() throws Exception {
        Process process = new ProcessBuilder("java", "-jar", fatJarPath(), "not-a-url")
                .start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        assertThat(finished).as("process should finish within 10s").isTrue();

        assertThat(process.exitValue()).isEqualTo(2);
        String stderr = new String(process.getErrorStream().readAllBytes());
        assertThat(stderr).contains("Error: args:");
    }

    /**
     * AC-5.5: --debug with invalid URL shows stack trace on stderr.
     * Exercises OBS-1 fix — debug flag enables full stack trace output.
     */
    @Test
    void debugWithInvalidUrl_showsStackTrace() throws Exception {
        Process process = new ProcessBuilder("java", "-jar", fatJarPath(), "--debug", "not-a-url")
                .start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        assertThat(finished).as("process should finish within 10s").isTrue();

        assertThat(process.exitValue()).isEqualTo(2);
        String stderr = new String(process.getErrorStream().readAllBytes());
        assertThat(stderr).contains("Error: args:");
        // AC-5.5: stack trace present when --debug is set
        assertThat(stderr).containsPattern("(?s)\\bat\\s+\\S+\\(\\S+\\.java:\\d+\\)");
    }

    /**
     * Real YouTube download — disabled by default (flaky: network, rate-limits, ffmpeg required).
     * Enable manually for local smoke testing; T-5.10 covers CI verification.
     */
    @Disabled("Requires network + ffmpeg; run manually for local smoke testing")
    @Test
    void realVideoDownload_audioOnly(@TempDir Path tempDir) throws Exception {
        // Creative Commons short video — relatively stable
        String url = "https://www.youtube.com/watch?v=BaW_jenozKc";
        Process process = new ProcessBuilder(
                "java", "-jar", fatJarPath(),
                "--audio-only", "--output-dir", tempDir.toString(), url)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        assertThat(finished).as("process should finish within 60s").isTrue();
        assertThat(process.exitValue()).isZero();
    }

    private static String fatJarPath() {
        // Resolve relative to project root (mvn runs from project root)
        return FAT_JAR.toAbsolutePath().toString();
    }
}
