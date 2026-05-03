package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link LanguageCode} — happy-path only.
 * Exhaustive coverage (invalid inputs, edge cases, property tests) is the tester's job.
 */
class LanguageCodeTest {

    @Test
    void of_givenPrimaryOnly_returnsLanguageCodeWithSameValue() {
        LanguageCode lc = LanguageCode.of("en");
        assertThat(lc.value()).isEqualTo("en");
        assertThat(lc.primary()).isEqualTo("en");
    }

    @Test
    void of_givenPrimaryWithSubtag_normalizesAndPreservesSubtagCase() {
        LanguageCode lc = LanguageCode.of("EN-US");
        assertThat(lc.value()).isEqualTo("en-US");
        assertThat(lc.primary()).isEqualTo("en");
    }

    @Test
    void matches_givenSamePrimary_returnsTrue() {
        LanguageCode en = LanguageCode.of("en");
        LanguageCode enUs = LanguageCode.of("en-US");
        assertThat(en.matches(enUs)).isTrue();
        assertThat(enUs.matches(en)).isTrue();
    }
}
