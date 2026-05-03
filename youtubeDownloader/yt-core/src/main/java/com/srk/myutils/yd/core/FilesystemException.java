package com.srk.myutils.yd.core;

/**
 * Thrown on filesystem errors — cannot write, disk full, permissions
 * (exit code 70, AC-5.2).
 */
public final class FilesystemException extends YoutubeDownloaderException {

    public FilesystemException(String message) {
        super(message);
    }

    public FilesystemException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int exitCode() {
        return 70;
    }
}
