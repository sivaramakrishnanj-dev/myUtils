package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link FormatSelector#selectCaption}.
 * Happy path: single manual English track + requestedLang=en → CaptionSelection(track, false).
 */
class FormatSelectorCaptionTest {

    @Test
    void selectCaption_givenManualEnglishTrack_returnsItWithoutAsrFallback() {
        CaptionTrack manualEn = new CaptionTrack(
                "https://example.com/timedtext?lang=en", LanguageCode.of("en"), "");
        FormatSelector selector = new FormatSelector();

        CaptionSelection result = selector.selectCaption(
                List.of(manualEn),
                Optional.of(LanguageCode.of("en")),
                Optional.empty(),
                false);

        assertThat(result.track()).isEqualTo(manualEn);
        assertThat(result.usedAsrFallback()).isFalse();
    }
}
