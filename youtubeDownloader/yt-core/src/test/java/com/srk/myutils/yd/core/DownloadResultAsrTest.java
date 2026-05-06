package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for T-4.7 — verifies {@code DownloadResult.usedAsrFallback}
 * field is accessible and carries both {@code false} (default orchestrator paths)
 * and {@code true} (ASR fallback per AC-7.3, INV-16).
 *
 * <p>INV-16: {@code usedAsrFallback} is set exactly once, only during
 * {@code SELECTING_FORMATS}. End-to-end wiring lands in T-4.10.
 */
class DownloadResultAsrTest {

    private static final VideoId SAMPLE_ID = VideoId.of("dQw4w9WgXcQ");

    @Test
    void usedAsrFallback_defaultsFalse_whenNoAsrSelected() {
        DownloadResult result = new DownloadResult(
                SAMPLE_ID, "title",
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                false
        );

        assertThat(result.usedAsrFallback()).isFalse();
    }

    @Test
    void usedAsrFallback_true_whenAsrFallbackUsed() {
        DownloadResult result = new DownloadResult(
                SAMPLE_ID, "title",
                Optional.empty(), Optional.empty(), Optional.of(Path.of("out.srt")),
                Optional.of(Path.of("out.txt")), Optional.empty(),
                true
        );

        assertThat(result.usedAsrFallback()).isTrue();
    }
}
