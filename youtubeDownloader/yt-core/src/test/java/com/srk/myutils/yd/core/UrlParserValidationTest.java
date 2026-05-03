package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive validation tests for {@link UrlParser} — AC-1.1.
 *
 * <p>Covers all four accepted URL shapes, rejection of unsupported shapes,
 * error-message quality, and edge cases. The implementer's {@link UrlParserTest}
 * is left untouched; this class provides the exhaustive coverage.
 *
 * <p>Spec decisions documented in this test:
 * <ul>
 *   <li>{@code m.youtube.com/shorts/<id>}: REJECTED — AC-1.1 enumerates only
 *       {@code m.youtube.com/watch?v=<id>} for the mobile host.</li>
 *   <li>{@code youtu.be/<id>/extra}: REJECTED — AC-1.1 short link shape is
 *       {@code youtu.be/<id>} with a single path segment.</li>
 * </ul>
 */
@DisplayName("UrlParser — AC-1.1 URL shape validation")
class UrlParserValidationTest {

    private final UrlParser parser = new UrlParser();

    // ── Valid URLs ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid URL shapes → correct VideoId")
    class ValidUrls {

        @ParameterizedTest(name = "[{index}] {0} → dQw4w9WgXcQ")
        @DisplayName("All four AC-1.1 shapes + query-param variations")
        @ValueSource(strings = {
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share&t=10",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=youtu.be",
                "https://youtu.be/dQw4w9WgXcQ",
                "https://youtu.be/dQw4w9WgXcQ?t=42s",
                "https://www.youtube.com/shorts/dQw4w9WgXcQ",
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        })
        void parse_givenValidUrl_returnsExpectedVideoId(String url) {
            assertThat(parser.parse(url).value()).isEqualTo("dQw4w9WgXcQ");
        }

        @ParameterizedTest(name = "[{index}] id={0}")
        @DisplayName("Different valid 11-char video IDs")
        @ValueSource(strings = {"dQw4w9WgXcQ", "livelivelv1", "aaaaaaaaaaa", "ABC___---00"})
        void parse_givenDifferentValidIds_returnsCorrectId(String id) {
            String url = "https://www.youtube.com/watch?v=" + id;

            assertThat(parser.parse(url).value()).isEqualTo(id);
        }
    }

    // ── Invalid URLs ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid URLs → UrlParseException")
    class InvalidUrls {

