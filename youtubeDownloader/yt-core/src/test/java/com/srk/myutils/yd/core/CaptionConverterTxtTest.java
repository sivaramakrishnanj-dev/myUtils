package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link CaptionConverter#toTxt(List)} — happy-path (CT-CAP-APP-4).
 */
class CaptionConverterTxtTest {

    @Test
    void toTxt_givenMultipleCues_producesOnePlainLinePerCueWithDuplicatePrefixCollapsing() {
        List<CaptionCue> cues = List.of(
                new CaptionCue(0, 2000, "Hello"),
                new CaptionCue(2000, 2000, "Hello world"),
                new CaptionCue(4000, 2000, "How are you")
        );

        String txt = CaptionConverter.toTxt(cues);

        assertThat(txt).isEqualTo("Hello world\nHow are you");
    }
}
