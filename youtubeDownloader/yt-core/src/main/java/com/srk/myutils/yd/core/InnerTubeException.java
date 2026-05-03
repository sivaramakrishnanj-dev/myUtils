package com.srk.myutils.yd.core;

/**
 * Thrown when an InnerTube network request fails (connection refused, timeout,
 * HTTP 5xx after retries exhausted).
 *
 * <p>This is a minimal stub for T-1.5. T-1.9 will rehome it under
 * {@code YoutubeDownloaderException} and assign exit-code 10 (AC-5.2).
 */
public final class InnerTubeException extends RuntimeException {

    public InnerTubeException(String message, Throwable cause) {
        super(message, cause);
    }

    public InnerTubeException(String message) {
        super(message);
    }
}
