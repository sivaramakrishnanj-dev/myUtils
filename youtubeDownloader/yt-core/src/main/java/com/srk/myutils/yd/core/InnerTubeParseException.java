package com.srk.myutils.yd.core;

/**
 * Thrown when the InnerTube JSON response cannot be parsed into a
 * {@link PlayerResponse} — malformed JSON, missing required fields,
 * or invalid field values.
 *
 * <p>Maps to exit code 11 (AC-5.2). This is a minimal stub for T-1.7;
 * T-1.9 will rehome it under {@code YoutubeDownloaderException}.
 */
public final class InnerTubeParseException extends RuntimeException {

    public InnerTubeParseException(String message) {
        super(message);
    }

    public InnerTubeParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
