package com.srk.myutils.yd.core;

/**
 * Thrown when all candidate formats require JavaScript signature deciphering
 * (exit code 22, AC-5.2, AC-5.3).
 */
public final class CipherRequiredException extends YoutubeDownloaderException {

    public CipherRequiredException(String message) {
        super(message);
    }

    @Override
    public int exitCode() {
        return 22;
    }
}
