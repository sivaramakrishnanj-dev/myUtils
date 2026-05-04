package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for T-3.5: FfmpegMuxer shutdown hook cooperation.
 * Verifies that {@link FfmpegMuxer#terminateAll()} sends SIGTERM (then SIGKILL
 * if needed) to tracked child processes, satisfying INV-8 and 02-architecture.md § 5.
 *
 * <p>Spawns a real {@code sleep} process as a stand-in for ffmpeg.
 * Cannot fire the actual JVM shutdown hook in a unit test, so
 * {@code terminateAll()} is invoked directly (package-private).
 */
@DisabledOnOs(OS.WINDOWS)
class FfmpegMuxerShutdownTest {

    @Test
    void terminateAll_killsTrackedProcess() throws Exception {
        Process sleeper = new ProcessBuilder("sleep", "300").start();
        assertThat(sleeper.isAlive()).isTrue();

        FfmpegMuxer.LIVE_PROCESSES.add(sleeper);
        try {
            assertThat(FfmpegMuxer.liveProcesses()).contains(sleeper);

            FfmpegMuxer.terminateAll();

            assertThat(sleeper.isAlive()).isFalse();
        } finally {
            FfmpegMuxer.LIVE_PROCESSES.remove(sleeper);
            if (sleeper.isAlive()) {
                sleeper.destroyForcibly();
            }
        }
    }
}
