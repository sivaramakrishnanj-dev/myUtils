package com.srk.myutils.yd.core;

import java.util.List;

/**
 * Immutable SRT document composed of ordered caption cues (AC-6.2).
 * {@link #toString()} emits the SRT wire format with sequential cue numbers
 * and HH:MM:SS,mmm timestamps.
 *
 * @param cues ordered list of caption cues (ascending by startMs)
 */
public record SrtDocument(List<CaptionCue> cues) {

    public SrtDocument {
        cues = List.copyOf(cues);
    }

    @Override
    public String toString() {
        return CaptionConverter.toSrt(cues);
    }
}
