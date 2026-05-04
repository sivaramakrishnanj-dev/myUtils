package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link ProgressReporter} — happy-path only.
 *
 * <p>Uses a real {@link ScheduledExecutorService} but captures output to a
 * {@link StringWriter} so no stderr side-effects occur. Verifies that after
 * progress events and close, the sink contains a rendered progress line.
 */
class ProgressReporterTest {

    @Test
    void onProgress_givenBytesAndClose_rendersProgressLine() throws Exception {
        StringWriter buf = new StringWriter();
        PrintWriter sink = new PrintWriter(buf, true);

        // Non-TTY mode: newline-separated output, 1000ms cadence.
        // Use a short-interval scheduler so the test doesn't wait 1s.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "test-progress");
            t.setDaemon(true);
            return t;
        });

        try (ProgressReporter reporter = new ProgressReporter(sink, false, scheduler)) {
            reporter.onProgress(256_000, 512_000);
            // Give the scheduler time to fire at least once.
            Thread.sleep(1200);
        }

        String output = buf.toString();
        assertThat(output).contains("50%");
        assertThat(output).contains("/");
    }
}
