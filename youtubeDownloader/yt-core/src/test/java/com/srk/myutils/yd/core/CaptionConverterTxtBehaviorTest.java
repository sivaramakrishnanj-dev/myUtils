package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive behavior tests for {@link CaptionConverter#toTxt(List)} and
 * {@link PlainTextTranscript} — covering AC-6.2 (plain text output) and
 * § 2.8 duplicate-prefix collapsing.
 */
class CaptionConverterTxtBehaviorTest {

    @Nested
    @DisplayName("Basic output (AC-6.2)")
    class BasicOutput {

        @Test
        void toTxt_givenEmptyList_returnsEmptyString() {
            String result = CaptionConverter.toTxt(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        void toTxt_givenSingleCue_returnsCueText() {
            List<CaptionCue> cues = List.of(new CaptionCue(0, 1000, "Hello"));

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).isEqualTo("Hello");
        }

        @Test
        void toTxt_givenTwoDistinctCues_joinsWithNewlineNoTimestampsNoBlanks() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 1000, "Hello"),
                    new CaptionCue(1000, 1000, "World")
            );

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).isEqualTo("Hello\nWorld");
        }

        @Test
        void toTxt_outputContainsNoCueNumbers() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 1000, "First"),
                    new CaptionCue(1000, 1000, "Second")
            );

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).doesNotContain("1", "2");
        }

        @Test
        void toTxt_outputHasNoBlankSeparatorLines() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 1000, "Line one"),
                    new CaptionCue(1000, 1000, "Line two"),
                    new CaptionCue(2000, 1000, "Line three")
            );

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).doesNotContain("\n\n");
        }
    }

    @Nested
    @DisplayName("Duplicate-prefix collapsing (§ 2.8)")
    class DuplicatePrefixCollapsing {

        @Test
        void toTxt_givenNextCueStartsWithPreviousText_dropsPreviousCue() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 2000, "Hello"),
                    new CaptionCue(2000, 2000, "Hello World")
            );

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).isEqualTo("Hello World");
        }

        @Test
        void toTxt_givenLongerPrefixOverlap_dropsPreviousCue() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 2000, "The quick"),
                    new CaptionCue(2000, 2000, "The quick brown fox")
            );

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).isEqualTo("The quick brown fox");
        }

        @Test
        void toTxt_givenThreeOverlappingCues_collapsesChainToFinal() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 1000, "A"),
                    new CaptionCue(1000, 1000, "A B"),
                    new CaptionCue(2000, 1000, "A B C")
            );

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).isEqualTo("A B C");
        }

        @Test
        void toTxt_givenNonOverlappingCues_preservesBoth() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 1000, "Hello"),
                    new CaptionCue(1000, 1000, "Goodbye")
            );

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).isEqualTo("Hello\nGoodbye");
        }

        @Test
        void toTxt_givenPartialNonPrefixOverlap_preservesBoth() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 1000, "Hello world"),
                    new CaptionCue(1000, 1000, "world")
            );

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).isEqualTo("Hello world\nworld");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        void toTxt_givenCueWithEmbeddedNewline_preservesAsIs() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 2000, "Line1\nLine2")
            );

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).isEqualTo("Line1\nLine2");
        }

        @Test
        void toTxt_givenEmptyTextCue_collapsedBecauseEmptyIsPrefix() {
            // Empty string is a prefix of any string, so it gets collapsed per § 2.8
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 1000, "Before"),
                    new CaptionCue(1000, 1000, ""),
                    new CaptionCue(2000, 1000, "After")
            );

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).isEqualTo("Before\nAfter");
        }

        @Test
        void toTxt_givenUnicodeText_preservesCorrectly() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 1000, "こんにちは"),
                    new CaptionCue(1000, 1000, "世界")
            );

            String result = CaptionConverter.toTxt(cues);

            assertThat(result).isEqualTo("こんにちは\n世界");
        }
    }

    @Nested
    @DisplayName("PlainTextTranscript record")
    class PlainTextTranscriptTests {

        @Test
        void toString_givenMultipleLines_joinsWithNewline() {
            PlainTextTranscript transcript = new PlainTextTranscript(List.of("a", "b"));

            assertThat(transcript.toString()).isEqualTo("a\nb");
        }

        @Test
        void lines_returnsImmutableCopy() {
            List<String> input = new java.util.ArrayList<>(List.of("a", "b"));
            PlainTextTranscript transcript = new PlainTextTranscript(input);
            input.add("c");

            assertThat(transcript.lines()).containsExactly("a", "b");
        }

        @Test
        void toString_givenEmptyList_returnsEmptyString() {
            PlainTextTranscript transcript = new PlainTextTranscript(List.of());

            assertThat(transcript.toString()).isEmpty();
        }
    }
}
