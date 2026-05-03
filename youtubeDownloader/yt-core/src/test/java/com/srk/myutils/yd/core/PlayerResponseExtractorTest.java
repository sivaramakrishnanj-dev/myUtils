package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link PlayerResponseExtractor} — happy-path only.
 * Exhaustive fixture coverage (unplayable, live, cipher, asr-only, no-captions,
 * negative CT-RESP-N1..N8) is the tester's job.
 */
class PlayerResponseExtractorTest {

    @Test
    void extract_givenHappyFixture_returnsFullyPopulatedPlayerResponse() throws IOException {
        String json = loadFixture("innertube-response-happy.json");

        PlayerResponse response = PlayerResponseExtractor.extract(json);

        // videoDetails
        assertThat(response.videoDetails().videoId().value()).isEqualTo("dQw4w9WgXcQ");
        assertThat(response.videoDetails().title())
                .isEqualTo("Rick Astley - Never Gonna Give You Up (Official Music Video)");
        assertThat(response.videoDetails().isLive()).isFalse();
        assertThat(response.videoDetails().isPrivate()).isFalse();
        assertThat(response.videoDetails().audioLanguage()).isPresent();
        assertThat(response.videoDetails().audioLanguage().get().value()).isEqualTo("en");

        // playabilityStatus
        assertThat(response.playabilityStatus()).isEqualTo(PlayabilityStatus.OK);

        // adaptiveFormats
        assertThat(response.adaptiveFormats()).hasSize(3);
        Format video1080 = response.adaptiveFormats().get(0);
        assertThat(video1080.itag()).isEqualTo(137);
        assertThat(video1080.isVideo()).isTrue();
        assertThat(video1080.height()).hasValue(1080);
        assertThat(video1080.hasCipher()).isFalse();

        Format audio = response.adaptiveFormats().get(2);
        assertThat(audio.itag()).isEqualTo(140);
        assertThat(audio.isAudio()).isTrue();
        assertThat(audio.audioSampleRate()).hasValue(44100);
        assertThat(audio.contentLength()).hasValue(5_000_000L);

        // captionTracks
        assertThat(response.captionTracks()).hasSize(2);
        assertThat(response.captionTracks().get(0).languageCode().value()).isEqualTo("en");
        assertThat(response.captionTracks().get(0).isAsr()).isFalse();
        assertThat(response.captionTracks().get(1).isAsr()).isTrue();

        // thumbnails
        assertThat(response.thumbnails()).hasSize(3);
        assertThat(response.thumbnails().get(2).width()).isEqualTo(1280);
    }

    private static String loadFixture(String name) throws IOException {
        try (InputStream is = PlayerResponseExtractorTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
