package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for {@link CaptionConverter#parseXml(String)}.
 * Covers CT-CAP-APP-1, CT-CAP-APP-2, AC-6.1, AC-6.3, AC-11.1.
 */
class CaptionConverterParseBehaviorTest {

    private static String wrap(String textElements) {
        return "<transcript>" + textElements + "</transcript>";
    }

    // ─── CT-CAP-APP-1: basic parse ───────────────────────────────────────────

    @Test
    @DisplayName("CT-CAP-APP-1: single cue parsed with correct ms conversion")
    void parseXml_givenSingleCue_returnsCorrectMilliseconds() {
        String xml = wrap("<text start=\"0.12\" dur=\"1.68\">Hello</text>");

        List<CaptionCue> cues = CaptionConverter.parseXml(xml);

        assertThat(cues).hasSize(1);
        CaptionCue cue = cues.get(0);
        assertThat(cue.startMs()).isEqualTo(120);
        assertThat(cue.durationMs()).isEqualTo(1680);
        assertThat(cue.text()).isEqualTo("Hello");
    }

    // ─── CT-CAP-APP-2: HTML entity decode (AC-6.3) ──────────────────────────

    @Nested
    @DisplayName("CT-CAP-APP-2: HTML entity decoding (AC-6.3)")
    class HtmlEntityDecode {

        @Test
        @DisplayName("&quot; and &amp; decoded correctly")
        void parseXml_givenQuotAndAmp_decodesEntities() {
            String xml = wrap("<text start=\"0\" dur=\"1\">&quot;hello&quot; &amp; goodbye</text>");

            List<CaptionCue> cues = CaptionConverter.parseXml(xml);

            assertThat(cues.get(0).text()).isEqualTo("\"hello\" & goodbye");
        }

        @Test
        @DisplayName("&#39; decoded to apostrophe")
        void parseXml_givenNumeric39_decodesToApostrophe() {
            String xml = wrap("<text start=\"0\" dur=\"1\">it&#39;s</text>");

            assertThat(CaptionConverter.parseXml(xml).get(0).text()).isEqualTo("it's");
        }

        @Test
        @DisplayName("&lt; and &gt; decoded to angle brackets")
        void parseXml_givenLtGt_decodesToBrackets() {
            String xml = wrap("<text start=\"0\" dur=\"1\">&lt;b&gt;</text>");

            assertThat(CaptionConverter.parseXml(xml).get(0).text()).isEqualTo("<b>");
        }

        @Test
        @DisplayName("&amp; decoded to ampersand")
        void parseXml_givenAmp_decodesToAmpersand() {
            String xml = wrap("<text start=\"0\" dur=\"1\">A &amp; B</text>");

            assertThat(CaptionConverter.parseXml(xml).get(0).text()).isEqualTo("A & B");
        }

        @Test
        @DisplayName("&nbsp; via double-encoding decoded to space by decodeHtmlEntities")
        void decodeHtmlEntities_givenNbsp_decodesToSpace() {
            // &nbsp; is not a predefined XML entity, so it cannot appear raw in XML.
            // The impl's decodeHtmlEntities handles it for text already extracted from XML
            // (e.g., double-encoded as &amp;nbsp; in the source, which XML decodes to &nbsp;).
            String decoded = CaptionConverter.decodeHtmlEntities("hello&nbsp;world");

            assertThat(decoded).isEqualTo("hello world");
        }

        @Test
        @DisplayName("&#8217; (right single quotation) decoded")
        void parseXml_givenNumeric8217_decodesToRightQuote() {
            String xml = wrap("<text start=\"0\" dur=\"1\">it&#8217;s</text>");

            assertThat(CaptionConverter.parseXml(xml).get(0).text()).isEqualTo("it\u2019s");
        }

        @Test
        @DisplayName("&#x27; (hex) decoded to apostrophe")
        void parseXml_givenHex27_decodesToApostrophe() {
            String xml = wrap("<text start=\"0\" dur=\"1\">it&#x27;s</text>");

            assertThat(CaptionConverter.parseXml(xml).get(0).text()).isEqualTo("it's");
        }

        @Test
        @DisplayName("Unknown entity &foo; — XML parser behavior")
        void parseXml_givenUnknownEntity_throwsParseException() {
            // XML parsers reject undefined entities — this is malformed XML
            String xml = wrap("<text start=\"0\" dur=\"1\">&foo;</text>");

            assertThatThrownBy(() -> CaptionConverter.parseXml(xml))
                    .isInstanceOf(InnerTubeParseException.class);
        }
    }

    // ─── Multiple cues ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Multiple cues returned in document order")
    void parseXml_givenThreeCues_returnsAllInOrder() {
        String xml = wrap(
                "<text start=\"0\" dur=\"1\">A</text>"
                + "<text start=\"1.5\" dur=\"2\">B</text>"
                + "<text start=\"4\" dur=\"1\">C</text>");

        List<CaptionCue> cues = CaptionConverter.parseXml(xml);

        assertThat(cues).hasSize(3);
        assertThat(cues.get(0).text()).isEqualTo("A");
        assertThat(cues.get(1).text()).isEqualTo("B");
        assertThat(cues.get(2).text()).isEqualTo("C");
    }

    @Test
    @DisplayName("Empty transcript returns empty list")
    void parseXml_givenEmptyTranscript_returnsEmptyList() {
        String xml = "<transcript/>";

        assertThat(CaptionConverter.parseXml(xml)).isEmpty();
    }

    @Test
    @DisplayName("Self-closing <text/> produces cue with empty text")
    void parseXml_givenSelfClosingText_returnsCueWithEmptyText() {
        String xml = wrap("<text start=\"1\" dur=\"2\" />");

        List<CaptionCue> cues = CaptionConverter.parseXml(xml);

        assertThat(cues).hasSize(1);
        assertThat(cues.get(0).text()).isEmpty();
    }

    // ─── Whitespace + newlines ───────────────────────────────────────────────

    @Test
    @DisplayName("Embedded newline in text is preserved")
    void parseXml_givenTextWithNewline_preservesNewline() {
        String xml = wrap("<text start=\"0\" dur=\"1\">line one\nline two</text>");

        assertThat(CaptionConverter.parseXml(xml).get(0).text()).isEqualTo("line one\nline two");
    }

    // ─── Malformed XML ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Broken XML throws InnerTubeParseException")
    void parseXml_givenBrokenXml_throwsInnerTubeParseException() {
        String xml = "<text start=\"0\" dur=\"1\"";

        assertThatThrownBy(() -> CaptionConverter.parseXml(xml))
                .isInstanceOf(InnerTubeParseException.class);
    }

    @Test
    @DisplayName("Non-XML content throws InnerTubeParseException")
    void parseXml_givenPlainText_throwsInnerTubeParseException() {
        assertThatThrownBy(() -> CaptionConverter.parseXml("hello"))
                .isInstanceOf(InnerTubeParseException.class);
    }

    // ─── Numeric conversion precision ────────────────────────────────────────

    @Test
    @DisplayName("start=0.5 dur=1.5 → (500, 1500)")
    void parseXml_givenHalfSeconds_convertsCorrectly() {
        String xml = wrap("<text start=\"0.5\" dur=\"1.5\">X</text>");

        CaptionCue cue = CaptionConverter.parseXml(xml).get(0);

        assertThat(cue.startMs()).isEqualTo(500);
        assertThat(cue.durationMs()).isEqualTo(1500);
    }

    @Test
    @DisplayName("Fractional ms rounding: start=0.1234 → Math.round(123.4) = 123")
    void parseXml_givenFractionalMs_roundsCorrectly() {
        String xml = wrap("<text start=\"0.1234\" dur=\"0\">X</text>");

        assertThat(CaptionConverter.parseXml(xml).get(0).startMs()).isEqualTo(123);
    }

    @Test
    @DisplayName("start=0 dur=0 → (0, 0)")
    void parseXml_givenZeroValues_returnsZeros() {
        String xml = wrap("<text start=\"0\" dur=\"0\">X</text>");

        CaptionCue cue = CaptionConverter.parseXml(xml).get(0);

        assertThat(cue.startMs()).isEqualTo(0);
        assertThat(cue.durationMs()).isEqualTo(0);
    }

    // ─── XXE safety ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("XML with external DTD reference is rejected or ignored (no XXE)")
    void parseXml_givenXxePayload_doesNotResolveExternalEntity() {
        String xml = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE foo ["
                + "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">"
                + "]>"
                + "<transcript><text start=\"0\" dur=\"1\">&xxe;</text></transcript>";

        // Either throws (external entities disabled) or returns without the file content
        try {
            List<CaptionCue> cues = CaptionConverter.parseXml(xml);
            // If it parses, the entity must NOT have resolved to file contents
            assertThat(cues.get(0).text()).doesNotContain("root:");
        } catch (InnerTubeParseException e) {
            // Acceptable — parser rejects the DTD entirely
        }
    }

    // ─── CaptionCue record ───────────────────────────────────────────────────

    @Test
    @DisplayName("endMs() = startMs + durationMs")
    void endMs_computedCorrectly() {
        CaptionCue cue = new CaptionCue(120, 1680, "Hello");

        assertThat(cue.endMs()).isEqualTo(1800);
    }
}
