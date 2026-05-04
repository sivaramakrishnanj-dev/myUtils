package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for {@link PlayerResponseExtractor#checkPlayability(PlayerResponse)}.
 *
 * <p>Covers AC-1.7 (live stream rejection), AC-5.2 (exit-code categories 20/21/11),
 * CT-APP-6 (live fixture), CT-APP-7 (unplayable fixture), and error-message quality.
 */
class CheckPlayabilityTest {

    private static final VideoId TEST_VIDEO_ID = VideoId.of("dQw4w9WgXcQ");

    // ── 1. Parameterized: each PlayabilityStatus → correct exception ───

    @Nested
    @DisplayName("Status → exception mapping")
    class StatusMapping {

        @Test
        @DisplayName("OK + isLive=false → returns same response (pass-through)")
        void checkPlayability_givenOkNotLive_returnsSameResponse() {
            PlayerResponse response = buildResponse(PlayabilityStatus.OK, false);

            PlayerResponse result = PlayerResponseExtractor.checkPlayability(response);

            assertThat(result).isSameAs(response);
        }

        @ParameterizedTest(name = "{0} → VideoUnavailableException (exit 20)")
        @EnumSource(value = PlayabilityStatus.class,
                names = {"UNPLAYABLE", "LOGIN_REQUIRED", "ERROR", "AGE_VERIFICATION_REQUIRED"})
        void checkPlayability_givenUnavailableStatus_throwsVideoUnavailableException(
                PlayabilityStatus status) {
            PlayerResponse response = buildResponse(status, false);

            assertThatThrownBy(() -> PlayerResponseExtractor.checkPlayability(response))
                    .isInstanceOf(VideoUnavailableException.class)
                    .satisfies(ex -> assertThat(((VideoUnavailableException) ex).exitCode()).isEqualTo(20));
        }

        @Test
        @DisplayName("LIVE_STREAM_OFFLINE → LiveStreamException (exit 21)")
        void checkPlayability_givenLiveStreamOffline_throwsLiveStreamException() {
            PlayerResponse response = buildResponse(PlayabilityStatus.LIVE_STREAM_OFFLINE, false);

            assertThatThrownBy(() -> PlayerResponseExtractor.checkPlayability(response))
                    .isInstanceOf(LiveStreamException.class)
                    .satisfies(ex -> assertThat(((LiveStreamException) ex).exitCode()).isEqualTo(21));
        }

        @Test
        @DisplayName("UNKNOWN → InnerTubeParseException (exit 11)")
        void checkPlayability_givenUnknown_throwsInnerTubeParseException() {
            PlayerResponse response = buildResponse(PlayabilityStatus.UNKNOWN, false);

            assertThatThrownBy(() -> PlayerResponseExtractor.checkPlayability(response))
                    .isInstanceOf(InnerTubeParseException.class)
                    .satisfies(ex -> assertThat(((InnerTubeParseException) ex).exitCode()).isEqualTo(11));
        }
    }

    // ── 2. Live-check precedence: isLive=true + OK → LiveStreamException ─

    @Test
    @DisplayName("AC-1.7: isLive=true with status=OK → LiveStreamException (live takes precedence)")
    void checkPlayability_givenOkButIsLive_throwsLiveStreamException() {
        PlayerResponse response = buildResponse(PlayabilityStatus.OK, true);

        assertThatThrownBy(() -> PlayerResponseExtractor.checkPlayability(response))
                .isInstanceOf(LiveStreamException.class)
                .satisfies(ex -> assertThat(((LiveStreamException) ex).exitCode()).isEqualTo(21));
    }

    @Test
    @DisplayName("isLive=true with status=UNPLAYABLE → LiveStreamException (live takes precedence over unavailable)")
    void checkPlayability_givenUnplayableButIsLive_throwsLiveStreamException() {
        PlayerResponse response = buildResponse(PlayabilityStatus.UNPLAYABLE, true);

        assertThatThrownBy(() -> PlayerResponseExtractor.checkPlayability(response))
                .isInstanceOf(LiveStreamException.class);
    }

    // ── 3. Fixture integration: extract + checkPlayability ─────────────

    @Nested
    @DisplayName("Fixture integration (extract + checkPlayability)")
    class FixtureIntegration {

        @Test
        @DisplayName("CT-APP-6: live fixture → LiveStreamException")
        void checkPlayability_givenLiveFixture_throwsLiveStreamException() throws IOException {
            PlayerResponse response = extractFixture("innertube-response-live.json");

            assertThatThrownBy(() -> PlayerResponseExtractor.checkPlayability(response))
                    .isInstanceOf(LiveStreamException.class);
        }

        @Test
        @DisplayName("CT-APP-7: unplayable fixture → VideoUnavailableException")
        void checkPlayability_givenUnplayableFixture_throwsVideoUnavailableException() throws IOException {
            PlayerResponse response = extractFixture("innertube-response-unplayable.json");

            assertThatThrownBy(() -> PlayerResponseExtractor.checkPlayability(response))
                    .isInstanceOf(VideoUnavailableException.class);
        }

        @Test
        @DisplayName("happy fixture → returns response (no throw)")
        void checkPlayability_givenHappyFixture_returnsResponse() throws IOException {
            PlayerResponse response = extractFixture("innertube-response-happy.json");

            PlayerResponse result = PlayerResponseExtractor.checkPlayability(response);

            assertThat(result).isSameAs(response);
        }
    }

    // ── 4. Error message quality ───────────────────────────────────────

    @Nested
    @DisplayName("Error message quality (AC-1.7, AC-5.1)")
    class ErrorMessageQuality {

        @Test
        @DisplayName("AC-1.7: LiveStreamException message contains 'live' (case-insensitive)")
        void checkPlayability_givenLive_messageContainsLive() {
            PlayerResponse response = buildResponse(PlayabilityStatus.OK, true);

            assertThatThrownBy(() -> PlayerResponseExtractor.checkPlayability(response))
                    .isInstanceOf(LiveStreamException.class)
                    .message().containsIgnoringCase("live");
        }

        @Test
        @DisplayName("AC-5.1: VideoUnavailableException message names the videoId")
        void checkPlayability_givenUnplayable_messageNamesVideoId() {
            PlayerResponse response = buildResponse(PlayabilityStatus.UNPLAYABLE, false);

            assertThatThrownBy(() -> PlayerResponseExtractor.checkPlayability(response))
                    .isInstanceOf(VideoUnavailableException.class)
                    .hasMessageContaining(TEST_VIDEO_ID.value());
        }

        @Test
        @DisplayName("LiveStreamException message names the videoId")
        void checkPlayability_givenLive_messageNamesVideoId() {
            PlayerResponse response = buildResponse(PlayabilityStatus.LIVE_STREAM_OFFLINE, false);

            assertThatThrownBy(() -> PlayerResponseExtractor.checkPlayability(response))
                    .isInstanceOf(LiveStreamException.class)
                    .hasMessageContaining(TEST_VIDEO_ID.value());
        }

        @Test
        @DisplayName("VideoUnavailableException message includes the status name")
        void checkPlayability_givenLoginRequired_messageIncludesStatus() {
            PlayerResponse response = buildResponse(PlayabilityStatus.LOGIN_REQUIRED, false);

            assertThatThrownBy(() -> PlayerResponseExtractor.checkPlayability(response))
                    .isInstanceOf(VideoUnavailableException.class)
                    .hasMessageContaining("LOGIN_REQUIRED");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static PlayerResponse buildResponse(PlayabilityStatus status, boolean isLive) {
        return new PlayerResponse(
                new VideoDetails(TEST_VIDEO_ID, "Test Video", isLive, false, Optional.empty()),
                status,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    private static PlayerResponse extractFixture(String name) throws IOException {
        try (InputStream is = CheckPlayabilityTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return PlayerResponseExtractor.extract(
                    new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
