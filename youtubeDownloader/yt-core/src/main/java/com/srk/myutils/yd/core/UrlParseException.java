package com.srk.myutils.yd.core;

/**
 * Thrown when a URL or video-id string fails validation (exit code 2, AC-5.2).
 */
public final class UrlParseException extends YoutubeDownloaderException {

    public UrlParseException(String message) {
        super(message);
    }

    @Override
    public int exitCode() {
        return 2;
    }
}
