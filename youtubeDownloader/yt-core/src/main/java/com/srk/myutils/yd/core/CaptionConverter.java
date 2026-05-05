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
