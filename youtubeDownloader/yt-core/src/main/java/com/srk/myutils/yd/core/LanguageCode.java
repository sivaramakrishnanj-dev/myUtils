package com.srk.myutils.yd.core;

import java.util.regex.Pattern;

/**
 * A BCP-47-ish language tag: a 2–3 letter primary subtag with an optional
 * region/script subtag (e.g. {@code en}, {@code en-US}, {@code pt-BR}).
 *
 * <p>Constructed via {@link #of(String)}. The primary subtag is normalized to
 * lowercase; the optional region subtag preserves its original case.
 * {@link #matches(LanguageCode)} implements the primary-subtag match rule
 * from AC-8.2: {@code en} matches {@code en-US} and {@code en-GB}.
 *
 * @param value the normalized language tag
 */
public record LanguageCode(String value) {

    /** BCP-47-ish: {@code <primary>} or {@code <primary>-<subtag>}. */
    public static final Pattern PATTERN = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]+)?");

    public LanguageCode {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid language code: " + value);
        }
    }

    /** Static factory — preferred construction entry point (Effective Java Item 1). */
    public static LanguageCode of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Invalid language code: null");
        }
        // Normalize primary subtag to lowercase; preserve region subtag case.
        int dash = raw.indexOf('-');
        String normalized = dash < 0
                ? raw.toLowerCase()
                : raw.substring(0, dash).toLowerCase() + raw.substring(dash);
        return new LanguageCode(normalized);
    }

    /**
     * Returns the lowercase primary subtag (the part before the first {@code -},
     * or the entire value if there is no subtag).
     */
    public String primary() {
        int dash = value.indexOf('-');
        return dash < 0 ? value : value.substring(0, dash);
    }

    /**
     * AC-8.2 primary-subtag match: {@code en} matches {@code en-US}.
     * Reflexive and symmetric over primary subtag.
     */
    public boolean matches(LanguageCode other) {
        return this.value.equals(other.value) || this.primary().equals(other.primary());
    }
}
