package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for {@link FormatSelector} — AC-1.3, AC-1.4, AC-1.5, AC-2.2.
 *
 * <p>Contract tests satisfied: CT-APP-3, CT-APP-4.
 * CT-APP-5 (cipher → CipherRequiredException) is deferred to T-2.2.
 */
class FormatSelectorBehaviorTest {

    private final FormatSelector selector = new FormatSelector();

    // ── helpers ──────────────────────────────────────────────────────

    private static Format video(int itag, String codec, int height, long bitrate) {
        return new Format(itag,
                "video/mp4; codecs=\"" + codec + "\"",
                bitrate,
                OptionalInt.of((int) (height * 16.0 / 9)),
                OptionalInt.of(height),
                OptionalInt.of(30),
                OptionalInt.empty(),
                Optional.of(10_000_000L),
                "https://cdn.example.com/v" + itag,
                "");
    }

    private static Format videoWebm(int itag, String codec, int height, long bitrate) {
        return new Format(itag,
                "video/webm; codecs=\"" + codec + "\"",
                bitrate,
                OptionalInt.of((int) (height * 16.0 / 9)),
                OptionalInt.of(height),
                OptionalInt.of(30),
                OptionalInt.empty(),
                Optional.of(10_000_000L),
                "https://cdn.example.com/v" + itag,
                "");
    }

    private static Format audioM4a(int itag, long bitrate) {
        return new Format(itag,
                "audio/mp4; codecs=\"mp4a.40.2\"",
                bitrate,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.of(44100),
                Optional.of(5_000_000L),
                "https://cdn.example.com/a" + itag,
                "");
    }

    private static Format audioWebm(int itag, long bitrate) {
        return new Format(itag,
                "audio/webm; codecs=\"opus\"",
                bitrate,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.of(48000),
                Optional.of(4_000_000L),
                "https://cdn.example.com/a" + itag,
                "");
    }

    private static Format cipherVideo(int itag, String codec, int height, long bitrate) {
        return new Format(itag,
                "video/mp4; codecs=\"" + codec + "\"",
                bitrate,
                OptionalInt.of((int) (height * 16.0 / 9)),
                OptionalInt.of(height),
                OptionalInt.of(30),
                OptionalInt.empty(),
                Optional.of(10_000_000L),
                "",
                "s=ENCRYPTED_SIG&sp=sig&url=https://cdn.example.com/v" + itag);
    }

    private static Format cipherAudio(int itag, long bitrate) {
        return new Format(itag,
                "audio/mp4; codecs=\"mp4a.40.2\"",
                bitrate,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.of(44100),
                Optional.of(5_000_000L),
                "",
                "s=ENCRYPTED_SIG&sp=sig&url=https://cdn.example.com/a" + itag);
    }

    // ── AC-1.3: --max-height filter ─────────────────────────────────

    @Nested
    @DisplayName("AC-1.3: --max-height filter")
    class MaxHeightFilter {

        @Test
        @DisplayName("maxHeight=0 (uncapped) selects highest-res video")
        void select_givenMaxHeightZero_selectsHighestResolution() {
            Format v1080 = video(137, "avc1.640028", 1080, 4_500_000);
            Format v720 = video(136, "avc1.4d401f", 720, 2_500_000);
            Format v4k = video(313, "vp9", 2160, 15_000_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(v1080, v720, v4k, audio), 0);

            assertThat(result.video().itag()).isEqualTo(313);
        }

        @Test
        @DisplayName("maxHeight=720 rejects 1080p, selects ≤720p")
        void select_givenMaxHeight720_rejects1080p() {
            Format v1080 = video(137, "avc1.640028", 1080, 4_500_000);
            Format v720 = video(136, "avc1.4d401f", 720, 2_500_000);
            Format v480 = video(135, "avc1.4d4015", 480, 1_000_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(v1080, v720, v480, audio), 720);

            assertThat(result.video().itag()).isEqualTo(136);
        }

        @Test
        @DisplayName("maxHeight=480 with only 1080p available throws NoMatchingFormatException")
        void select_givenMaxHeight480WithOnly1080p_throws() {
            Format v1080 = video(137, "avc1.640028", 1080, 4_500_000);
            Format audio = audioM4a(140, 130_000);

            assertThatThrownBy(() -> selector.select(List.of(v1080, audio), 480))
                    .isInstanceOf(NoMatchingFormatException.class);
        }

