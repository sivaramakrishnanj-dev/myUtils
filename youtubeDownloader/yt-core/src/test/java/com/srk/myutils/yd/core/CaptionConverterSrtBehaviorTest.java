package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for {@link CaptionConverter#toSrt(List)} and
 * {@link CaptionConverter#formatSrtTimestamp(long)} covering CT-CAP-APP-3 / AC-6.2.
 */
class CaptionConverterSrtBehaviorTest {

    @Nested
    @DisplayName("toSrt format (CT-CAP-APP-3)")
    class ToSrtFormat {

        @Test
        void toSrt_givenEmptyList_returnsEmptyString() {
            String srt = CaptionConverter.toSrt(Collections.emptyList());

            assertThat(srt).isEmpty();
        }

        @Test
        void toSrt_givenSingleCue_producesNumberedCueBlock() {
            List<CaptionCue> cues = List.of(new CaptionCue(120, 1680, "Hello"));

            String srt = CaptionConverter.toSrt(cues);

            assertThat(srt).isEqualTo(
                    "1\n" +
                    "00:00:00,120 --> 00:00:01,800\n" +
                    "Hello\n"
            );
        }

        @Test
        void toSrt_givenMultipleCues_numbersSequentiallyWithBlankLineSeparator() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 1000, "First"),
                    new CaptionCue(1000, 1000, "Second"),
                    new CaptionCue(2000, 1000, "Third")
            );

            String srt = CaptionConverter.toSrt(cues);

            assertThat(srt).isEqualTo(
                    "1\n" +
                    "00:00:00,000 --> 00:00:01,000\n" +
                    "First\n" +
                    "\n" +
                    "2\n" +
                    "00:00:01,000 --> 00:00:02,000\n" +
                    "Second\n" +
                    "\n" +
                    "3\n" +
                    "00:00:02,000 --> 00:00:03,000\n" +
                    "Third\n"
            );
        }

        @Test
        void toSrt_timestampUsesCommaBeforeMilliseconds() {
            List<CaptionCue> cues = List.of(new CaptionCue(1500, 500, "x"));

            String srt = CaptionConverter.toSrt(cues);

            assertThat(srt).contains("00:00:01,500 --> 00:00:02,000");
        }

        @Test
        void toSrt_endTimeEqualsStartPlusDuration() {
            List<CaptionCue> cues = List.of(new CaptionCue(5000, 3000, "x"));

            String srt = CaptionConverter.toSrt(cues);

            assertThat(srt).contains("00:00:05,000 --> 00:00:08,000");
        }

        @Test
        void toSrt_blankLineSeparatesCuesButNoTrailingBlankLine() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 1000, "A"),
                    new CaptionCue(1000, 1000, "B")
            );

            String srt = CaptionConverter.toSrt(cues);

            // Blank line between cues, no trailing blank line after last cue
            assertThat(srt).endsWith("B\n");
            assertThat(srt).doesNotEndWith("B\n\n");
            assertThat(srt).contains("A\n\n2\n");
        }

        @Test
        void toSrt_textPreservedVerbatim() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 1000, "Already decoded: \"quotes\" & <angle>")
            );

            String srt = CaptionConverter.toSrt(cues);

            assertThat(srt).contains("Already decoded: \"quotes\" & <angle>\n");
        }

        @Test
        void toSrt_multiLineCueTextPreserved() {
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 2000, "Line one\nLine two")
            );

            String srt = CaptionConverter.toSrt(cues);

            assertThat(srt).isEqualTo(
                    "1\n" +
                    "00:00:00,000 --> 00:00:02,000\n" +
                    "Line one\nLine two\n"
            );
        }
    }

    @Nested
    @DisplayName("formatSrtTimestamp")
    class FormatSrtTimestamp {

        @Test
        void formatSrtTimestamp_zero() {
            assertThat(CaptionConverter.formatSrtTimestamp(0)).isEqualTo("00:00:00,000");
        }

        @Test
        void formatSrtTimestamp_120ms() {
            assertThat(CaptionConverter.formatSrtTimestamp(120)).isEqualTo("00:00:00,120");
        }

        @Test
        void formatSrtTimestamp_oneSecond() {
            assertThat(CaptionConverter.formatSrtTimestamp(1000)).isEqualTo("00:00:01,000");
        }

        @Test
        void formatSrtTimestamp_oneMinute() {
            assertThat(CaptionConverter.formatSrtTimestamp(60_000)).isEqualTo("00:01:00,000");
        }

        @Test
        void formatSrtTimestamp_oneHour() {
            assertThat(CaptionConverter.formatSrtTimestamp(3_600_000)).isEqualTo("01:00:00,000");
        }

        @Test
        void formatSrtTimestamp_combinedHoursMinutesSecondsMillis() {
            // 1h + 1m + 1s + 500ms = 3600000 + 60000 + 1000 + 500 = 3661500
            assertThat(CaptionConverter.formatSrtTimestamp(3_661_500)).isEqualTo("01:01:01,500");
        }

        @Test
        void formatSrtTimestamp_tenHoursPlus() {
            // 10h + 12s + 345ms = 36000000 + 12000 + 345 = 36012345
            assertThat(CaptionConverter.formatSrtTimestamp(36_012_345)).isEqualTo("10:00:12,345");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        void toSrt_cueWithZeroDuration_startEqualsEnd() {
            List<CaptionCue> cues = List.of(new CaptionCue(5000, 0, "Instant"));

            String srt = CaptionConverter.toSrt(cues);

            assertThat(srt).contains("00:00:05,000 --> 00:00:05,000");
        }

        @Test
        void toSrt_overlappingCues_emittedInOrder() {
            // Cue 2 starts before cue 1 ends — SRT allows overlaps
            List<CaptionCue> cues = List.of(
                    new CaptionCue(0, 3000, "First"),
                    new CaptionCue(2000, 2000, "Second")
            );

            String srt = CaptionConverter.toSrt(cues);

            assertThat(srt).isEqualTo(
                    "1\n" +
                    "00:00:00,000 --> 00:00:03,000\n" +
                    "First\n" +
                    "\n" +
                    "2\n" +
                    "00:00:02,000 --> 00:00:04,000\n" +
                    "Second\n"
            );
        }
    }
}
