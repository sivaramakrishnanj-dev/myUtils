package com.srk.myutils.yd.core;

/**
 * Public callback interface for byte-progress events during stream downloads.
 *
 * <p>Library embedders (P3 / AC-9.3) implement this to receive progress
 * updates without depending on CLI internals or {@code System.err}.
 *
 * <p>Implementations must be safe for frequent invocation (once per 64 KB chunk).
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * Called after each chunk is written to the {@code .part} file.
     *
     * @param bytesWritten total bytes written so far
     * @param totalBytes   total expected bytes, or {@code -1} if unknown
     */
    void onProgress(long bytesWritten, long totalBytes);

    /** A no-op listener for callers that do not need progress events. */
    ProgressListener NO_OP = (b, t) -> {};
}
