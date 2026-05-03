package com.srk.myutils.yd.core;

import java.util.regex.Pattern;

/**
 * Validated 11-character YouTube video identifier.
 *
 * <p>Constructed via {@link #of(String)} or the canonical constructor, both of
 * which enforce the {@link #PATTERN} invariant (AC-1.1). A {@code VideoId}
 * that exists is guaranteed valid — downstream components never re-validate.
 *
 * @param value the 11-character video id, matching {@code [A-Za-z0-9_-]{11}}
 */
public record VideoId(String value) {

    /** The regex every YouTube video id must satisfy (AC-1.1). */
    public static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_-]{11}");

    public VideoId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new UrlParseException("Invalid video id: " + value);
        }
    }

    /** Static factory — preferred construction entry point (Effective Java Item 1). */
    public static VideoId of(String raw) {
        return new VideoId(raw);
    }
}
