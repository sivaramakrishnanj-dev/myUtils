package com.srk.myutils.yd.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extended tests for T-1.4 domain records — covers equals/hashCode/toString,
 * helper methods, fixture-based manual construction, enum completeness,
 * and {@code @JsonIgnoreProperties} annotation presence.
 *
 * <p>Complements the implementer's {@link DomainRecordsTest} characterization tests.
 */
class DomainRecordsExtendedTest {

    // ── Reusable fixtures ──────────────────────────────────────────────

    private static final VideoId RICK_ID = VideoId.of("dQw4w9WgXcQ");
    private static final String RICK_TITLE = "Rick Astley - Never Gonna Give You Up (Official Music Video)";

    private static Format videoFormat(int itag, String codec, int w, int h, long bitrate, String url) {
        return new Format(itag, "video/mp4; codecs=\"" + codec + "\"", bitrate,
                OptionalInt.of(w), OptionalInt.of(h), OptionalInt.of(30),
                OptionalInt.empty(), Optional.of(95_000_000L), url, "");
    }

    private static Format audioFormat(int itag, long bitrate, String url) {
        return new Format(itag, "audio/mp4; codecs=\"mp4a.40.2\"", bitrate,
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.of(44100), Optional.of(5_000_000L), url, "");
    }

    // ── PlayabilityStatus ──────────────────────────────────────────────

    @Nested
    @DisplayName("PlayabilityStatus enum")
    class PlayabilityStatusTests {

        @Test
        @DisplayName("contains exactly 7 values per spec (AC-9.1)")
        void containsExactly7Values() {
            assertThat(PlayabilityStatus.values()).hasSize(7);
        }

        @Test
        @DisplayName("valueOf round-trips for every value")
        void valueOf_roundTrips() {
            for (PlayabilityStatus s : PlayabilityStatus.values()) {
                assertThat(PlayabilityStatus.valueOf(s.name())).isEqualTo(s);
            }
        }

        @Test
        @DisplayName("UNKNOWN is the sentinel for unrecognized statuses")
        void unknown_isSentinel() {
            assertThat(PlayabilityStatus.UNKNOWN.name()).isEqualTo("UNKNOWN");
        }
    }

    // ── ThumbnailUrl ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ThumbnailUrl record")
    class ThumbnailUrlTests {

