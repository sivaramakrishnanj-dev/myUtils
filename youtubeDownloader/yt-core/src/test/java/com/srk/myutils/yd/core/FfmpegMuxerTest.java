package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization test for {@link FfmpegMuxer} (T-3.1).
 * Verifies the class instantiates and throws {@link FfmpegException} for a bogus path.
 * Does not require ffmpeg on PATH — tester adds comprehensive tests.
 */
class FfmpegMuxerTest {

    @Test
    void probeVersion_givenBogusPath_throwsFfmpegException() {
        var muxer = new FfmpegMuxer("/nonexistent/ffmpeg");
        assertThatThrownBy(muxer::probeVersion)
                .isInstanceOf(FfmpegException.class)
                .hasMessageContaining("ffmpeg not found on PATH or version check failed");
    }
}
