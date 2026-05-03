package com.srk.myutils.yd.core;

/**
 * Playability status from the InnerTube {@code /player} response.
 *
 * <p>Known values are mapped from the {@code playabilityStatus.status} string.
 * {@link #UNKNOWN} is a sentinel for any value our parser does not recognize —
 * mapped to exit code 11 (parse error), not 20 (video unavailable).
 *
 * @see <a href="../../../../../design/03-data-model.md">03-data-model.md § 2.7</a>
 */
public enum PlayabilityStatus {
    OK,
    UNPLAYABLE,
    LIVE_STREAM_OFFLINE,
    LOGIN_REQUIRED,
    ERROR,
    AGE_VERIFICATION_REQUIRED,
    UNKNOWN
}
