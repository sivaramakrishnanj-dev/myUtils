package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixture-based tests for {@link PlayerResponseExtractor} covering CT-APP-1..CT-APP-7
 * and per-fixture assertions (unplayable, live, cipher, asr-only, no-captions).
 */
class PlayerResponseExtractorFixtureTest {

    // ── CT-APP-1: happy fixture → videoId ──────────────────────────────

    @Test
    @DisplayName("CT-APP-1: happy fixture → videoId == dQw4w9WgXcQ")
    void extract_givenHappyFixture_videoIdMatchesFixture() throws IOException {
        PlayerResponse r = extract("innertube-response-happy.json");

        assertThat(r.videoDetails().videoId().value()).isEqualTo("dQw4w9WgXcQ");
    }

    // ── CT-APP-2: happy fixture → adaptiveFormats count ────────────────

    @Test
    @DisplayName("CT-APP-2: happy fixture → adaptiveFormats.size() == 3")
    void extract_givenHappyFixture_hasThreeAdaptiveFormats() throws IOException {
        PlayerResponse r = extract("innertube-response-happy.json");

        assertThat(r.adaptiveFormats()).hasSize(3);
    }

    // ── CT-APP-6: live fixture → isLive true ───────────────────────────

    @Test
    @DisplayName("CT-APP-6: live fixture → videoDetails.isLive == true")
    void extract_givenLiveFixture_isLiveTrue() throws IOException {
        PlayerResponse r = extract("innertube-response-live.json");

        assertThat(r.videoDetails().isLive()).isTrue();
    }

    // ── CT-APP-7: unplayable fixture → UNPLAYABLE status ───────────────

    @Test
    @DisplayName("CT-APP-7: unplayable fixture → playabilityStatus == UNPLAYABLE")
    void extract_givenUnplayableFixture_statusIsUnplayable() throws IOException {
        PlayerResponse r = extract("innertube-response-unplayable.json");

        assertThat(r.playabilityStatus()).isEqualTo(PlayabilityStatus.UNPLAYABLE);
    }

    // ── Happy fixture: format classification ───────────────────────────

    @Nested
    @DisplayName("Happy fixture — format classification")
    class HappyFormatClassification {

        @Test
        void extract_givenHappyFixture_firstTwoFormatsAreVideo() throws IOException {
            PlayerResponse r = extract("innertube-response-happy.json");

            assertThat(r.adaptiveFormats().get(0).isVideo()).isTrue();
            assertThat(r.adaptiveFormats().get(1).isVideo()).isTrue();
        }

        @Test
        void extract_givenHappyFixture_thirdFormatIsAudio() throws IOException {
            PlayerResponse r = extract("innertube-response-happy.json");

            assertThat(r.adaptiveFormats().get(2).isAudio()).isTrue();
        }

        @Test
        void extract_givenHappyFixture_noCipherOnDirectFormats() throws IOException {
            PlayerResponse r = extract("innertube-response-happy.json");

            assertThat(r.adaptiveFormats()).allMatch(f -> !f.hasCipher());
        }

        @Test
        void extract_givenHappyFixture_formatsOrderPreserved() throws IOException {
            PlayerResponse r = extract("innertube-response-happy.json");

            assertThat(r.adaptiveFormats())
                    .extracting(Format::itag)
                    .containsExactly(137, 136, 140);
        }
    }

    // ── Happy fixture: captions ────────────────────────────────────────

    @Test
    void extract_givenHappyFixture_hasTwoCaptionTracks() throws IOException {
        PlayerResponse r = extract("innertube-response-happy.json");

        assertThat(r.captionTracks()).hasSize(2);
        assertThat(r.captionTracks().get(0).isAsr()).isFalse();
        assertThat(r.captionTracks().get(1).isAsr()).isTrue();
    }

    // ── Happy fixture: thumbnails ──────────────────────────────────────

    @Test
    void extract_givenHappyFixture_hasThreeThumbnails() throws IOException {
        PlayerResponse r = extract("innertube-response-happy.json");

        assertThat(r.thumbnails()).hasSize(3);
        assertThat(r.thumbnails().get(0).width()).isEqualTo(320);
    }

    // ── Happy fixture: audioSampleRate string→int (CT type-conversion) ─

