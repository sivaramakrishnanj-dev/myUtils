package com.srk.myutils.yd.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-function converter for YouTube timedtext XML (AC-11.1).
 * Parses XML into {@link CaptionCue} instances with HTML entity decoding (AC-6.3).
 */
public final class CaptionConverter {

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    private CaptionConverter() {}

    /**
     * Converts caption cues to plain text with duplicate-prefix collapsing (AC-6.2, § 2.8).
     * One line per cue, no timestamps, no cue numbers, no blank separator lines.
     * If cue N+1's text starts with cue N's text, cue N is collapsed (dropped).
     *
     * @param cues ordered list of caption cues
     * @return plain-text lines joined by newline
     */
    public static String toTxt(List<CaptionCue> cues) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < cues.size(); i++) {
            String text = cues.get(i).text();
            if (i + 1 < cues.size() && cues.get(i + 1).text().startsWith(text)) {
                continue;
            }
            lines.add(text);
        }
        return String.join("\n", lines);
    }

    /**
     * Formats a list of caption cues as an SRT document string (AC-6.2).
     * Sequential cue numbers starting from 1, HH:MM:SS,mmm timestamps,
     * blank line between cues.
     *
     * @param cues ordered list of caption cues
     * @return SRT-formatted string
     */
    public static String toSrt(List<CaptionCue> cues) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cues.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            CaptionCue cue = cues.get(i);
            sb.append(i + 1).append('\n');
            sb.append(formatSrtTimestamp(cue.startMs()))
              .append(" --> ")
              .append(formatSrtTimestamp(cue.endMs()))
              .append('\n');
            sb.append(cue.text()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Formats milliseconds as an SRT timestamp: HH:MM:SS,mmm.
     */
    static String formatSrtTimestamp(long ms) {
        long totalSeconds = ms / 1000;
        long millis = ms % 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }

    /**
     * Parses timedtext XML into an ordered list of caption cues.
     *
     * @param xml raw XML string from YouTube's timedtext endpoint
     * @return list of cues; empty if the transcript element has no children
     * @throws InnerTubeParseException if the XML is malformed or unparseable
     */
    public static List<CaptionCue> parseXml(String xml) {
        Document doc = parseDocument(xml);
        NodeList textNodes = doc.getElementsByTagName("text");
        List<CaptionCue> cues = new ArrayList<>(textNodes.getLength());

        for (int i = 0; i < textNodes.getLength(); i++) {
            Element el = (Element) textNodes.item(i);
            long startMs = Math.round(Double.parseDouble(el.getAttribute("start")) * 1000);
            long durationMs = Math.round(Double.parseDouble(el.getAttribute("dur")) * 1000);
            String text = decodeHtmlEntities(el.getTextContent());
            cues.add(new CaptionCue(startMs, durationMs, text));
        }

        return cues;
    }

    private static Document parseDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new InnerTubeParseException("Failed to parse timedtext XML", e);
        }
    }

    static String decodeHtmlEntities(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        result = result.replace("&amp;", "&");
        result = result.replace("&lt;", "<");
        result = result.replace("&gt;", ">");
        result = result.replace("&quot;", "\"");
        result = result.replace("&#39;", "'");
        result = result.replace("&apos;", "'");
        result = result.replace("&nbsp;", " ");

        Matcher matcher = NUMERIC_ENTITY.matcher(result);
        StringBuilder sb = null;
        while (matcher.find()) {
            if (sb == null) {
                sb = new StringBuilder(result.length());
            }
            int codePoint = matcher.group(1).isEmpty()
                    ? Integer.parseInt(matcher.group(2), 10)
                    : Integer.parseInt(matcher.group(2), 16);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                    new String(Character.toChars(codePoint))));
        }
        if (sb != null) {
            matcher.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }
}
