package com.srk.myutils.yd.core;

/**
 * Thrown when ffmpeg is missing, too old, or its invocation fails
 * (exit code 60, AC-5.2, AC-13.*).
 */
public final class FfmpegException extends YoutubeDownloaderException {

    public FfmpegException(String message) {
        super(message);
    }

    public FfmpegException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int exitCode() {
        return 60;
    }
}
