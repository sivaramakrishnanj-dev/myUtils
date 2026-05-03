package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive validation tests for {@link LanguageCode} — covers AC-8.2
 * primary-subtag match, BCP-47-ish pattern validation, normalization, and
 * record semantics.
 */
class LanguageCodeValidationTest {

    // ── Invalid inputs via of() → IllegalArgumentException ──────────

    @ParameterizedTest(name = "of(null) → IllegalArgumentException")
    @NullSource
    void of_givenNull_throwsIllegalArgumentException(String input) {
        assertThatThrownBy(() -> LanguageCode.of(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "of(\"{0}\") → IllegalArgumentException")
    @ValueSource(strings = {
            "",           // empty
            "e",          // 1 char primary — too short
            "ENGL",       // 4 chars primary — too long
            "123",        // non-letter primary
            "en-",        // trailing dash, empty subtag
            "-en",        // leading dash
            "en-US-extra" // multi-subtag — pattern allows only one optional subtag
    })
    void of_givenInvalidInput_throwsIllegalArgumentException(String input) {
        assertThatThrownBy(() -> LanguageCode.of(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid language code");
    }

    // ── Invalid inputs via canonical constructor → IllegalArgumentException ──

    @ParameterizedTest(name = "new LanguageCode(\"{0}\") → IllegalArgumentException")
    @NullSource
    void constructor_givenNull_throwsIllegalArgumentException(String input) {
        assertThatThrownBy(() -> new LanguageCode(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "new LanguageCode(\"{0}\") → IllegalArgumentException")
    @ValueSource(strings = {
            "EN",    // uppercase primary rejected by constructor (pattern requires lowercase)
            "Fr",    // mixed-case primary
            "ENGL",  // 4 chars
            ""       // empty
    })
    void constructor_givenUppercasePrimary_throwsIllegalArgumentException(String input) {
        assertThatThrownBy(() -> new LanguageCode(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Valid inputs via of() ───────────────────────────────────────

    @ParameterizedTest(name = "of(\"{0}\") → valid LanguageCode")
    @ValueSource(strings = {
            "en",      // 2-letter primary
            "fr",      // 2-letter primary
            "eng",     // 3-letter primary
            "en-US",   // primary + region
            "en-GB",   // primary + region
            "fr-CA",   // primary + region
            "zh-Hans", // primary + script subtag (still [A-Za-z0-9]+)
            "pt-BR"    // primary + region
    })
    void of_givenValidInput_returnsLanguageCode(String input) {
        LanguageCode lc = LanguageCode.of(input);
        assertThat(lc).isNotNull();
    }

    // ── of() normalization ──────────────────────────────────────────

    @Test
    void of_givenUppercasePrimary_normalizesToLowercase() {
        LanguageCode lc = LanguageCode.of("EN");
        assertThat(lc.value()).isEqualTo("en");
    }

    @Test
    void of_givenMixedCasePrimaryWithSubtag_normalizesOnlyPrimary() {
        LanguageCode lc = LanguageCode.of("EN-US");
        assertThat(lc.value()).isEqualTo("en-US");
    }

    @Test
    void of_givenLowercaseSubtag_preservesCase() {
        LanguageCode lc = LanguageCode.of("en-us");
        assertThat(lc.value()).isEqualTo("en-us");
    }

    @Test
    void of_givenAlreadyLowercase_returnsUnchanged() {
        LanguageCode lc = LanguageCode.of("en");
        assertThat(lc.value()).isEqualTo("en");
    }

    // ── primary() accessor ──────────────────────────────────────────

    @ParameterizedTest(name = "of(\"{0}\").primary() == \"{1}\"")
    @CsvSource({
            "en,     en",
            "en-US,  en",
            "zh-Hans, zh",
            "eng,    eng",
            "pt-BR,  pt"
    })
    void primary_returnsLowercasePrimarySubtag(String input, String expectedPrimary) {
        assertThat(LanguageCode.of(input).primary()).isEqualTo(expectedPrimary);
    }

    // ── matches() — AC-8.2 primary-subtag match ─────────────────────

    @Test
    void matches_givenSameExactValue_returnsTrue() {
        LanguageCode en = LanguageCode.of("en");
        assertThat(en.matches(LanguageCode.of("en"))).isTrue();
    }

    @Test
    void matches_givenPrimaryOnlyVsRegional_returnsTrue() {
        LanguageCode en = LanguageCode.of("en");
        LanguageCode enUs = LanguageCode.of("en-US");
        assertThat(en.matches(enUs)).isTrue();
    }

    @Test
    void matches_givenRegionalVsPrimaryOnly_returnsTrue_symmetric() {
        LanguageCode enUs = LanguageCode.of("en-US");
        LanguageCode en = LanguageCode.of("en");
        assertThat(enUs.matches(en)).isTrue();
    }

    @Test
    void matches_givenDifferentRegionsSamePrimary_returnsTrue() {
        LanguageCode enUs = LanguageCode.of("en-US");
        LanguageCode enGb = LanguageCode.of("en-GB");
        assertThat(enUs.matches(enGb)).isTrue();
    }

    @Test
    void matches_givenDifferentPrimary_returnsFalse() {
        LanguageCode en = LanguageCode.of("en");
        LanguageCode fr = LanguageCode.of("fr");
        assertThat(en.matches(fr)).isFalse();
    }

    @Test
    void matches_givenTwoLetterVsThreeLetterPrimary_returnsFalse() {
        // "en" != "eng" even if culturally close — BCP-47 codes are distinct
        LanguageCode en = LanguageCode.of("en");
        LanguageCode eng = LanguageCode.of("eng");
        assertThat(en.matches(eng)).isFalse();
    }

    @Test
    void matches_givenSymmetryHoldsForAllPairs() {
        LanguageCode en = LanguageCode.of("en");
        LanguageCode enUs = LanguageCode.of("en-US");
        LanguageCode enGb = LanguageCode.of("en-GB");
        LanguageCode fr = LanguageCode.of("fr");

        // symmetric: a.matches(b) == b.matches(a)
        assertThat(en.matches(enUs)).isEqualTo(enUs.matches(en));
        assertThat(en.matches(enGb)).isEqualTo(enGb.matches(en));
        assertThat(enUs.matches(enGb)).isEqualTo(enGb.matches(enUs));
        assertThat(en.matches(fr)).isEqualTo(fr.matches(en));
    }

    @Test
    void matches_givenNull_throwsNullPointerException() {
        LanguageCode en = LanguageCode.of("en");
        assertThatThrownBy(() -> en.matches(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── Record semantics: equals, hashCode, toString ────────────────

    @Test
    void equals_sameValue_areEqual() {
        assertThat(LanguageCode.of("en")).isEqualTo(LanguageCode.of("en"));
    }

    @Test
    void equals_differentValue_areNotEqual() {
        assertThat(LanguageCode.of("en")).isNotEqualTo(LanguageCode.of("fr"));
    }

    @Test
    void equals_sameNormalizedValue_areEqual() {
        // of("EN") normalizes to "en", same as of("en")
        assertThat(LanguageCode.of("EN")).isEqualTo(LanguageCode.of("en"));
    }

    @Test
    void hashCode_sameValue_areEqual() {
        assertThat(LanguageCode.of("en").hashCode())
                .isEqualTo(LanguageCode.of("en").hashCode());
    }

    @Test
    void toString_containsValue() {
        assertThat(LanguageCode.of("en-US").toString()).contains("en-US");
    }

    // ── PATTERN field ───────────────────────────────────────────────

    @Test
    void pattern_isNotNull() {
        assertThat(LanguageCode.PATTERN).isNotNull();
    }

    @Test
    void pattern_matchesValidCode() {
        assertThat(LanguageCode.PATTERN.matcher("en").matches()).isTrue();
        assertThat(LanguageCode.PATTERN.matcher("en-US").matches()).isTrue();
    }

    @Test
    void pattern_rejectsInvalidCode() {
        assertThat(LanguageCode.PATTERN.matcher("ENGL").matches()).isFalse();
        assertThat(LanguageCode.PATTERN.matcher("").matches()).isFalse();
    }
}
