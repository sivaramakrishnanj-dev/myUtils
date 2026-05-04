package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link FormatSelector} — happy-path only.
 * Exhaustive coverage (edge cases, codec tiebreaks, cipher filtering, no-match)
 * is the tester's job.
 */
class FormatSelectorTest {

    @Test
    void select_givenHappyPathFormats_returnsExpectedVideoAndAudio() {
        Format video1080 = new Format(
                137,
                "video/mp4; codecs=\"avc1.640028\"",
                4_500_000L,
                OptionalInt.of(1920),
                OptionalInt.of(1080),
                OptionalInt.of(30),
                OptionalInt.empty(),
                Optional.of(95_000_000L),
                "https://cdn.example.com/video137",
                ""
        );
        Format video720 = new Format(
                136,
                "video/mp4; codecs=\"avc1.4d401f\"",
                2_500_000L,
                OptionalInt.of(1280),
                OptionalInt.of(720),
                OptionalInt.of(30),
                OptionalInt.empty(),
                Optional.of(48_000_000L),
                "https://cdn.example.com/video136",
                ""
        );
        Format audio = new Format(
                140,
                "audio/mp4; codecs=\"mp4a.40.2\"",
                130_000L,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.of(44100),
                Optional.of(5_000_000L),
                "https://cdn.example.com/audio140",
                ""
        );

        FormatSelection result = new FormatSelector().select(
                List.of(video1080, video720, audio), 1080);

        assertThat(result.video().itag()).isEqualTo(137);
        assertThat(result.audio().itag()).isEqualTo(140);
    }
}
