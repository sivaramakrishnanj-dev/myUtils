package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive cipher-check tests for {@link FormatSelector} (T-2.2, AC-5.3).
 *
 * <p>Covers all cipher/clear combinations for video and audio candidates,
 * the fixture-based end-to-end path, error message content, and exit code.
 *
 * <p>Contract test satisfied: CT-APP-5.
 */
class FormatSelectorCipherBehaviorTest {

    private final FormatSelector selector = new FormatSelector();

    // ── helpers ──────────────────────────────────────────────────────

    private static Format clearVideo(int itag, int height, long bitrate) {
        return new Format(itag, "video/mp4; codecs=\"avc1.640028\"", bitrate,
                OptionalInt.of((int) (height * 16.0 / 9)), OptionalInt.of(height),
                OptionalInt.of(30), OptionalInt.empty(), Optional.of(10_000_000L),
                "https://cdn.example.com/v" + itag, "");
    }

    private static Format cipherVideo(int itag, int height, long bitrate) {
        return new Format(itag, "video/mp4; codecs=\"avc1.640028\"", bitrate,
                OptionalInt.of((int) (height * 16.0 / 9)), OptionalInt.of(height),
                OptionalInt.of(30), OptionalInt.empty(), Optional.of(10_000_000L),
                "", "s=SIG&url=https://cdn.example.com/v" + itag);
    }

    private static Format clearAudio(int itag, long bitrate) {
        return new Format(itag, "audio/mp4; codecs=\"mp4a.40.2\"", bitrate,
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.of(44100), Optional.of(5_000_000L),
                "https://cdn.example.com/a" + itag, "");
    }

    private static Format cipherAudio(int itag, long bitrate) {
        return new Format(itag, "audio/mp4; codecs=\"mp4a.40.2\"", bitrate,
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.of(44100), Optional.of(5_000_000L),
                "", "s=SIG&url=https://cdn.example.com/a" + itag);
    }

    // ── Test 1: all video cipher, some audio clear → CipherRequiredException ──

    @Test
    @DisplayName("AC-5.3: all video cipher + some audio clear → CipherRequiredException (video trips first)")
    void select_givenAllVideoCipherSomeAudioClear_throwsCipherRequired() {
        List<Format> formats = List.of(
                cipherVideo(137, 1080, 4_500_000),
                cipherVideo(136, 720, 2_500_000),
                clearAudio(140, 130_000));

        assertThatThrownBy(() -> selector.select(formats, 1080))
                .isInstanceOf(CipherRequiredException.class);
    }

    // ── Test 2: all video cipher, all audio cipher → CipherRequiredException ──

    @Test
    @DisplayName("AC-5.3: all video cipher + all audio cipher → CipherRequiredException")
    void select_givenAllCipherVideoAndAudio_throwsCipherRequired() {
        List<Format> formats = List.of(
                cipherVideo(137, 1080, 4_500_000),
                cipherAudio(140, 130_000));

        assertThatThrownBy(() -> selector.select(formats, 1080))
                .isInstanceOf(CipherRequiredException.class);
    }

    // ── Test 3: some video clear, all audio cipher → CipherRequiredException ──

    @Test
    @DisplayName("AC-5.3: some video clear + all audio cipher → CipherRequiredException (audio trips)")
    void select_givenSomeVideoClearAllAudioCipher_throwsCipherRequired() {
        List<Format> formats = List.of(
                clearVideo(137, 1080, 4_500_000),
                cipherAudio(140, 130_000),
                cipherAudio(141, 256_000));

        assertThatThrownBy(() -> selector.select(formats, 1080))
                .isInstanceOf(CipherRequiredException.class);
    }

    // ── Test 4: mixed cipher + clear in both → normal selection succeeds ──

    @Test
    @DisplayName("AC-5.3: some clear video + some clear audio + cipher entries → normal selection")
    void select_givenMixedCipherAndClear_succeedsNormally() {
        List<Format> formats = List.of(
                cipherVideo(137, 1080, 4_500_000),
                clearVideo(136, 720, 2_500_000),
                cipherAudio(140, 130_000),
                clearAudio(141, 256_000));

        FormatSelection result = selector.select(formats, 1080);

        assertThat(result.video().itag()).isEqualTo(136);
        assertThat(result.audio().itag()).isEqualTo(141);
    }

    // ── Test 5: no formats at all → NoMatchingFormatException (not Cipher) ──

    @Test
    @DisplayName("Empty format list → NoMatchingFormatException, not CipherRequiredException")
    void select_givenNoFormats_throwsNoMatchingFormat() {
        assertThatThrownBy(() -> selector.select(List.of(), 1080))
                .isInstanceOf(NoMatchingFormatException.class)
                .isNotInstanceOf(CipherRequiredException.class);
    }

    // ── Test 6: fixture end-to-end via PlayerResponseExtractor → FormatSelector ──

    @Test
    @DisplayName("CT-APP-5: innertube-response-cipher.json → PlayerResponseExtractor → FormatSelector → CipherRequiredException")
    void select_givenCipherFixture_throwsCipherRequired() throws IOException {
        String json = loadFixture("innertube-response-cipher.json");
        PlayerResponse response = PlayerResponseExtractor.extract(json);

        assertThatThrownBy(() -> selector.select(response.adaptiveFormats(), 1080))
                .isInstanceOf(CipherRequiredException.class);
    }

    // ── Test 7: error message matches AC-5.3 (mentions yt-dlp) ──

    @Test
    @DisplayName("AC-5.3: error message mentions 'yt-dlp' and 'JavaScript signature deciphering'")
    void select_givenAllCipher_messageMatchesAc53() {
        List<Format> formats = List.of(
                cipherVideo(137, 1080, 4_500_000),
                cipherAudio(140, 130_000));

        assertThatThrownBy(() -> selector.select(formats, 1080))
                .isInstanceOf(CipherRequiredException.class)
                .hasMessageContaining("JavaScript signature deciphering")
                .hasMessageContaining("yt-dlp");
    }

    // ── Test 8: exit code 22 ──

    @Test
    @DisplayName("AC-5.2: CipherRequiredException.exitCode() == 22")
    void cipherRequiredException_exitCode_is22() {
        List<Format> formats = List.of(
                cipherVideo(137, 1080, 4_500_000),
                cipherAudio(140, 130_000));

        try {
            selector.select(formats, 1080);
        } catch (CipherRequiredException e) {
            assertThat(e.exitCode()).isEqualTo(22);
            return;
        }
        org.junit.jupiter.api.Assertions.fail("Expected CipherRequiredException");
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static String loadFixture(String name) throws IOException {
        try (InputStream is = FormatSelectorCipherBehaviorTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
