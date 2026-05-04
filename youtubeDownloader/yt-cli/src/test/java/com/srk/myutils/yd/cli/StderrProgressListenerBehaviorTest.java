package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.ProgressListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Comprehensive behavior tests for {@link StderrProgressListener}.
 *
 * <p>Covers AC-4.1 (progress output to stderr), AC-9.3 (implements
 * ProgressListener), and AutoCloseable contract.
 *
 * <p>Captures {@code System.err} to verify stderr output without
 * real terminal interaction.
 */
class StderrProgressListenerBehaviorTest {

    // ── 1. onProgress delegates to ProgressReporter (stderr capture) ────

    @Test
    @DisplayName("AC-4.1: onProgress writes progress to stderr")
    void onProgress_givenBytesAndClose_writesToStderr() throws Exception {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capture, true));
        try {
            try (StderrProgressListener sut = new StderrProgressListener()) {
                sut.onProgress(256_000, 512_000);
                // Give the scheduled executor time to render at least once.
                Thread.sleep(1200);
            }

            String output = capture.toString();
            assertThat(output).contains("50%");
        } finally {
            System.setErr(originalErr);
        }
    }

    // ── 2. close() stops scheduler (no output after close) ──────────────

    @Test
    @DisplayName("close() stops the scheduler — no further output after close")
    void close_givenActiveListener_stopsScheduler() throws Exception {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capture, true));
        try {
            StderrProgressListener sut = new StderrProgressListener();
            sut.onProgress(100, 200);
            sut.close();

            int sizeAfterClose = capture.size();
            Thread.sleep(500);

            assertThat(capture.size()).isEqualTo(sizeAfterClose);
        } finally {
            System.setErr(originalErr);
        }
    }

    // ── 3. AutoCloseable — try-with-resources ───────────────────────────

    @Test
    @DisplayName("StderrProgressListener is AutoCloseable — try-with-resources works")
    void tryWithResources_givenListener_closesCleanly() {
        assertThatCode(() -> {
            try (StderrProgressListener sut = new StderrProgressListener()) {
                sut.onProgress(50, 100);
            }
        }).doesNotThrowAnyException();
    }

    // ── 4. Implements ProgressListener ──────────────────────────────────

    @Test
    @DisplayName("StderrProgressListener implements ProgressListener")
    void stderrProgressListener_isProgressListener() {
        try (StderrProgressListener sut = new StderrProgressListener()) {
            assertThat(sut).isInstanceOf(ProgressListener.class);
        }
    }

    // ── 5. Implements AutoCloseable ─────────────────────────────────────

    @Test
    @DisplayName("StderrProgressListener implements AutoCloseable")
    void stderrProgressListener_isAutoCloseable() {
        try (StderrProgressListener sut = new StderrProgressListener()) {
            assertThat(sut).isInstanceOf(AutoCloseable.class);
        }
    }
}
