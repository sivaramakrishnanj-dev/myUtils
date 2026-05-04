package com.srk.myutils.yd.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Comprehensive tests for T-3.5: FfmpegMuxer shutdown hook cooperation.
 * Verifies LIVE_PROCESSES tracking, terminateAll() SIGTERM→SIGKILL behaviour,
 * and process lifecycle management per 02-architecture.md § 5 and INV-8.
 */
class FfmpegMuxerShutdownBehaviorTest {

    @AfterEach
    void cleanupLiveProcesses() {
        for (Process p : FfmpegMuxer.LIVE_PROCESSES) {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
        FfmpegMuxer.LIVE_PROCESSES.clear();
    }

    // ── 1. add / remove / liveProcesses view ──────────────────────────

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void addLiveProcess_givenProcess_appearsInLiveProcessesView() throws Exception {
        Process sleeper = new ProcessBuilder("sleep", "60").start();
        try {
            FfmpegMuxer.LIVE_PROCESSES.add(sleeper);

            assertThat(FfmpegMuxer.liveProcesses()).containsExactly(sleeper);
        } finally {
            FfmpegMuxer.LIVE_PROCESSES.remove(sleeper);
            sleeper.destroyForcibly();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void removeLiveProcess_givenTrackedProcess_noLongerInView() throws Exception {
        Process sleeper = new ProcessBuilder("sleep", "60").start();
        try {
            FfmpegMuxer.LIVE_PROCESSES.add(sleeper);
            FfmpegMuxer.LIVE_PROCESSES.remove(sleeper);

            assertThat(FfmpegMuxer.liveProcesses()).doesNotContain(sleeper);
        } finally {
            sleeper.destroyForcibly();
        }
    }

    @Test
    void liveProcesses_returnsUnmodifiableView() {
        Set<Process> view = FfmpegMuxer.liveProcesses();

        assertThat(view).isNotSameAs(FfmpegMuxer.LIVE_PROCESSES);
        assertThatCode(() -> view.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── 2. terminateAll on empty set ──────────────────────────────────

    @Test
    void terminateAll_givenEmptySet_completesWithoutError() {
        assertThat(FfmpegMuxer.LIVE_PROCESSES).isEmpty();

        assertThatCode(FfmpegMuxer::terminateAll).doesNotThrowAnyException();
    }

    // ── 3. terminateAll kills a sleeping process within 5s ────────────

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void terminateAll_givenSleepingProcess_killsWithinGracePeriod() throws Exception {
        Process sleeper = new ProcessBuilder("sleep", "60").start();
        FfmpegMuxer.LIVE_PROCESSES.add(sleeper);
        assertThat(sleeper.isAlive()).isTrue();

        long start = System.nanoTime();
        FfmpegMuxer.terminateAll();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(sleeper.isAlive()).isFalse();
        // SIGTERM should kill sleep quickly — well under the 5s grace period
        assertThat(elapsedMs).isLessThan(5_000);
    }

    // ── 4. mux success removes process from LIVE_PROCESSES ────────────

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void mux_givenSuccessfulRun_processRemovedFromLiveProcesses(@TempDir Path tmp) throws Exception {
        Path script = fakeScript(tmp, 0);
        Path video = tmp.resolve("video.part");
        Path audio = tmp.resolve("audio.part");
        Path output = tmp.resolve("output.mp4");
        Files.createFile(video);
        Files.createFile(audio);

        FfmpegMuxer muxer = new FfmpegMuxer(script.toString());
        muxer.mux(video, audio, output, false);

        assertThat(FfmpegMuxer.liveProcesses())
                .as("LIVE_PROCESSES should be empty after successful mux")
                .isEmpty();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void transcodeMp3_givenSuccessfulRun_processRemovedFromLiveProcesses(@TempDir Path tmp) throws Exception {
        Path script = fakeScript(tmp, 0);
        Path audio = tmp.resolve("audio.part");
        Path output = tmp.resolve("output.mp3");
        Files.createFile(audio);

        FfmpegMuxer muxer = new FfmpegMuxer(script.toString());
        muxer.transcodeMp3(audio, output, false);

        assertThat(FfmpegMuxer.liveProcesses())
                .as("LIVE_PROCESSES should be empty after successful transcode")
                .isEmpty();
    }

    // ── 5. Multiple instances share LIVE_PROCESSES ────────────────────

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void multipleInstances_shareSameLiveProcessesSet() throws Exception {
        Process sleeper1 = new ProcessBuilder("sleep", "60").start();
        Process sleeper2 = new ProcessBuilder("sleep", "60").start();
        try {
            // Simulate two FfmpegMuxer instances both registering processes
            FfmpegMuxer.LIVE_PROCESSES.add(sleeper1);
            FfmpegMuxer.LIVE_PROCESSES.add(sleeper2);

            assertThat(FfmpegMuxer.liveProcesses()).containsExactlyInAnyOrder(sleeper1, sleeper2);

            // terminateAll kills both — single static set, not per-instance
            FfmpegMuxer.terminateAll();

            assertThat(sleeper1.isAlive()).isFalse();
            assertThat(sleeper2.isAlive()).isFalse();
        } finally {
            sleeper1.destroyForcibly();
            sleeper2.destroyForcibly();
        }
    }

    // ── 6. Exited process: terminateAll skips it ──────────────────────

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void terminateAll_givenAlreadyExitedProcess_skipsWithoutError() throws Exception {
        Process shortLived = new ProcessBuilder("true").start();
        shortLived.waitFor();
        assertThat(shortLived.isAlive()).isFalse();

        FfmpegMuxer.LIVE_PROCESSES.add(shortLived);

        assertThatCode(FfmpegMuxer::terminateAll).doesNotThrowAnyException();
    }

    // ── 7. LIVE_PROCESSES is concurrent-safe (ConcurrentHashMap-backed) ─

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void liveProcesses_givenConcurrentAddRemove_noConcurrentModificationException() throws Exception {
        Process p1 = new ProcessBuilder("sleep", "60").start();
        Process p2 = new ProcessBuilder("sleep", "60").start();
        try {
            FfmpegMuxer.LIVE_PROCESSES.add(p1);
            FfmpegMuxer.LIVE_PROCESSES.add(p2);

            // Iterate while removing — ConcurrentHashMap.newKeySet() must not throw CME
            assertThatCode(() -> {
                for (Process p : FfmpegMuxer.LIVE_PROCESSES) {
                    FfmpegMuxer.LIVE_PROCESSES.remove(p);
                }
            }).doesNotThrowAnyException();
        } finally {
            p1.destroyForcibly();
            p2.destroyForcibly();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────

    /**
     * Creates a fake ffmpeg shell script that exits with the given code.
     */
    private static Path fakeScript(Path dir, int exitCode) throws IOException {
        Path script = dir.resolve("ffmpeg");
        Files.writeString(script, "#!/bin/sh\nexit " + exitCode + "\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }
}
