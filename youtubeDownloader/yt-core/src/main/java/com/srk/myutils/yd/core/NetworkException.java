package com.srk.myutils.yd.core;

/**
 * Thrown on network failure — DNS, TCP, TLS, or HTTP transport error after
 * all retries exhausted (exit code 10, AC-5.2).
 */
public final class NetworkException extends YoutubeDownloaderException {

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }

    public NetworkException(String message) {
        super(message);
    }

    @Override
    public int exitCode() {
        return 10;
    }
}
