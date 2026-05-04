package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link PlayerResponseExtractor#checkPlayability(PlayerResponse)}.
 * Happy path only — tester adds exhaustive coverage for each status value.
 */
class PlayerResponsePlayabilityTest {

    @Test
    void checkPlayability_givenOkAndNotLive_returnsSameResponse() {
        PlayerResponse response = new PlayerResponse(
                new VideoDetails(
                        VideoId.of("dQw4w9WgXcQ"),
                        "Test Video",
                        false,
                        false,
                        Optional.empty()),
                PlayabilityStatus.OK,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());

        PlayerResponse result = PlayerResponseExtractor.checkPlayability(response);

        assertThat(result).isSameAs(response);
    }
}