        @Test
        @DisplayName("maxHeight=1080 with 1080p, 720p, 480p picks 1080p")
        void select_givenMaxHeight1080_picks1080p() {
            Format v1080 = video(137, "avc1.640028", 1080, 4_500_000);
            Format v720 = video(136, "avc1.4d401f", 720, 2_500_000);
            Format v480 = video(135, "avc1.4d4015", 480, 1_000_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(v1080, v720, v480, audio), 1080);

            assertThat(result.video().itag()).isEqualTo(137);
        }
    }

    // ── AC-1.4: Codec preference ────────────────────────────────────

    @Nested
    @DisplayName("AC-1.4: Codec preference H.264 > VP9 > AV1")
    class CodecPreference {

        @Test
        @DisplayName("At equal 1080p: avc1 preferred over vp9")
        void select_givenEqualRes_prefersAvc1OverVp9() {
            Format vp9 = videoWebm(248, "vp9", 1080, 4_500_000);
            Format avc1 = video(137, "avc1.640028", 1080, 4_500_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(vp9, avc1, audio), 1080);

            assertThat(result.video().itag()).isEqualTo(137);
        }

        @Test
        @DisplayName("At equal 1080p: vp9 preferred over av01")
        void select_givenEqualRes_prefersVp9OverAv01() {
            Format av01 = video(399, "av01.0.08M.08", 1080, 4_500_000);
            Format vp9 = videoWebm(248, "vp9", 1080, 4_500_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(av01, vp9, audio), 1080);

            assertThat(result.video().itag()).isEqualTo(248);
        }

        @Test
        @DisplayName("At equal 1080p: avc1 preferred over av01")
        void select_givenEqualRes_prefersAvc1OverAv01() {
            Format av01 = video(399, "av01.0.08M.08", 1080, 4_500_000);
            Format avc1 = video(137, "avc1.640028", 1080, 4_500_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(av01, avc1, audio), 1080);

            assertThat(result.video().itag()).isEqualTo(137);
        }

        @Test
        @DisplayName("Higher resolution wins even with worse codec (resolution-first per AC-1.4a)")
        void select_givenHigherResWithWorseCodec_prefersHigherRes() {
            // 1080p VP9 should beat 720p H.264 because resolution is tiebreak (a)
            Format vp9_1080 = videoWebm(248, "vp9", 1080, 4_500_000);
            Format avc1_720 = video(136, "avc1.4d401f", 720, 2_500_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(vp9_1080, avc1_720, audio), 1080);

            assertThat(result.video().itag()).isEqualTo(248);
        }
    }

    // ── AC-1.5: Bitrate tiebreaker ──────────────────────────────────

    @Nested
    @DisplayName("AC-1.5: Bitrate tiebreaker")
    class BitrateTiebreaker {

        @Test
        @DisplayName("Same codec + resolution: higher bitrate wins")
        void select_givenSameCodecAndRes_prefersHigherBitrate() {
            Format lowBr = video(137, "avc1.640028", 1080, 3_000_000);
            Format highBr = video(299, "avc1.640028", 1080, 5_000_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(lowBr, highBr, audio), 1080);

            assertThat(result.video().itag()).isEqualTo(299);
        }
    }

    // ── AC-2.2: Audio selection ─────────────────────────────────────

    @Nested
    @DisplayName("AC-2.2: Audio selection — m4a > webm, then bitrate")
    class AudioSelection {

        @Test
        @DisplayName("m4a preferred over webm even at lower bitrate")
        void select_givenM4aAndWebm_prefersM4a() {
            Format video = video(137, "avc1.640028", 1080, 4_500_000);
            Format m4a = audioM4a(140, 130_000);
            Format webm = audioWebm(251, 160_000);

            FormatSelection result = selector.select(List.of(video, m4a, webm), 1080);

            assertThat(result.audio().itag()).isEqualTo(140);
        }

        @Test
        @DisplayName("Within same container (m4a), higher bitrate wins")
        void select_givenTwoM4a_prefersHigherBitrate() {
            Format video = video(137, "avc1.640028", 1080, 4_500_000);
            Format m4aLow = audioM4a(139, 48_000);
            Format m4aHigh = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(video, m4aLow, m4aHigh), 1080);

            assertThat(result.audio().itag()).isEqualTo(140);
        }

        @Test
        @DisplayName("Only webm audio available: webm picked (no failure)")
        void select_givenOnlyWebmAudio_picksWebm() {
            Format video = video(137, "avc1.640028", 1080, 4_500_000);
            Format webm = audioWebm(251, 160_000);

            FormatSelection result = selector.select(List.of(video, webm), 1080);

            assertThat(result.audio().itag()).isEqualTo(251);
        }

