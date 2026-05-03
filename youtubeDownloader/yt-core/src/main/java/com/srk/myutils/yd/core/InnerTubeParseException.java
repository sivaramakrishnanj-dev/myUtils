package com.srk.myutils.yd.core;

/**
 * Thrown when the InnerTube JSON response cannot be parsed into a
 * {@link PlayerResponse} — malformed JSON, missing required fields,
 * or invalid field values (exit code 11, AC-5.2).
 */
public final class InnerTubeParseException extends YoutubeDownloaderException {

    public InnerTubeParseException(String message) {
        super(message);
    }

    public InnerTubeParseException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int exitCode() {
        return 11;
    }
}
