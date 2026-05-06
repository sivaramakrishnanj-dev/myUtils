package com.srk.myutils.yd.core;

import java.util.List;

/**
 * Immutable plain-text transcript with duplicate-prefix collapsing (AC-6.2, § 2.8).
 * {@link #toString()} produces one line per cue, no timestamps, no cue numbers,
 * no blank separator lines.
 *
 * @param lines collapsed text lines in cue order
 */
public record PlainTextTranscript(List<String> lines) {

    public PlainTextTranscript {
        lines = List.copyOf(lines);
    }

    @Override
    public String toString() {
        return String.join("\n", lines);
    }
}
