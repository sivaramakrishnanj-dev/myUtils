package com.srk.myutils.yd.core;

/**
 * Thrown when the output file already exists and {@code --force} was not given
 * (exit code 50, AC-5.2, AC-3.6).
 */
public final class OutputExistsException extends YoutubeDownloaderException {

    public OutputExistsException(String message) {
        super(message);
    }

    @Override
    public int exitCode() {
        return 50;
    }
}