    @Test
    @DisplayName("audioSampleRate string '44100' → OptionalInt.of(44100)")
    void extract_givenHappyFixture_audioSampleRateParsedFromString() throws IOException {
        PlayerResponse r = extract("innertube-response-happy.json");

        assertThat(r.adaptiveFormats().get(2).audioSampleRate()).hasValue(44100);
    }

    @Test
    @DisplayName("contentLength string '5000000' → Optional.of(5000000L)")
    void extract_givenHappyFixture_contentLengthParsedFromString() throws IOException {
        PlayerResponse r = extract("innertube-response-happy.json");

        assertThat(r.adaptiveFormats().get(2).contentLength()).hasValue(5_000_000L);
    }

    // ── Cipher fixture ─────────────────────────────────────────────────

    @Test
    @DisplayName("cipher fixture → all formats have cipher, no direct URL")
    void extract_givenCipherFixture_allFormatsHaveCipher() throws IOException {
        PlayerResponse r = extract("innertube-response-cipher.json");

        assertThat(r.adaptiveFormats()).isNotEmpty();
        assertThat(r.adaptiveFormats()).allMatch(Format::hasCipher);
    }

    @Test
    void extract_givenCipherFixture_statusIsOk() throws IOException {
        PlayerResponse r = extract("innertube-response-cipher.json");

        assertThat(r.playabilityStatus()).isEqualTo(PlayabilityStatus.OK);
    }

    // ── ASR-only fixture ───────────────────────────────────────────────

    @Test
    @DisplayName("asr-only fixture → all caption tracks are ASR")
    void extract_givenAsrOnlyFixture_allTracksAreAsr() throws IOException {
        PlayerResponse r = extract("innertube-response-asr-only.json");

        assertThat(r.captionTracks()).isNotEmpty();
        assertThat(r.captionTracks()).allMatch(CaptionTrack::isAsr);
    }

    @Test
    void extract_givenAsrOnlyFixture_hasOneFormat() throws IOException {
        PlayerResponse r = extract("innertube-response-asr-only.json");

        assertThat(r.adaptiveFormats()).hasSize(1);
        assertThat(r.adaptiveFormats().get(0).isAudio()).isTrue();
    }

    // ── No-captions fixture ────────────────────────────────────────────

    @Test
    @DisplayName("no-captions fixture → captionTracks is empty list (not null)")
    void extract_givenNoCaptionsFixture_captionTracksIsEmptyList() throws IOException {
        PlayerResponse r = extract("innertube-response-no-captions.json");

        assertThat(r.captionTracks()).isNotNull().isEmpty();
    }

    @Test
    void extract_givenNoCaptionsFixture_statusIsOk() throws IOException {
        PlayerResponse r = extract("innertube-response-no-captions.json");

        assertThat(r.playabilityStatus()).isEqualTo(PlayabilityStatus.OK);
    }

    // ── Unplayable fixture: isPrivate ──────────────────────────────────

    @Test
    void extract_givenUnplayableFixture_isPrivateTrue() throws IOException {
        PlayerResponse r = extract("innertube-response-unplayable.json");

        assertThat(r.videoDetails().isPrivate()).isTrue();
    }

    @Test
    @DisplayName("unplayable fixture → adaptiveFormats is empty (no streamingData)")
    void extract_givenUnplayableFixture_noFormats() throws IOException {
        PlayerResponse r = extract("innertube-response-unplayable.json");

        assertThat(r.adaptiveFormats()).isNotNull().isEmpty();
    }

    // ── Live fixture: no streamingData ─────────────────────────────────

    @Test
    @DisplayName("live fixture → adaptiveFormats is empty (no streamingData)")
    void extract_givenLiveFixture_noFormats() throws IOException {
        PlayerResponse r = extract("innertube-response-live.json");

        assertThat(r.adaptiveFormats()).isNotNull().isEmpty();
    }

    @Test
    void extract_givenLiveFixture_noCaptions() throws IOException {
        PlayerResponse r = extract("innertube-response-live.json");

        assertThat(r.captionTracks()).isNotNull().isEmpty();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static PlayerResponse extract(String fixtureName) throws IOException {
        return PlayerResponseExtractor.extract(loadFixture(fixtureName));
    }

    private static String loadFixture(String name) throws IOException {
        try (InputStream is = PlayerResponseExtractorFixtureTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
