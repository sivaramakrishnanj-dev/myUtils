package com.srk.myutils.yd.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

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
     * Selects a caption track using the AC-8.1 preference chain and AC-7.* manual/ASR rules.
     *
     * @param tracks        caption tracks from the InnerTube response
     * @param requestedLang user-specified {@code --lang} (AC-8.1 step 1)
     * @param audioLanguage video's primary audio language from {@code videoDetails} (AC-8.1 step 3)
     * @param noAsr         {@code true} when {@code --no-asr} was passed (AC-7.4)
     * @return a {@link CaptionSelection} with the chosen track and ASR-fallback flag (INV-16)
     * @throws CaptionUnavailableException per AC-6.4, AC-7.4, AC-8.3
     */
    public CaptionSelection selectCaption(List<CaptionTrack> tracks,
                                          Optional<LanguageCode> requestedLang,
                                          Optional<LanguageCode> audioLanguage,
                                          boolean noAsr) {
        if (tracks.isEmpty()) {
            throw new CaptionUnavailableException(
                    "no caption tracks available for this video.");
        }

        LanguageCode target = resolveTargetLanguage(tracks, requestedLang, audioLanguage);
        return selectForLanguage(tracks, target, requestedLang.isPresent(), noAsr);
    }

    private LanguageCode resolveTargetLanguage(List<CaptionTrack> tracks,
                                               Optional<LanguageCode> requestedLang,
                                               Optional<LanguageCode> audioLanguage) {
        // AC-8.1 step 1
        if (requestedLang.isPresent()) {
            return requestedLang.get();
        }
        // AC-8.1 step 2: "en" if any English track exists
        LanguageCode en = LanguageCode.of("en");
        boolean hasEnglish = tracks.stream()
                .anyMatch(t -> t.languageCode().matches(en));
        if (hasEnglish) {
            return en;
        }
        // AC-8.1 step 3: video's audioLanguage if declared
        if (audioLanguage.isPresent()) {
            return audioLanguage.get();
        }
        // AC-8.1 step 4: first track listed
        return tracks.get(0).languageCode();
    }

    private CaptionSelection selectForLanguage(List<CaptionTrack> tracks,
                                               LanguageCode target,
                                               boolean wasExplicitlyRequested,
                                               boolean noAsr) {
        // Prefer exact match over primary-subtag-only match (AC-8.2, AC-8.4 determinism)
        Optional<CaptionTrack> manual = tracks.stream()
                .filter(t -> !t.isAsr())
                .filter(t -> t.languageCode().value().equals(target.value()))
                .findFirst()
                .or(() -> tracks.stream()
                        .filter(t -> !t.isAsr())
                        .filter(t -> t.languageCode().matches(target))
                        .findFirst());
        if (manual.isPresent()) {
            return new CaptionSelection(manual.get(), false);
        }

        // ASR lookup (AC-7.3): exact then primary-subtag
        Optional<CaptionTrack> asr = tracks.stream()
                .filter(CaptionTrack::isAsr)
                .filter(t -> t.languageCode().value().equals(target.value()))
                .findFirst()
                .or(() -> tracks.stream()
                        .filter(CaptionTrack::isAsr)
                        .filter(t -> t.languageCode().matches(target))
                        .findFirst());

        // AC-7.4: only fire when ASR matches the target but --no-asr prevents use
        if (asr.isPresent()) {
            if (noAsr) {
                throw new CaptionUnavailableException(
                        "only auto-generated captions available; --no-asr prevents their use.");
            }
            LOGGER.warn("ASR fallback for language {}", target.value());
            return new CaptionSelection(asr.get(), true);
        }

        // No track matches at all — AC-8.3
        String available = tracks.stream()
                .map(t -> t.languageCode().value())
                .distinct()
                .collect(Collectors.joining(", "));
        throw new CaptionUnavailableException(
                "no caption track available for language '" + target.value()
                        + "'. Available: " + available);
    }

    /**
     * Selects the best audio format only — no video (AC-2.1).
     *
     * @param formats adaptive formats from {@link PlayerResponse#adaptiveFormats()}
     * @return a {@link FormatSelection} with {@code video == null} and the chosen audio
     * @throws NoMatchingFormatException if no suitable audio format is found (exit 30)
     */
    public FormatSelection selectAudioOnly(List<Format> formats) {
        Format audio = selectAudio(formats);
        LOGGER.info("Audio-only format selected: itag={} {}bps", audio.itag(), audio.bitrate());
        return new FormatSelection(null, audio);
    }

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
                .orElseThrow(() -> {
                    if (allCipher(formats, true)) {
                        return newCipherException();
                    }
                    return new NoMatchingFormatException(
                            "no video format matches the selection criteria"
                                    + (maxHeight > 0 ? " (max-height=" + maxHeight + ")" : ""));
                });
    }

    private Format selectAudio(List<Format> formats) {
        return formats.stream()
                .filter(Format::isAudio)
                .filter(f -> !f.hasCipher())
                .max(audioComparator())
                .orElseThrow(() -> {
                    if (allCipher(formats, false)) {
                        return newCipherException();
                    }
                    return new NoMatchingFormatException(
                            "no audio format matches the selection criteria");
                });
    }

    private static boolean allCipher(List<Format> formats, boolean video) {
        List<Format> kind = formats.stream()
                .filter(video ? Format::isVideo : Format::isAudio)
                .toList();
        return !kind.isEmpty() && kind.stream().allMatch(Format::hasCipher);
    }

    private static CipherRequiredException newCipherException() {
        return new CipherRequiredException(
                "this video requires JavaScript signature deciphering, "
                        + "which is out of scope for this tool. Use yt-dlp for this URL.");
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
