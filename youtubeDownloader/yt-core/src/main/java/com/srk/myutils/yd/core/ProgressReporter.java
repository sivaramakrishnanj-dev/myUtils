package com.srk.myutils.yd.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects byte-progress events from {@link StreamDownloader} and renders them
 * to a sink on a scheduled cadence.
 *
 * <ul>
 *   <li>TTY (stderr is terminal): 100 ms refresh, carriage-return overwrite ({@code \r})</li>
 *   <li>Non-TTY (piped/redirected): 1000 ms, newline-separated</li>
 * </ul>
 *
 * <p>{@code onProgress} only stores the latest snapshot; the scheduled executor
 * periodically renders it. This decouples the update rate from the write rate.
 *
 * @see ProgressListener
 */
public final class ProgressReporter implements ProgressListener, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressReporter.class);

    /** NFR-PROGRESS-TTY-REFRESH = 100 ms. */
    static final long TTY_INTERVAL_MS = 100;

    /** NFR-PROGRESS-INTERVAL = 1000 ms. */
    static final long NON_TTY_INTERVAL_MS = 1000;

    private final PrintWriter sink;
    private final boolean tty;
    private final ScheduledExecutorService scheduler;

    private final AtomicLong bytesWritten = new AtomicLong();
    private final AtomicLong totalBytes = new AtomicLong(-1);

    /** Nano-timestamp of the first progress event — used for rate and ETA. */
    private volatile long startNanos;

    /** Guard to track whether we've received at least one event. */
    private volatile boolean started;

    private ScheduledFuture<?> renderTask;

    /**
     * @param sink the writer to render progress lines to (typically wrapping {@code System.err})
     * @param tty  {@code true} if the sink is an interactive terminal (carriage-return mode)
     */
    public ProgressReporter(PrintWriter sink, boolean tty) {
        this(sink, tty, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "progress-reporter");
            t.setDaemon(true);
            return t;
        }));
    }

    /**
     * Package-private constructor for testing with an injectable scheduler.
     */
    ProgressReporter(PrintWriter sink, boolean tty, ScheduledExecutorService scheduler) {
        this.sink = sink;
        this.tty = tty;
        this.scheduler = scheduler;
        long intervalMs = tty ? TTY_INTERVAL_MS : NON_TTY_INTERVAL_MS;
        this.renderTask = scheduler.scheduleAtFixedRate(
                this::render, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Factory that detects TTY via {@code System.console() != null} and writes to {@code System.err}.
     */
    public static ProgressReporter forStderr() {
        boolean isTty = System.console() != null;
        LOGGER.debug("TTY detection: System.console() {} null → tty={}", isTty ? "!=" : "==", isTty);
        return new ProgressReporter(new PrintWriter(System.err, true), isTty);
    }

    @Override
    public void onProgress(long bytesWritten, long totalBytes) {
        if (!started) {
            startNanos = System.nanoTime();
            started = true;
        }
        this.bytesWritten.set(bytesWritten);
        this.totalBytes.set(totalBytes);
    }

    @Override
    public void close() {
        if (renderTask != null) {
            renderTask.cancel(false);
        }
        scheduler.shutdown();
        // Final render to show 100% if applicable.
        if (started) {
            render();
            if (tty) {
                sink.println();
            }
        }
        sink.flush();
    }

    private void render() {
        if (!started) {
            return;
        }
        long bytes = bytesWritten.get();
        long total = totalBytes.get();
        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedSec = elapsedNanos / 1_000_000_000.0;

        String pct = total > 0 ? String.format("%3d%%", (int) (bytes * 100 / total)) : "  ?%";
        String progress = formatBytes(bytes) + " / " + (total > 0 ? formatBytes(total) : "?");
        String rate = elapsedSec > 0.1 ? formatBytes((long) (bytes / elapsedSec)) + "/s" : "? B/s";
        String eta = (total > 0 && elapsedSec > 0.1 && bytes > 0)
                ? formatEta((long) ((total - bytes) / (bytes / elapsedSec)))
                : "ETA ?";

        String line = String.format("  %s %s  %s  %s", pct, progress, rate, eta);

        if (tty) {
            sink.print("\r" + line);
            sink.flush();
        } else {
            sink.println(line);
        }
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    static String formatEta(long seconds) {
        if (seconds < 0) {
            return "ETA ?";
        }
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("ETA %d:%02d", m, s);
    }
}
