package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link UrlParser} — happy-path only.
 * Exhaustive coverage (all four shapes, rejection cases, edge cases) is the tester's job.
 */
class UrlParserTest {

    @Test
    void parse_givenCanonicalWatchUrl_returnsVideoId() {
        VideoId id = new UrlParser().parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(id.value()).isEqualTo("dQw4w9WgXcQ");
    }
}
