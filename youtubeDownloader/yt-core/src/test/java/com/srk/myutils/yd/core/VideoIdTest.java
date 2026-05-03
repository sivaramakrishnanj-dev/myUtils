package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link VideoId} — happy-path only.
 * Exhaustive coverage (null, empty, wrong length, special chars) is the tester's job.
 */
class VideoIdTest {

    @Test
    void of_givenCanonicalVideoId_returnsVideoIdWithSameValue() {
        VideoId id = VideoId.of("dQw4w9WgXcQ");
        assertThat(id.value()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void of_givenIdWithUnderscoreAndHyphen_returnsVideoId() {
        VideoId id = VideoId.of("a_B-c1D2e3F");
        assertThat(id.value()).isEqualTo("a_B-c1D2e3F");
    }
}
