package com.srk.myutils.yd.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single caption-track descriptor from the InnerTube response's
 * {@code captions.playerCaptionsTracklistRenderer.captionTracks[]}.
 *
 * @param baseUrl      timed-text fetch URL (AC-6.1)
 * @param languageCode BCP-47 language tag
 * @param kind         {@code "asr"} for auto-generated tracks; empty string for manual (AC-7.1)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CaptionTrack(String baseUrl, LanguageCode languageCode, String kind) {

    /** {@code true} when this track is auto-generated (AC-7.1). */
    public boolean isAsr() {
        return "asr".equals(kind);
    }
}