        @Test
        @DisplayName("equals — same fields are equal")
        void equals_sameFields() {
            var a = new ThumbnailUrl("https://example.com/thumb.jpg", 320, 180);
            var b = new ThumbnailUrl("https://example.com/thumb.jpg", 320, 180);
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("equals — different url not equal")
        void equals_differentUrl() {
            var a = new ThumbnailUrl("https://example.com/a.jpg", 320, 180);
            var b = new ThumbnailUrl("https://example.com/b.jpg", 320, 180);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("hashCode — equal objects have equal hashCodes")
        void hashCode_consistent() {
            var a = new ThumbnailUrl("https://example.com/thumb.jpg", 480, 360);
            var b = new ThumbnailUrl("https://example.com/thumb.jpg", 480, 360);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("toString contains url and dimensions")
        void toString_containsFields() {
            var thumb = new ThumbnailUrl("https://example.com/thumb.jpg", 1280, 720);
            assertThat(thumb.toString()).contains("1280", "720", "https://example.com/thumb.jpg");
        }

        @Test
        @DisplayName("@JsonIgnoreProperties(ignoreUnknown=true) present")
        void jsonIgnoreProperties_present() {
            var ann = ThumbnailUrl.class.getAnnotation(JsonIgnoreProperties.class);
            assertThat(ann).isNotNull();
            assertThat(ann.ignoreUnknown()).isTrue();
        }
    }

    // ── CaptionTrack ───────────────────────────────────────────────────

    @Nested
    @DisplayName("CaptionTrack record")
    class CaptionTrackTests {

        @Test
        @DisplayName("isAsr — empty kind returns false")
        void isAsr_emptyKind_false() {
            var track = new CaptionTrack("https://example.com/tt", LanguageCode.of("en"), "");
            assertThat(track.isAsr()).isFalse();
        }

        @Test
        @DisplayName("isAsr — kind='asr' returns true")
        void isAsr_asrKind_true() {
            var track = new CaptionTrack("https://example.com/tt", LanguageCode.of("en"), "asr");
            assertThat(track.isAsr()).isTrue();
        }

        @Test
        @DisplayName("isAsr — kind='manual' returns false (non-asr string)")
        void isAsr_otherKind_false() {
            var track = new CaptionTrack("https://example.com/tt", LanguageCode.of("fr"), "manual");
            assertThat(track.isAsr()).isFalse();
        }

        @Test
        @DisplayName("equals — same fields are equal")
        void equals_sameFields() {
            var a = new CaptionTrack("https://example.com/tt", LanguageCode.of("en"), "asr");
            var b = new CaptionTrack("https://example.com/tt", LanguageCode.of("en"), "asr");
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("equals — different languageCode not equal")
        void equals_differentLang() {
            var a = new CaptionTrack("https://example.com/tt", LanguageCode.of("en"), "");
            var b = new CaptionTrack("https://example.com/tt", LanguageCode.of("fr"), "");
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("toString contains baseUrl and languageCode")
        void toString_containsFields() {
            var track = new CaptionTrack("https://example.com/tt?lang=en", LanguageCode.of("en"), "");
            assertThat(track.toString()).contains("https://example.com/tt?lang=en", "en");
        }

        @Test
        @DisplayName("@JsonIgnoreProperties(ignoreUnknown=true) present")
        void jsonIgnoreProperties_present() {
            var ann = CaptionTrack.class.getAnnotation(JsonIgnoreProperties.class);
            assertThat(ann).isNotNull();
            assertThat(ann.ignoreUnknown()).isTrue();
        }
    }

    // ── Format ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Format record")
    class FormatTests {

        @Test
        @DisplayName("isVideo — video/mp4 mimeType returns true")
        void isVideo_videoMime_true() {
            var fmt = videoFormat(137, "avc1.640028", 1920, 1080, 4_500_000L, "https://cdn/v");
            assertThat(fmt.isVideo()).isTrue();
        }

        @Test
        @DisplayName("isVideo — audio/mp4 mimeType returns false")
        void isVideo_audioMime_false() {
            var fmt = audioFormat(140, 130_000L, "https://cdn/a");
            assertThat(fmt.isVideo()).isFalse();
        }

        @Test
        @DisplayName("isAudio — audio/mp4 mimeType returns true")
        void isAudio_audioMime_true() {
            var fmt = audioFormat(140, 130_000L, "https://cdn/a");
            assertThat(fmt.isAudio()).isTrue();
        }

        @Test
        @DisplayName("isAudio — video/mp4 mimeType returns false")
        void isAudio_videoMime_false() {
            var fmt = videoFormat(137, "avc1.640028", 1920, 1080, 4_500_000L, "https://cdn/v");
            assertThat(fmt.isAudio()).isFalse();
        }

        @Test
        @DisplayName("hasCipher — empty signatureCipher returns false")
        void hasCipher_empty_false() {
            var fmt = videoFormat(137, "avc1.640028", 1920, 1080, 4_500_000L, "https://cdn/v");
            assertThat(fmt.hasCipher()).isFalse();
        }

        @Test
        @DisplayName("hasCipher — non-empty signatureCipher returns true")
        void hasCipher_nonEmpty_true() {
            var fmt = new Format(137, "video/mp4; codecs=\"avc1.640028\"", 4_500_000L,
                    OptionalInt.of(1920), OptionalInt.of(1080), OptionalInt.of(30),
                    OptionalInt.empty(), Optional.empty(), "", "s=cipher_data");
            assertThat(fmt.hasCipher()).isTrue();
        }

        @Test
        @DisplayName("isVideo XOR isAudio — video/webm is video not audio")
        void isVideo_webm() {
            var fmt = new Format(248, "video/webm; codecs=\"vp9\"", 3_000_000L,
                    OptionalInt.of(1920), OptionalInt.of(1080), OptionalInt.of(30),
                    OptionalInt.empty(), Optional.of(80_000_000L), "https://cdn/v", "");
            assertThat(fmt.isVideo()).isTrue();
            assertThat(fmt.isAudio()).isFalse();
        }

        @Test
        @DisplayName("isAudio — audio/webm is audio not video")
        void isAudio_webm() {
            var fmt = new Format(251, "audio/webm; codecs=\"opus\"", 160_000L,
                    OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                    OptionalInt.of(48000), Optional.of(6_000_000L), "https://cdn/a", "");
            assertThat(fmt.isAudio()).isTrue();
            assertThat(fmt.isVideo()).isFalse();
        }

        @Test
        @DisplayName("equals — same fields are equal")
        void equals_sameFields() {
            var a = audioFormat(140, 130_000L, "https://cdn/a");
            var b = audioFormat(140, 130_000L, "https://cdn/a");
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("equals — different itag not equal")
        void equals_differentItag() {
            var a = audioFormat(140, 130_000L, "https://cdn/a");
            var b = audioFormat(141, 130_000L, "https://cdn/a");
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("hashCode — equal objects have equal hashCodes")
        void hashCode_consistent() {
            var a = audioFormat(140, 130_000L, "https://cdn/a");
            var b = audioFormat(140, 130_000L, "https://cdn/a");
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("toString contains itag and mimeType")
        void toString_containsFields() {
            var fmt = audioFormat(140, 130_000L, "https://cdn/a");
            assertThat(fmt.toString()).contains("140", "audio/mp4");
        }

        @Test
        @DisplayName("optional fields — contentLength empty, width/height/fps empty for audio")
        void optionalFields_audioFormat() {
            var fmt = audioFormat(140, 130_000L, "https://cdn/a");
            assertThat(fmt.width()).isEmpty();
            assertThat(fmt.height()).isEmpty();
            assertThat(fmt.fps()).isEmpty();
            assertThat(fmt.audioSampleRate()).isPresent();
        }

        @Test
        @DisplayName("optional fields — audioSampleRate empty for video")
        void optionalFields_videoFormat() {
            var fmt = videoFormat(137, "avc1.640028", 1920, 1080, 4_500_000L, "https://cdn/v");
            assertThat(fmt.audioSampleRate()).isEmpty();
            assertThat(fmt.width()).isPresent();
            assertThat(fmt.height()).isPresent();
        }

        @Test
        @DisplayName("@JsonIgnoreProperties(ignoreUnknown=true) present")
        void jsonIgnoreProperties_present() {
            var ann = Format.class.getAnnotation(JsonIgnoreProperties.class);
            assertThat(ann).isNotNull();
            assertThat(ann.ignoreUnknown()).isTrue();
        }
    }

    // ── VideoDetails ───────────────────────────────────────────────────

    @Nested
    @DisplayName("VideoDetails record")
    class VideoDetailsTests {

        @Test
        @DisplayName("equals — same fields are equal")
        void equals_sameFields() {
            var a = new VideoDetails(RICK_ID, RICK_TITLE, false, false, Optional.of(LanguageCode.of("en")));
            var b = new VideoDetails(RICK_ID, RICK_TITLE, false, false, Optional.of(LanguageCode.of("en")));
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("equals — different title not equal")
        void equals_differentTitle() {
            var a = new VideoDetails(RICK_ID, "Title A", false, false, Optional.empty());
            var b = new VideoDetails(RICK_ID, "Title B", false, false, Optional.empty());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("hashCode — equal objects have equal hashCodes")
        void hashCode_consistent() {
            var a = new VideoDetails(RICK_ID, RICK_TITLE, false, false, Optional.empty());
            var b = new VideoDetails(RICK_ID, RICK_TITLE, false, false, Optional.empty());
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("toString contains videoId and title")
        void toString_containsFields() {
            var d = new VideoDetails(RICK_ID, RICK_TITLE, false, false, Optional.empty());
            assertThat(d.toString()).contains("dQw4w9WgXcQ", RICK_TITLE);
        }

        @Test
        @DisplayName("isLive and isPrivate flags read back correctly")
        void flags_readBack() {
            var d = new VideoDetails(RICK_ID, "Live", true, true, Optional.empty());
            assertThat(d.isLive()).isTrue();
            assertThat(d.isPrivate()).isTrue();
        }

        @Test
        @DisplayName("@JsonIgnoreProperties(ignoreUnknown=true) present")
        void jsonIgnoreProperties_present() {
            var ann = VideoDetails.class.getAnnotation(JsonIgnoreProperties.class);
            assertThat(ann).isNotNull();
            assertThat(ann.ignoreUnknown()).isTrue();
        }
    }

    // ── PlayerResponse ─────────────────────────────────────────────────

    @Nested
    @DisplayName("PlayerResponse record")
    class PlayerResponseTests {

        @Test
        @DisplayName("equals — same sub-records are equal")
        void equals_sameSubRecords() {
            var vd = new VideoDetails(RICK_ID, RICK_TITLE, false, false, Optional.of(LanguageCode.of("en")));
            var a = new PlayerResponse(vd, PlayabilityStatus.OK, List.of(), List.of(), List.of());
            var b = new PlayerResponse(vd, PlayabilityStatus.OK, List.of(), List.of(), List.of());
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("equals — different playabilityStatus not equal")
        void equals_differentStatus() {
            var vd = new VideoDetails(RICK_ID, RICK_TITLE, false, false, Optional.empty());
            var a = new PlayerResponse(vd, PlayabilityStatus.OK, List.of(), List.of(), List.of());
            var b = new PlayerResponse(vd, PlayabilityStatus.UNPLAYABLE, List.of(), List.of(), List.of());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("hashCode — equal objects have equal hashCodes")
        void hashCode_consistent() {
            var vd = new VideoDetails(RICK_ID, RICK_TITLE, false, false, Optional.empty());
            var a = new PlayerResponse(vd, PlayabilityStatus.OK, List.of(), List.of(), List.of());
            var b = new PlayerResponse(vd, PlayabilityStatus.OK, List.of(), List.of(), List.of());
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("toString contains videoDetails and playabilityStatus")
        void toString_containsFields() {
            var vd = new VideoDetails(RICK_ID, RICK_TITLE, false, false, Optional.empty());
            var pr = new PlayerResponse(vd, PlayabilityStatus.OK, List.of(), List.of(), List.of());
            assertThat(pr.toString()).contains("dQw4w9WgXcQ", "OK");
        }

        @Test
        @DisplayName("@JsonIgnoreProperties(ignoreUnknown=true) present")
        void jsonIgnoreProperties_present() {
            var ann = PlayerResponse.class.getAnnotation(JsonIgnoreProperties.class);
            assertThat(ann).isNotNull();
            assertThat(ann.ignoreUnknown()).isTrue();
        }
    }

    // ── Fixture-based manual construction ──────────────────────────────

    @Nested
    @DisplayName("Fixture-based manual construction (no JSON parsing)")
    class FixtureConstructionTests {

        @Test
        @DisplayName("happy fixture — full PlayerResponse with video+audio+captions+thumbnails")
        void happyFixture_fullPlayerResponse() {
            var vd = new VideoDetails(RICK_ID, RICK_TITLE, false, false, Optional.of(LanguageCode.of("en")));
            var video1080 = videoFormat(137, "avc1.640028", 1920, 1080, 4_500_000L,
                    "https://rr3---sn-synthetic.googlevideo.com/videoplayback?itag=137");
            var video720 = videoFormat(136, "avc1.4d401f", 1280, 720, 2_500_000L,
                    "https://rr3---sn-synthetic.googlevideo.com/videoplayback?itag=136");
            var audio = audioFormat(140, 130_000L,
                    "https://rr3---sn-synthetic.googlevideo.com/videoplayback?itag=140");
            var manualCaption = new CaptionTrack(
                    "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&lang=en",
                    LanguageCode.of("en"), "");
            var asrCaption = new CaptionTrack(
                    "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&lang=en&kind=asr",
                    LanguageCode.of("en"), "asr");
            var thumbs = List.of(
                    new ThumbnailUrl("https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg", 320, 180),
                    new ThumbnailUrl("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", 480, 360),
                    new ThumbnailUrl("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", 1280, 720));

            var pr = new PlayerResponse(vd, PlayabilityStatus.OK,
                    List.of(video1080, video720, audio),
                    List.of(manualCaption, asrCaption), thumbs);

            assertThat(pr.videoDetails().videoId()).isEqualTo(RICK_ID);
            assertThat(pr.videoDetails().title()).isEqualTo(RICK_TITLE);
            assertThat(pr.videoDetails().isLive()).isFalse();
            assertThat(pr.videoDetails().audioLanguage()).isPresent();
            assertThat(pr.playabilityStatus()).isEqualTo(PlayabilityStatus.OK);
            assertThat(pr.adaptiveFormats()).hasSize(3);
            assertThat(pr.adaptiveFormats().stream().filter(Format::isVideo).count()).isEqualTo(2);
            assertThat(pr.adaptiveFormats().stream().filter(Format::isAudio).count()).isEqualTo(1);
            assertThat(pr.captionTracks()).hasSize(2);
            assertThat(pr.captionTracks().stream().filter(CaptionTrack::isAsr).count()).isEqualTo(1);
            assertThat(pr.thumbnails()).hasSize(3);
        }

        @Test
        @DisplayName("no-captions fixture — OK status, empty captionTracks, no audioLanguage")
        void noCaptionsFixture_emptyCaptionTracks() {
            var vd = new VideoDetails(VideoId.of("nocapnocapz"), "Synthesized no-captions fixture",
                    false, false, Optional.empty());
            var video = videoFormat(137, "avc1.640028", 1920, 1080, 4_500_000L, "https://cdn/v");
            var audio = audioFormat(140, 130_000L, "https://cdn/a");

            var pr = new PlayerResponse(vd, PlayabilityStatus.OK,
                    List.of(video, audio), List.of(), List.of(
                    new ThumbnailUrl("https://i.ytimg.com/vi/nocapnocapz/mqdefault.jpg", 320, 180)));

            assertThat(pr.playabilityStatus()).isEqualTo(PlayabilityStatus.OK);
            assertThat(pr.captionTracks()).isEmpty();
            assertThat(pr.videoDetails().audioLanguage()).isEmpty();
            assertThat(pr.adaptiveFormats()).hasSize(2);
        }

        @Test
        @DisplayName("unplayable fixture — UNPLAYABLE status, private video, no formats")
        void unplayableFixture_unplayableStatus() {
            var vd = new VideoDetails(VideoId.of("privprivpr1"), "Synthesized unplayable fixture",
                    false, true, Optional.empty());

            var pr = new PlayerResponse(vd, PlayabilityStatus.UNPLAYABLE,
                    List.of(), List.of(), List.of(
                    new ThumbnailUrl("https://i.ytimg.com/vi/privprivpr1/mqdefault.jpg", 320, 180)));

            assertThat(pr.playabilityStatus()).isEqualTo(PlayabilityStatus.UNPLAYABLE);
            assertThat(pr.videoDetails().isPrivate()).isTrue();
            assertThat(pr.adaptiveFormats()).isEmpty();
            assertThat(pr.captionTracks()).isEmpty();
        }

        @Test
        @DisplayName("live fixture — OK status, isLive=true, no formats")
        void liveFixture_isLiveTrue() {
            var vd = new VideoDetails(VideoId.of("livelivelv1"), "Synthesized live stream fixture",
                    true, false, Optional.empty());

            var pr = new PlayerResponse(vd, PlayabilityStatus.OK,
                    List.of(), List.of(), List.of(
                    new ThumbnailUrl("https://i.ytimg.com/vi/livelivelv1/mqdefault.jpg", 320, 180)));

            assertThat(pr.playabilityStatus()).isEqualTo(PlayabilityStatus.OK);
            assertThat(pr.videoDetails().isLive()).isTrue();
            assertThat(pr.adaptiveFormats()).isEmpty();
        }
    }
}
