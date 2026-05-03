package com.srk.myutils.yd.core;

/**
 * Thrown when the video is private, deleted, or geo-blocked (exit code 20, AC-5.2).
 */
public final class VideoUnavailableException extends YoutubeDownloaderException {

    public VideoUnavailableException(String message) {
        super(message);
    }

    @Override
    public int exitCode() {
        return 20;
    }
}
