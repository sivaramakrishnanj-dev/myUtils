package com.srk.myutils.yd.core;

/**
 * Thrown when a URL or video-id string fails validation.
 *
 * <p>This is a minimal stub for T-1.1. T-1.9 will rehome it under
 * {@code YoutubeDownloaderException} and assign exit-code 2 (AC-5.2).
 */
public final class UrlParseException extends RuntimeException {

    public UrlParseException(String message) {
        super(message);
    }
}