        @ParameterizedTest(name = "[{index}] null → UrlParseException")
        @NullSource
        void parse_givenNull_throws(String url) {
            assertThatThrownBy(() -> parser.parse(url))
                    .isInstanceOf(UrlParseException.class);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → UrlParseException")
        @DisplayName("Empty / whitespace")
        @ValueSource(strings = {"", " ", "   "})
        void parse_givenBlank_throws(String url) {
            assertThatThrownBy(() -> parser.parse(url))
                    .isInstanceOf(UrlParseException.class);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("Wrong scheme")
        @ValueSource(strings = {
                "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "ftp://www.youtube.com/watch?v=dQw4w9WgXcQ"
        })
        void parse_givenNonHttpsScheme_throws(String url) {
            assertThatThrownBy(() -> parser.parse(url))
                    .isInstanceOf(UrlParseException.class);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("Wrong or unsupported host")
        @ValueSource(strings = {
                "https://youtube.com/watch?v=dQw4w9WgXcQ",
                "https://www.google.com/",
                "https://evil.com/watch?v=dQw4w9WgXcQ"
        })
        void parse_givenUnsupportedHost_throws(String url) {
            assertThatThrownBy(() -> parser.parse(url))
                    .isInstanceOf(UrlParseException.class);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("www.youtube.com — unsupported paths")
        @ValueSource(strings = {
                "https://www.youtube.com/",
                "https://www.youtube.com/watch",
                "https://www.youtube.com/embed/dQw4w9WgXcQ",
                "https://www.youtube.com/shorts/"
        })
        void parse_givenWwwUnsupportedPath_throws(String url) {
            assertThatThrownBy(() -> parser.parse(url))
                    .isInstanceOf(UrlParseException.class);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("Missing or invalid v= parameter")
        @ValueSource(strings = {
                "https://www.youtube.com/watch?v=",
                "https://www.youtube.com/watch?v=short",
                "https://www.youtube.com/watch?v=way_too_long_id12",
                "https://www.youtube.com/watch?v=invalid!!!!!"
        })
        void parse_givenBadVParam_throws(String url) {
            assertThatThrownBy(() -> parser.parse(url))
                    .isInstanceOf(UrlParseException.class);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("youtu.be — missing or extra path segments")
        @ValueSource(strings = {
                "https://youtu.be/",
                "https://youtu.be/dQw4w9WgXcQ/extra"
        })
        void parse_givenShortLinkBadPath_throws(String url) {
            assertThatThrownBy(() -> parser.parse(url))
                    .isInstanceOf(UrlParseException.class);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("Completely malformed input")
        @ValueSource(strings = {
                "not-a-url-at-all"
        })
        void parse_givenMalformedInput_throws(String url) {
            assertThatThrownBy(() -> parser.parse(url))
                    .isInstanceOf(UrlParseException.class);
        }

        @Test
        @DisplayName("m.youtube.com/shorts/<id> — strict AC-1.1: mobile host only accepts /watch?v=")
        void parse_givenMobileShortsUrl_throws() {
            assertThatThrownBy(() -> parser.parse("https://m.youtube.com/shorts/dQw4w9WgXcQ"))
                    .isInstanceOf(UrlParseException.class);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("m.youtube.com — non-/watch paths with v= param (C3: must reject with 'Unsupported URL')")
        @ValueSource(strings = {
                "https://m.youtube.com/embed/foo?v=dQw4w9WgXcQ",
                "https://m.youtube.com/shorts/dQw4w9WgXcQ?v=dQw4w9WgXcQ",
                "https://m.youtube.com/playlist?v=dQw4w9WgXcQ",
                "https://m.youtube.com/channel/UC1234?v=dQw4w9WgXcQ",
                "https://m.youtube.com/?v=dQw4w9WgXcQ"
        })
        void parse_givenMobileNonWatchPathWithVParam_throws(String url) {
            assertThatThrownBy(() -> parser.parse(url))
                    .isInstanceOf(UrlParseException.class)
                    .hasMessageContaining("Unsupported URL");
        }
    }

    // ── Error message quality ───────────────────────────────────────────

    @Nested
    @DisplayName("Error message quality — AC-5.1, AC-11.4")
    class ErrorMessages {

        @Test
        @DisplayName("Null input → message contains 'null' marker")
        void parse_givenNull_messageContainsNullMarker() {
            assertThatThrownBy(() -> parser.parse(null))
                    .isInstanceOf(UrlParseException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("Empty input → message contains 'empty' marker")
        void parse_givenEmpty_messageContainsEmptyMarker() {
            assertThatThrownBy(() -> parser.parse(""))
                    .isInstanceOf(UrlParseException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("Unsupported URL shape → message contains the raw URL")
        void parse_givenUnsupportedShape_messageContainsRawUrl() {
            String raw = "https://www.youtube.com/embed/dQw4w9WgXcQ";

            assertThatThrownBy(() -> parser.parse(raw))
                    .isInstanceOf(UrlParseException.class)
                    .hasMessageContaining(raw);
        }

        @Test
        @DisplayName("Invalid video id (via VideoId.of) → message contains 'video id'")
        void parse_givenInvalidVideoIdChars_messageIndicatesVideoIdProblem() {
            assertThatThrownBy(() -> parser.parse("https://www.youtube.com/watch?v=invalid!!!!!"))
                    .isInstanceOf(UrlParseException.class)
                    .hasMessageContaining("video id");
        }

        @Test
        @DisplayName("Missing v= param → message contains 'v parameter'")
        void parse_givenMissingVParam_messageIndicatesMissingParam() {
            assertThatThrownBy(() -> parser.parse("https://www.youtube.com/watch"))
                    .isInstanceOf(UrlParseException.class)
                    .hasMessageContaining("v parameter");
        }
    }
}
