package com.srk.myutils.yd.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Selects the best video and audio {@link Format} from a list of adaptive formats.
 *
 * <p>Pure function — no network, no filesystem I/O (AC-11.1).
 *
 * <h3>Video selection (AC-1.3, AC-1.4)</h3>
 * <ol>
 *   <li>Filter to video formats with {@code height ≤ maxHeight} (0 = uncapped).</li>
 *   <li>Exclude cipher-protected formats.</li>
 *   <li>Sort by: height desc → codec preference (H.264 &gt; VP9 &gt; AV1) → bitrate desc.</li>
 *   <li>Pick the first.</li>
 * </ol>
 *
 * <h3>Audio selection (AC-1.5, AC-2.2)</h3>
 * <ol>
 *   <li>Filter to audio formats without cipher.</li>
 *   <li>Sort by: container preference (m4a/mp4 &gt; webm) → bitrate desc.</li>
 *   <li>Pick the first.</li>
 * </ol>
 *
 * @see <a href="design/00-requirements.md">AC-1.3, AC-1.4, AC-1.5, AC-2.2</a>
 * @see <a href="design/02-architecture.md">02-architecture.md § 1.2.2 FormatSelector</a>
 */
public final class FormatSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(FormatSelector.class);

    /**
     * Selects the best video + audio format pair from the given formats.
     *
     * @param formats   adaptive formats from {@link PlayerResponse#adaptiveFormats()}
     * @param maxHeight maximum video height (0 = uncapped, AC-1.3)
     * @return a {@link FormatSelection} with the chosen video and audio
     * @throws NoMatchingFormatException if no suitable video or audio format is found (exit 30)
     */
    public FormatSelection select(List<Format> formats, int maxHeight) {
        Format video = selectVideo(formats, maxHeight);
        Format audio = selectAudio(formats);
        LOGGER.info("Format selected: video itag={} {}p {} {}bps, audio itag={} {}bps",
                video.itag(), video.height().orElse(0), codecName(video.mimeType()), video.bitrate(),
                audio.itag(), audio.bitrate());
        return new FormatSelection(video, audio);
    }

    private Format selectVideo(List<Format> formats, int maxHeight) {
        return formats.stream()
                .filter(Format::isVideo)
                .filter(f -> !f.hasCipher())
                .filter(f -> maxHeight == 0 || f.height().orElse(0) <= maxHeight)
                .max(videoComparator())
                .orElseThrow(() -> new NoMatchingFormatException(
                        "no video format matches the selection criteria"
                                + (maxHeight > 0 ? " (max-height=" + maxHeight + ")" : "")));
    }

    private Format selectAudio(List<Format> formats) {
        return formats.stream()
                .filter(Format::isAudio)
                .filter(f -> !f.hasCipher())
                .max(audioComparator())
                .orElseThrow(() -> new NoMatchingFormatException(
                        "no audio format matches the selection criteria"));
    }

    /**
     * Video comparator: height asc → codec preference asc → bitrate asc.
     * Used with {@code .max()} so higher values win.
     */
    private static Comparator<Format> videoComparator() {
        return Comparator.comparingInt((Format f) -> f.height().orElse(0))
                .thenComparingInt(f -> codecRank(f.mimeType()))
                .thenComparingLong(Format::bitrate);
    }

    /**
     * Audio comparator: container preference asc → bitrate asc.
     * Used with {@code .max()} so higher values win.
     */
    private static Comparator<Format> audioComparator() {
        return Comparator.comparingInt((Format f) -> containerRank(f.mimeType()))
                .thenComparingLong(Format::bitrate);
    }

    /**
     * Codec preference: H.264 (avc1) = 2, VP9 = 1, AV1 = 0.
     * Higher rank wins (AC-1.4: H.264 &gt; VP9 &gt; AV1).
     */
    static int codecRank(String mimeType) {
        String lower = mimeType.toLowerCase(Locale.ROOT);
        if (lower.contains("avc1")) return 2;
        if (lower.contains("vp9") || lower.contains("vp09")) return 1;
        if (lower.contains("av01")) return 0;
        return -1; // unknown codec — lowest priority
    }

    /**
     * Container preference: m4a/mp4 = 1, webm = 0.
     * Higher rank wins (AC-2.2: m4a preferred over webm).
     */
    static int containerRank(String mimeType) {
        String lower = mimeType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("audio/mp4")) return 1;
        if (lower.startsWith("audio/webm")) return 0;
        return -1; // unknown container — lowest priority
    }

    private static String codecName(String mimeType) {
        int semi = mimeType.indexOf(';');
        return semi >= 0 ? mimeType.substring(semi + 1).trim() : mimeType;
    }
}
