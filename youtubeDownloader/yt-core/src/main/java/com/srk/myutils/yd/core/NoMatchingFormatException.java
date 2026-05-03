package com.srk.myutils.yd.core;

/**
 * Thrown when no formats match the requested selection criteria
 * (exit code 30, AC-5.2).
 */
public final class NoMatchingFormatException extends YoutubeDownloaderException {

    public NoMatchingFormatException(String message) {
        super(message);
    }

    @Override
    public int exitCode() {
        return 30;
    }
}
