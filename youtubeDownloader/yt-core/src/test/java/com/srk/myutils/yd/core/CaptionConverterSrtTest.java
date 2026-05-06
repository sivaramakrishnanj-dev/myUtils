package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link CaptionConverter#toSrt(List)} — happy-path (CT-CAP-APP-3).
 */
class CaptionConverterSrtTest {

    @Test
    void toSrt_givenMultipleCues_producesCanonicalSrt() {
        List<CaptionCue> cues = List.of(
                new CaptionCue(120, 1680, "Hello"),
                new CaptionCue(1800, 1700, "World")
        );

        String srt = CaptionConverter.toSrt(cues);

        assertThat(srt).isEqualTo(
                "1\n" +
                "00:00:00,120 --> 00:00:01,800\n" +
                "Hello\n" +
                "\n" +
                "2\n" +
                "00:00:01,800 --> 00:00:03,500\n" +
                "World\n"
        );
    }
}
