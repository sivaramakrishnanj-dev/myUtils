package com.srk.myutils.yd.core;

/**
 * Thrown when the requested caption track is not available — no tracks at all,
 * only ASR with {@code --no-asr}, or no match for {@code --lang}
 * (exit code 40, AC-5.2).
 */
public final class CaptionUnavailableException extends YoutubeDownloaderException {

    public CaptionUnavailableException(String message) {
        super(message);
    }

    @Override
    public int exitCode() {
        return 40;
    }
}
