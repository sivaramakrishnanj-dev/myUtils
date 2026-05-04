package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.ProgressListener;
import com.srk.myutils.yd.core.ProgressReporter;

/**
 * CLI-side {@link ProgressListener} that delegates to a {@link ProgressReporter}
 * writing to {@code System.err} with TTY-aware refresh (AC-4.1, AC-9.3).
 */
public final class StderrProgressListener implements ProgressListener, AutoCloseable {

    private final ProgressReporter delegate;

    public StderrProgressListener() {
        this.delegate = ProgressReporter.forStderr();
    }

    @Override
    public void onProgress(long bytesWritten, long totalBytes) {
        delegate.onProgress(bytesWritten, totalBytes);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
