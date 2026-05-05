package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link CaptionConverter#parseXml(String)} — happy-path (CT-CAP-APP-1).
 */
class CaptionConverterParseTest {

    @Test
    void parseXml_givenSingleCue_returnsCaptionCueWithMilliseconds() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<transcript><text start=\"0.12\" dur=\"1.68\">Hello</text></transcript>";

        List<CaptionCue> cues = CaptionConverter.parseXml(xml);

        assertThat(cues).hasSize(1);
        assertThat(cues.get(0).startMs()).isEqualTo(120);
        assertThat(cues.get(0).durationMs()).isEqualTo(1680);
        assertThat(cues.get(0).text()).isEqualTo("Hello");
    }
}
