package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive validation tests for {@link VideoId} — covers AC-1.1 input domain
 * and cross-references CT-REQ-N2 / CT-REQ-N3 pattern-mismatch contract tests.
 */
class VideoIdValidationTest {

    // ── Invalid inputs → UrlParseException ──────────────────────────

    @ParameterizedTest(name = "of(\"{0}\") → UrlParseException")
    @NullSource
    void of_givenNull_throwsUrlParseException(String input) {
        assertThatThrownBy(() -> VideoId.of(input))
                .isInstanceOf(UrlParseException.class);
    }

    @ParameterizedTest(name = "of(\"{0}\") → UrlParseException")
    @ValueSource(strings = {
            "",                    // empty
            "short",               // 5 chars — too short
            "dQw4w9WgXc",          // 10 chars — one short (CT-REQ-N2 cross-ref)
            "dQw4w9WgXcQ1",        // 12 chars — one long (CT-REQ-N3 cross-ref)
            "way_too_long_12345",  // >11 chars
            "invalid!!!!!",        // 11 chars, invalid char '!'
            "!@#$%^&*()_",         // 11 chars, multiple invalid
            "           ",         // 11 spaces — whitespace not in pattern
            "hello world",         // 11 chars with space
            "dQw4w9WgXc\t",        // 11 chars with tab
    })
    void of_givenInvalidInput_throwsUrlParseException(String input) {
        assertThatThrownBy(() -> VideoId.of(input))
                .isInstanceOf(UrlParseException.class)
                .hasMessageContaining("Invalid video id");
    }

    // ── Valid inputs → VideoId ──────────────────────────────────────

    @ParameterizedTest(name = "of(\"{0}\") → valid VideoId")
    @ValueSource(strings = {
            "dQw4w9WgXcQ",   // canonical Rick Astley
            "aaaaaaaaaaa",   // all lowercase
            "AAAAAAAAAAA",   // all uppercase
            "01234567890",   // all digits
            "ABC___---00",   // underscores and hyphens (AC-1.1 pattern chars)
            "A0B1C2D3E4F",   // mixed alphanumeric
            "-----------",   // all hyphens
            "___________",   // all underscores
    })
    void of_givenValidId_returnsVideoIdWithSameValue(String input) {
        VideoId id = VideoId.of(input);
        assertThat(id.value()).isEqualTo(input);
    }

    // ── PATTERN field ───────────────────────────────────────────────

    @Test
    void pattern_isNotNull() {
        assertThat(VideoId.PATTERN).isNotNull();
    }

    @Test
    void pattern_matchesValidId() {
        assertThat(VideoId.PATTERN.matcher("dQw4w9WgXcQ").matches()).isTrue();
    }

    @Test
    void pattern_rejectsInvalidId() {
        assertThat(VideoId.PATTERN.matcher("short").matches()).isFalse();
    }

    // ── Record equality and toString ────────────────────────────────

    @Test
    void equals_sameValue_areEqual() {
        assertThat(VideoId.of("dQw4w9WgXcQ")).isEqualTo(VideoId.of("dQw4w9WgXcQ"));
    }

    @Test
    void equals_differentValue_areNotEqual() {
        assertThat(VideoId.of("dQw4w9WgXcQ")).isNotEqualTo(VideoId.of("aaaaaaaaaaa"));
    }

    @Test
    void hashCode_sameValue_areEqual() {
        assertThat(VideoId.of("dQw4w9WgXcQ").hashCode())
                .isEqualTo(VideoId.of("dQw4w9WgXcQ").hashCode());
    }

    @Test
    void toString_containsValue() {
        assertThat(VideoId.of("dQw4w9WgXcQ").toString()).contains("dQw4w9WgXcQ");
    }
}
