package com.srk.myutils.yd.core;

/**
 * A single timed caption cue parsed from YouTube's timedtext XML (AC-6.1).
 *
 * @param startMs    cue start time in milliseconds
 * @param durationMs cue duration in milliseconds
 * @param text       decoded text content (HTML entities resolved per AC-6.3)
 */
public record CaptionCue(long startMs, long durationMs, String text) {

    /** End time in milliseconds ({@code startMs + durationMs}). */
    public long endMs() {
        return startMs + durationMs;
    }
}
