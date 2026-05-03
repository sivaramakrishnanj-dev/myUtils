package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for T-1.4 domain records — happy-path construction
 * and field read-back only. Exhaustive edge-case coverage is the tester's job.
 */
class DomainRecordsTest {

    @Test
    void playabilityStatus_hasAllExpectedValues() {
        assertThat(PlayabilityStatus.values()).containsExactly(
                PlayabilityStatus.OK,
                PlayabilityStatus.UNPLAYABLE,
                PlayabilityStatus.LIVE_STREAM_OFFLINE,
                PlayabilityStatus.LOGIN_REQUIRED,
                PlayabilityStatus.ERROR,
                PlayabilityStatus.AGE_VERIFICATION_REQUIRED,
                PlayabilityStatus.UNKNOWN
        );
    }

    @Test
    void thumbnailUrl_constructsAndReadsBack() {
        var thumb = new ThumbnailUrl("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", 480, 360);
        assertThat(thumb.url()).isEqualTo("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
        assertThat(thumb.width()).isEqualTo(480);
        assertThat(thumb.height()).isEqualTo(360);
    }

    @Test
    void captionTrack_manualTrack_isAsrReturnsFalse() {
        var track = new CaptionTrack(
                "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&lang=en",
                LanguageCode.of("en"),
                ""
        );
        assertThat(track.isAsr()).isFalse();
        assertThat(track.languageCode().value()).isEqualTo("en");
    }

    @Test
    void captionTrack_asrTrack_isAsrReturnsTrue() {
        var track = new CaptionTrack(
                "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&lang=en&kind=asr",
                LanguageCode.of("en"),
                "asr"
        );
        assertThat(track.isAsr()).isTrue();
    }

    @Test
    void format_videoFormat_isVideoTrueIsAudioFalse() {
        var fmt = new Format(
                137,
                "video/mp4; codecs=\"avc1.640028\"",
                4_500_000L,
                OptionalInt.of(1920),
                OptionalInt.of(1080),
                OptionalInt.of(30),
                OptionalInt.empty(),
                Optional.of(95_000_000L),
                "https://rr3---sn-synthetic.googlevideo.com/videoplayback",
                ""
        );
        assertThat(fmt.isVideo()).isTrue();
        assertThat(fmt.isAudio()).isFalse();
        assertThat(fmt.hasCipher()).isFalse();
        assertThat(fmt.itag()).isEqualTo(137);
    }

    @Test
    void format_audioFormat_isAudioTrueIsVideoFalse() {
        var fmt = new Format(
                140,
                "audio/mp4; codecs=\"mp4a.40.2\"",
                130_000L,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.of(44100),
                Optional.of(5_000_000L),
                "https://rr3---sn-synthetic.googlevideo.com/videoplayback",
                ""
        );
        assertThat(fmt.isAudio()).isTrue();
        assertThat(fmt.isVideo()).isFalse();
    }

    @Test
    void format_withSignatureCipher_hasCipherReturnsTrue() {
        var fmt = new Format(
                137, "video/mp4; codecs=\"avc1.640028\"", 4_500_000L,
                OptionalInt.of(1920), OptionalInt.of(1080), OptionalInt.of(30),
                OptionalInt.empty(), Optional.empty(),
                "",
                "s=some_cipher_data"
        );
        assertThat(fmt.hasCipher()).isTrue();
        assertThat(fmt.url()).isEmpty();
    }

    @Test
    void videoDetails_constructsWithOptionalAudioLanguage() {
        var details = new VideoDetails(
                VideoId.of("dQw4w9WgXcQ"),
                "Rick Astley - Never Gonna Give You Up",
                false,
                false,
                Optional.of(LanguageCode.of("en"))
        );
        assertThat(details.videoId().value()).isEqualTo("dQw4w9WgXcQ");
        assertThat(details.title()).isEqualTo("Rick Astley - Never Gonna Give You Up");
        assertThat(details.isLive()).isFalse();
        assertThat(details.audioLanguage()).isPresent();
    }

    @Test
    void videoDetails_constructsWithEmptyAudioLanguage() {
        var details = new VideoDetails(
                VideoId.of("dQw4w9WgXcQ"), "Title", false, false, Optional.empty()
        );
        assertThat(details.audioLanguage()).isEmpty();
    }

    @Test
    void playerResponse_constructsAndAggregatesSubRecords() {
        var videoDetails = new VideoDetails(
                VideoId.of("dQw4w9WgXcQ"), "Title", false, false, Optional.empty()
        );
        var format = new Format(
                140, "audio/mp4; codecs=\"mp4a.40.2\"", 130_000L,
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.of(44100), Optional.of(5_000_000L),
                "https://example.com/audio", ""
        );
        var caption = new CaptionTrack(
                "https://example.com/timedtext", LanguageCode.of("en"), ""
        );
        var thumb = new ThumbnailUrl("https://example.com/thumb.jpg", 320, 180);

        var response = new PlayerResponse(
                videoDetails,
                PlayabilityStatus.OK,
                List.of(format),
                List.of(caption),
                List.of(thumb)
        );

        assertThat(response.videoDetails().videoId().value()).isEqualTo("dQw4w9WgXcQ");
        assertThat(response.playabilityStatus()).isEqualTo(PlayabilityStatus.OK);
        assertThat(response.adaptiveFormats()).hasSize(1);
        assertThat(response.captionTracks()).hasSize(1);
        assertThat(response.thumbnails()).hasSize(1);
    }
}
