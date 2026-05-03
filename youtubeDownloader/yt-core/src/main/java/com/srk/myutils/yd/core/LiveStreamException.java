package com.srk.myutils.yd.core;

/**
 * Thrown when the video is a live stream or not-yet-premiered (exit code 21, AC-5.2).
 */
public final class LiveStreamException extends YoutubeDownloaderException {

    public LiveStreamException(String message) {
        super(message);
    }

    @Override
    public int exitCode() {
        return 21;
    }
}
