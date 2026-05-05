package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behaviour tests for {@link FormatSelector#selectCaption}.
 * Covers AC-6.4, AC-7.1–7.4, AC-8.1–8.4, and contract tests CT-APP-8/9/10.
 */
class FormatSelectorCaptionBehaviorTest {

    private final FormatSelector selector = new FormatSelector();

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static CaptionTrack manual(String lang) {
        return new CaptionTrack("https://example.com/timedtext?lang=" + lang,
                LanguageCode.of(lang), "");
    }

    private static CaptionTrack asr(String lang) {
        return new CaptionTrack("https://example.com/timedtext?lang=" + lang + "&kind=asr",
                LanguageCode.of(lang), "asr");
    }

    private static Optional<LanguageCode> lang(String code) {
        return Optional.of(LanguageCode.of(code));
    }

    private static Optional<LanguageCode> noLang() {
        return Optional.empty();
    }

    private static Optional<LanguageCode> audioLang(String code) {
        return Optional.of(LanguageCode.of(code));
    }

    private static Optional<LanguageCode> noAudioLang() {
        return Optional.empty();
    }

    // ─── AC-6.4: no caption tracks ─────────────────────────────────────────

    @Nested
    @DisplayName("AC-6.4: no caption tracks at all")
    class NoCaptionTracks {

        @Test
        @DisplayName("Empty tracks list → CaptionUnavailableException with AC-6.4 message")
        void selectCaption_givenEmptyTracks_throwsCaptionUnavailable() {
            assertThatThrownBy(() -> selector.selectCaption(
                    List.of(), lang("en"), noAudioLang(), false))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .hasMessageContaining("no caption tracks available for this video");
        }
    }

    // ─── AC-7.1: kind classification ───────────────────────────────────────

    @Nested
    @DisplayName("AC-7.1: kind classification")
    class KindClassification {

        @Test
        @DisplayName("Manual track (kind='') → usedAsrFallback=false")
        void selectCaption_givenManualTrack_returnsNoAsrFallback() {
            CaptionTrack track = manual("en");

            CaptionSelection result = selector.selectCaption(
                    List.of(track), lang("en"), noAudioLang(), false);

            assertThat(result.usedAsrFallback()).isFalse();
        }

        @Test
        @DisplayName("ASR track (kind='asr') → usedAsrFallback=true")
        void selectCaption_givenAsrTrack_returnsAsrFallback() {
            CaptionTrack track = asr("en");

            CaptionSelection result = selector.selectCaption(
                    List.of(track), lang("en"), noAudioLang(), false);

            assertThat(result.usedAsrFallback()).isTrue();
        }
    }

    // ─── AC-7.2: manual preferred over ASR ─────────────────────────────────

    @Nested
    @DisplayName("AC-7.2: manual preferred over ASR")
    class ManualPreferred {

        @Test
        @DisplayName("Both manual en and ASR en → manual selected")
        void selectCaption_givenBothManualAndAsr_prefersManual() {
            CaptionTrack manualEn = manual("en");
            CaptionTrack asrEn = asr("en");

            CaptionSelection result = selector.selectCaption(
                    List.of(asrEn, manualEn), lang("en"), noAudioLang(), false);

            assertThat(result.track()).isEqualTo(manualEn);
            assertThat(result.usedAsrFallback()).isFalse();
        }
    }

    // ─── AC-7.3: ASR fallback ──────────────────────────────────────────────

    @Nested
    @DisplayName("AC-7.3: ASR fallback")
    class AsrFallback {

        @Test
        @DisplayName("Only ASR en, noAsr=false → ASR selected with fallback flag")
        void selectCaption_givenOnlyAsr_fallsBackToAsr() {
            CaptionTrack asrEn = asr("en");

            CaptionSelection result = selector.selectCaption(
                    List.of(asrEn), lang("en"), noAudioLang(), false);

            assertThat(result.track()).isEqualTo(asrEn);
            assertThat(result.usedAsrFallback()).isTrue();
        }
    }

    // ─── AC-7.4: --no-asr refuses ASR ──────────────────────────────────────

    @Nested
    @DisplayName("AC-7.4: --no-asr refuses ASR")
    class NoAsrRefusal {

        @Test
        @DisplayName("Only ASR en, noAsr=true → CaptionUnavailableException mentioning --no-asr")
        void selectCaption_givenOnlyAsrAndNoAsr_throwsWithMessage() {
            assertThatThrownBy(() -> selector.selectCaption(
                    List.of(asr("en")), lang("en"), noAudioLang(), true))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .hasMessageContaining("--no-asr");
        }

        @Test
        @DisplayName("C3: noAsr=true, requestedLang=ja, no Japanese track anywhere → AC-8.3 not AC-7.4")
        void selectCaption_givenNoAsrAndNoMatchingLang_throwsAc83() {
            assertThatThrownBy(() -> selector.selectCaption(
                    List.of(manual("fr"), asr("de")), lang("ja"), noAudioLang(), true))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .hasMessageContaining("no caption track available for language 'ja'")
                    .hasMessageContaining("Available:");
        }

        @Test
        @DisplayName("C4: noAsr=true, requestedLang=en, tracks [manual fr, ASR en] → AC-7.4")
        void selectCaption_givenNoAsrAndAsrMatchesTarget_throwsAc74() {
            assertThatThrownBy(() -> selector.selectCaption(
                    List.of(manual("fr"), asr("en")), lang("en"), noAudioLang(), true))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .hasMessageContaining("only auto-generated captions available; --no-asr prevents their use.");
        }
    }

    // ─── AC-8.1: preference chain ──────────────────────────────────────────

    @Nested
    @DisplayName("AC-8.1: preference chain steps")
    class PreferenceChain {

        @Test
        @DisplayName("Step 1: requestedLang=fr → fr selected, en ignored")
        void selectCaption_givenRequestedLangFr_selectsFr() {
            CaptionTrack fr = manual("fr");
            CaptionTrack en = manual("en");

            CaptionSelection result = selector.selectCaption(
                    List.of(en, fr), lang("fr"), noAudioLang(), false);

            assertThat(result.track()).isEqualTo(fr);
        }

        @Test
        @DisplayName("Step 2: no requestedLang, en available → en selected")
        void selectCaption_givenNoLangAndEnAvailable_selectsEn() {
            CaptionTrack en = manual("en");
            CaptionTrack fr = manual("fr");

            CaptionSelection result = selector.selectCaption(
                    List.of(fr, en), noLang(), noAudioLang(), false);

            assertThat(result.track()).isEqualTo(en);
        }

        @Test
        @DisplayName("Step 3: no requestedLang, no en, audioLanguage=es → es selected")
        void selectCaption_givenNoLangNoEnWithAudioLangEs_selectsEs() {
            CaptionTrack es = manual("es");
            CaptionTrack fr = manual("fr");

            CaptionSelection result = selector.selectCaption(
                    List.of(fr, es), noLang(), audioLang("es"), false);

            assertThat(result.track()).isEqualTo(es);
        }

        @Test
        @DisplayName("Step 4: no requestedLang, no en, no audioLanguage → first track selected")
        void selectCaption_givenNoLangNoEnNoAudioLang_selectsFirst() {
            CaptionTrack fr = manual("fr");
            CaptionTrack ja = manual("ja");

            CaptionSelection result = selector.selectCaption(
                    List.of(fr, ja), noLang(), noAudioLang(), false);

            assertThat(result.track()).isEqualTo(fr);
        }

        @Test
        @DisplayName("Step 4 deterministic: same tracks → same result (AC-8.4)")
        void selectCaption_givenSameTracks_returnsDeterministicResult() {
            CaptionTrack fr = manual("fr");
            CaptionTrack ja = manual("ja");
            List<CaptionTrack> tracks = List.of(fr, ja);

            CaptionSelection r1 = selector.selectCaption(tracks, noLang(), noAudioLang(), false);
            CaptionSelection r2 = selector.selectCaption(tracks, noLang(), noAudioLang(), false);

            assertThat(r1.track()).isEqualTo(r2.track());
        }
    }

    // ─── AC-8.2: language matching ─────────────────────────────────────────

    @Nested
    @DisplayName("AC-8.2: BCP-47 primary-subtag matching")
    class LanguageMatching {

        @Test
        @DisplayName("requestedLang=en, track en-US → en-US matched via primary subtag")
        void selectCaption_givenLangEnAndTrackEnUs_matchesByPrimarySubtag() {
            CaptionTrack enUs = manual("en-US");

            CaptionSelection result = selector.selectCaption(
                    List.of(enUs), lang("en"), noAudioLang(), false);

            assertThat(result.track()).isEqualTo(enUs);
        }

        @Test
        @DisplayName("requestedLang=en-US, tracks [en-US, en-GB] → exact en-US preferred")
        void selectCaption_givenExactMatch_prefersExact() {
            CaptionTrack enUs = manual("en-US");
            CaptionTrack enGb = manual("en-GB");

            CaptionSelection result = selector.selectCaption(
                    List.of(enGb, enUs), lang("en-US"), noAudioLang(), false);

            assertThat(result.track()).isEqualTo(enUs);
        }

        @Test
        @DisplayName("requestedLang=en-US, only en-GB → en-GB via primary-subtag fallback")
        void selectCaption_givenNoExactMatch_fallsToPrimarySubtag() {
            CaptionTrack enGb = manual("en-GB");

            CaptionSelection result = selector.selectCaption(
                    List.of(enGb), lang("en-US"), noAudioLang(), false);

            assertThat(result.track()).isEqualTo(enGb);
        }
    }

    // ─── AC-8.3: requested language unavailable ────────────────────────────

    @Nested
    @DisplayName("AC-8.3: requested language unavailable")
    class LanguageUnavailable {

        @Test
        @DisplayName("requestedLang=ja, tracks [en-US, fr] → exception with available list")
        void selectCaption_givenUnavailableLang_throwsWithAvailableList() {
            assertThatThrownBy(() -> selector.selectCaption(
                    List.of(manual("en-US"), manual("fr")), lang("ja"), noAudioLang(), false))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .hasMessageContaining("ja")
                    .hasMessageContaining("Available:");
        }
    }

    // ─── Mixed scenarios ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Mixed scenarios")
    class MixedScenarios {

        @Test
        @DisplayName("Manual en-US + ASR en, requestedLang=en → manual en-US wins (AC-7.2)")
        void selectCaption_givenManualEnUsAndAsrEn_prefersManualEnUs() {
            CaptionTrack manualEnUs = manual("en-US");
            CaptionTrack asrEn = asr("en");

            CaptionSelection result = selector.selectCaption(
                    List.of(asrEn, manualEnUs), lang("en"), noAudioLang(), false);

            assertThat(result.track()).isEqualTo(manualEnUs);
            assertThat(result.usedAsrFallback()).isFalse();
        }

        @Test
        @DisplayName("Only ASR de available, requestedLang=en → AC-8.3 exception")
        void selectCaption_givenOnlyAsrDeAndRequestedEn_throwsUnavailable() {
            assertThatThrownBy(() -> selector.selectCaption(
                    List.of(asr("de")), lang("en"), noAudioLang(), false))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .hasMessageContaining("en")
                    .hasMessageContaining("Available:");
        }
    }

    // ─── Fixture-based integration tests (CT-APP-8/9/10) ──────────────────

    @Nested
    @DisplayName("Fixture integration: CT-APP-8/9/10")
    class FixtureIntegration {

        @Test
        @DisplayName("CT-APP-9: asr-only fixture, lang=en, noAsr=false → ASR track, usedAsrFallback=true")
        void selectCaption_givenAsrOnlyFixture_returnsAsrTrack() throws IOException {
            PlayerResponse response = loadFixture("innertube-response-asr-only.json");
            List<CaptionTrack> tracks = response.captionTracks();

            CaptionSelection result = selector.selectCaption(
                    tracks, lang("en"), noAudioLang(), false);

            assertThat(result.track().isAsr()).isTrue();
            assertThat(result.track().languageCode().value()).isEqualTo("en");
            assertThat(result.usedAsrFallback()).isTrue();
        }

        @Test
        @DisplayName("CT-APP-10: asr-only fixture, noAsr=true → CaptionUnavailableException")
        void selectCaption_givenAsrOnlyFixtureAndNoAsr_throws() throws IOException {
            PlayerResponse response = loadFixture("innertube-response-asr-only.json");
            List<CaptionTrack> tracks = response.captionTracks();

            assertThatThrownBy(() -> selector.selectCaption(
                    tracks, lang("en"), noAudioLang(), true))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .hasMessageContaining("--no-asr");
        }

        @Test
        @DisplayName("CT-APP-8: no-captions fixture → CaptionUnavailableException")
        void selectCaption_givenNoCaptionsFixture_throws() throws IOException {
            PlayerResponse response = loadFixture("innertube-response-no-captions.json");
            List<CaptionTrack> tracks = response.captionTracks();

            assertThatThrownBy(() -> selector.selectCaption(
                    tracks, lang("en"), noAudioLang(), false))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .hasMessageContaining("no caption track");
        }

        private PlayerResponse loadFixture(String filename) throws IOException {
            try (InputStream is = getClass().getResourceAsStream("/fixtures/" + filename)) {
                assertThat(is).as("Fixture %s must exist on classpath", filename).isNotNull();
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return PlayerResponseExtractor.extract(json);
            }
        }
    }
}