        @Test
        @DisplayName("No audio at all throws NoMatchingFormatException")
        void select_givenNoAudio_throws() {
            Format video = video(137, "avc1.640028", 1080, 4_500_000);

            assertThatThrownBy(() -> selector.select(List.of(video), 1080))
                    .isInstanceOf(NoMatchingFormatException.class);
        }
    }

    // ── Cipher-protected formats (T-2.1 scope: silent filter) ───────

    @Nested
    @DisplayName("Cipher-protected formats filtered silently")
    class CipherFiltering {

        @Test
        @DisplayName("Cipher video filtered; non-cipher video selected")
        void select_givenMixedCipherAndClear_selectsClearVideo() {
            Format cipherV = cipherVideo(137, "avc1.640028", 1080, 4_500_000);
            Format clearV = video(136, "avc1.4d401f", 720, 2_500_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(cipherV, clearV, audio), 1080);

            assertThat(result.video().itag()).isEqualTo(136);
        }

        @Test
        @DisplayName("All cipher video + good audio throws CipherRequiredException (T-2.2, AC-5.3)")
        void select_givenAllCipherVideo_throwsCipherRequired() {
            Format cipherV1 = cipherVideo(137, "avc1.640028", 1080, 4_500_000);
            Format cipherV2 = cipherVideo(136, "avc1.4d401f", 720, 2_500_000);
            Format audio = audioM4a(140, 130_000);

            assertThatThrownBy(() -> selector.select(List.of(cipherV1, cipherV2, audio), 1080))
                    .isInstanceOf(CipherRequiredException.class)
                    .hasMessageContaining("JavaScript signature deciphering");
        }

        @Test
        @DisplayName("Cipher audio filtered; non-cipher audio selected")
        void select_givenMixedCipherAudio_selectsClearAudio() {
            Format video = video(137, "avc1.640028", 1080, 4_500_000);
            Format cipherA = cipherAudio(140, 130_000);
            Format clearA = audioWebm(251, 160_000);

            FormatSelection result = selector.select(List.of(video, cipherA, clearA), 1080);

            assertThat(result.audio().itag()).isEqualTo(251);
        }
    }

    // ── Edge cases ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Empty formats list throws NoMatchingFormatException")
        void select_givenEmptyList_throws() {
            assertThatThrownBy(() -> selector.select(List.of(), 1080))
                    .isInstanceOf(NoMatchingFormatException.class);
        }

        @Test
        @DisplayName("Only audio formats (no video) throws NoMatchingFormatException")
        void select_givenOnlyAudio_throws() {
            Format audio = audioM4a(140, 130_000);

            assertThatThrownBy(() -> selector.select(List.of(audio), 1080))
                    .isInstanceOf(NoMatchingFormatException.class);
        }

        @Test
        @DisplayName("Only video formats (no audio) throws NoMatchingFormatException")
        void select_givenOnlyVideo_throws() {
            Format video = video(137, "avc1.640028", 1080, 4_500_000);

            assertThatThrownBy(() -> selector.select(List.of(video), 1080))
                    .isInstanceOf(NoMatchingFormatException.class);
        }
    }

    // ── CT-APP-3 / CT-APP-4: Fixture integration ────────────────────

    @Nested
    @DisplayName("Contract tests: CT-APP-3, CT-APP-4")
    class ContractTests {

        @Test
        @DisplayName("CT-APP-3: FormatSelector picks video itag 137 under maxHeight=1080 from happy fixture")
        void ctApp3_selectVideo_fromHappyFixture() throws Exception {
            PlayerResponse response = loadHappyFixture();

            FormatSelection result = selector.select(response.adaptiveFormats(), 1080);

            assertThat(result.video().itag()).isEqualTo(137);
        }

        @Test
        @DisplayName("CT-APP-4: FormatSelector picks audio itag 140 from happy fixture")
        void ctApp4_selectAudio_fromHappyFixture() throws Exception {
            PlayerResponse response = loadHappyFixture();

            FormatSelection result = selector.select(response.adaptiveFormats(), 1080);

            assertThat(result.audio().itag()).isEqualTo(140);
        }

        private PlayerResponse loadHappyFixture() throws Exception {
            try (InputStream is = getClass().getResourceAsStream("/fixtures/innertube-response-happy.json")) {
                assertThat(is).as("fixture innertube-response-happy.json must exist").isNotNull();
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return PlayerResponseExtractor.extract(json);
            }
        }
    }

    // ── Package-private helper coverage ─────────────────────────────

    @Nested
    @DisplayName("codecRank / containerRank helpers")
    class HelperRanks {

        @Test
        @DisplayName("codecRank: avc1 > vp9 > av01 > unknown")
        void codecRank_ordering() {
            assertThat(FormatSelector.codecRank("video/mp4; codecs=\"avc1.640028\"")).isEqualTo(2);
            assertThat(FormatSelector.codecRank("video/webm; codecs=\"vp9\"")).isEqualTo(1);
            assertThat(FormatSelector.codecRank("video/webm; codecs=\"vp09.00.31.08\"")).isEqualTo(1);
            assertThat(FormatSelector.codecRank("video/mp4; codecs=\"av01.0.08M.08\"")).isEqualTo(0);
            assertThat(FormatSelector.codecRank("video/3gpp; codecs=\"mp4v.20.3\"")).isEqualTo(-1);
        }

        @Test
        @DisplayName("containerRank: audio/mp4 > audio/webm > unknown")
        void containerRank_ordering() {
            assertThat(FormatSelector.containerRank("audio/mp4; codecs=\"mp4a.40.2\"")).isEqualTo(1);
            assertThat(FormatSelector.containerRank("audio/webm; codecs=\"opus\"")).isEqualTo(0);
            assertThat(FormatSelector.containerRank("audio/ogg; codecs=\"vorbis\"")).isEqualTo(-1);
        }
    }

    // ── Portrait video (YouTube Shorts) — qualityHeight fix ────────

    @Nested
    @DisplayName("Portrait video (YouTube Shorts): qualityHeight uses min(w,h)")
    class PortraitVideo {

        private static Format portraitVideo(int itag, String codec, int width, int height, long bitrate) {
            String container = codec.startsWith("vp") ? "video/webm" : "video/mp4";
            return new Format(itag,
                    container + "; codecs=\"" + codec + "\"",
                    bitrate,
                    OptionalInt.of(width),
                    OptionalInt.of(height),
                    OptionalInt.of(30),
                    OptionalInt.empty(),
                    Optional.of(10_000_000L),
                    "https://cdn.example.com/v" + itag,
                    "");
        }

        @Test
        @DisplayName("maxHeight=1080 with portrait 1080x1920 H.264 picks H.264 over VP9 608x1080")
        void select_givenPortrait1080p_prefersH264OverVp9() {
            Format h264Portrait = portraitVideo(137, "avc1.640028", 1080, 1920, 4_500_000);
            Format vp9Portrait = portraitVideo(779, "vp9", 608, 1080, 2_000_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(h264Portrait, vp9Portrait, audio), 1080);

            assertThat(result.video().itag()).isEqualTo(137);
        }

        @Test
        @DisplayName("maxHeight=720 rejects portrait formats with qualityHeight > 720")
        void select_givenMaxHeight720_rejectsPortrait1080p() {
            Format h264Portrait = portraitVideo(137, "avc1.640028", 1080, 1920, 4_500_000);
            Format vp9Portrait = portraitVideo(779, "vp9", 1080, 1920, 2_000_000);
            Format audio = audioM4a(140, 130_000);

            assertThatThrownBy(() -> selector.select(List.of(h264Portrait, vp9Portrait, audio), 720))
                    .isInstanceOf(NoMatchingFormatException.class);
        }

        @Test
        @DisplayName("Portrait with only VP9 at 1080p: selector picks VP9 when no H.264 available")
        void select_givenPortraitOnlyVp9_picksVp9() {
            Format vp9Portrait = portraitVideo(779, "vp9", 1080, 1920, 4_000_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(vp9Portrait, audio), 1080);

            assertThat(result.video().itag()).isEqualTo(779);
        }

        @Test
        @DisplayName("Landscape 1920x1080 H.264: existing behavior preserved, H.264 picked")
        void select_givenLandscape1080p_picksH264() {
            Format h264Landscape = portraitVideo(137, "avc1.640028", 1920, 1080, 4_500_000);
            Format vp9Landscape = portraitVideo(248, "vp9", 1920, 1080, 4_500_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(h264Landscape, vp9Landscape, audio), 1080);

            assertThat(result.video().itag()).isEqualTo(137);
        }

        @Test
        @DisplayName("Square 720x720: treated as 720p, passes maxHeight=720")
        void select_givenSquare720_passesMaxHeight720() {
            Format square = portraitVideo(136, "avc1.4d401f", 720, 720, 2_500_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(square, audio), 720);

            assertThat(result.video().itag()).isEqualTo(136);
        }

        @Test
        @DisplayName("Portrait at maxHeight=480: selects 480p-tier format (qualityHeight=480)")
        void select_givenPortraitMaxHeight480_picks480pTier() {
            Format portrait1080 = portraitVideo(137, "avc1.640028", 1080, 1920, 4_500_000);
            Format portrait480 = portraitVideo(135, "avc1.4d4015", 480, 854, 1_000_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(portrait1080, portrait480, audio), 480);

            assertThat(result.video().itag()).isEqualTo(135);
        }

        @Test
        @DisplayName("Portrait at maxHeight=0 (uncapped): picks highest qualityHeight H.264")
        void select_givenPortraitMaxHeightZero_picksHighestQualityH264() {
            Format portrait1080 = portraitVideo(137, "avc1.640028", 1080, 1920, 4_500_000);
            Format portrait720 = portraitVideo(136, "avc1.4d401f", 720, 1280, 2_500_000);
            Format audio = audioM4a(140, 130_000);

            FormatSelection result = selector.select(List.of(portrait1080, portrait720, audio), 0);

            assertThat(result.video().itag()).isEqualTo(137);
        }
    }

    // ── qualityHeight helper ────────────────────────────────────────

    @Nested
    @DisplayName("qualityHeight: returns min(width, height)")
    class QualityHeight {

        @Test
        @DisplayName("Landscape 1920x1080 → qualityHeight = 1080")
        void qualityHeight_landscape_returnsHeight() {
            Format f = new Format(137, "video/mp4; codecs=\"avc1.640028\"", 4_500_000,
                    OptionalInt.of(1920), OptionalInt.of(1080), OptionalInt.of(30),
                    OptionalInt.empty(), Optional.of(10_000_000L),
                    "https://cdn.example.com/v137", "");

            assertThat(FormatSelector.qualityHeight(f)).isEqualTo(1080);
        }

        @Test
        @DisplayName("Portrait 1080x1920 → qualityHeight = 1080")
        void qualityHeight_portrait_returnsWidth() {
            Format f = new Format(137, "video/mp4; codecs=\"avc1.640028\"", 4_500_000,
                    OptionalInt.of(1080), OptionalInt.of(1920), OptionalInt.of(30),
                    OptionalInt.empty(), Optional.of(10_000_000L),
                    "https://cdn.example.com/v137", "");

            assertThat(FormatSelector.qualityHeight(f)).isEqualTo(1080);
        }

        @Test
        @DisplayName("Square 720x720 → qualityHeight = 720")
        void qualityHeight_square_returnsDimension() {
            Format f = new Format(136, "video/mp4; codecs=\"avc1.4d401f\"", 2_500_000,
                    OptionalInt.of(720), OptionalInt.of(720), OptionalInt.of(30),
                    OptionalInt.empty(), Optional.of(5_000_000L),
                    "https://cdn.example.com/v136", "");

            assertThat(FormatSelector.qualityHeight(f)).isEqualTo(720);
        }

        @Test
        @DisplayName("Absent width, height=1080 → qualityHeight = min(MAX_VALUE, 1080) = 1080")
        void qualityHeight_absentWidth_returnsHeight() {
            Format f = new Format(137, "video/mp4; codecs=\"avc1.640028\"", 4_500_000,
                    OptionalInt.empty(), OptionalInt.of(1080), OptionalInt.of(30),
                    OptionalInt.empty(), Optional.of(10_000_000L),
                    "https://cdn.example.com/v137", "");

            assertThat(FormatSelector.qualityHeight(f)).isEqualTo(1080);
        }

        @Test
        @DisplayName("Absent height, width=1920 → qualityHeight = min(1920, MAX_VALUE) = 1920")
        void qualityHeight_absentHeight_returnsWidth() {
            Format f = new Format(137, "video/mp4; codecs=\"avc1.640028\"", 4_500_000,
                    OptionalInt.of(1920), OptionalInt.empty(), OptionalInt.of(30),
                    OptionalInt.empty(), Optional.of(10_000_000L),
                    "https://cdn.example.com/v137", "");

            assertThat(FormatSelector.qualityHeight(f)).isEqualTo(1920);
        }

        @Test
        @DisplayName("Both absent → qualityHeight = MAX_VALUE (excluded by any capped filter)")
        void qualityHeight_bothAbsent_returnsMaxValue() {
            Format f = new Format(137, "video/mp4; codecs=\"avc1.640028\"", 4_500_000,
                    OptionalInt.empty(), OptionalInt.empty(), OptionalInt.of(30),
                    OptionalInt.empty(), Optional.of(10_000_000L),
                    "https://cdn.example.com/v137", "");

            assertThat(FormatSelector.qualityHeight(f)).isEqualTo(Integer.MAX_VALUE);
        }
    }
}
