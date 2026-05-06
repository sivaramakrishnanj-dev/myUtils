package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SrtDocumentTest {

    @Test
    void toString_givenTwoCues_producesCanonicalSrt() {
        List<CaptionCue> cues = List.of(
                new CaptionCue(120, 1680, "Hello"),
                new CaptionCue(1800, 1700, "World")
        );
        SrtDocument doc = new SrtDocument(cues);

        String expected = """
                1
                00:00:00,120 --> 00:00:01,800
                Hello

                2
                00:00:01,800 --> 00:00:03,500
                World
                """;

        assertThat(doc.toString()).isEqualTo(expected);
    }
}
