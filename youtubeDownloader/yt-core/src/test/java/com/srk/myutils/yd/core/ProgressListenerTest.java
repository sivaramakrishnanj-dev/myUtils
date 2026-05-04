package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Characterization test for {@link ProgressListener} — happy-path only.
 */
class ProgressListenerTest {

    @Test
    void onProgress_givenLambdaImplementation_receivesValues() {
        AtomicLong capturedBytes = new AtomicLong();
        AtomicLong capturedTotal = new AtomicLong();

        ProgressListener listener = (bytesWritten, totalBytes) -> {
            capturedBytes.set(bytesWritten);
            capturedTotal.set(totalBytes);
        };

        listener.onProgress(512, 1024);

        assertThat(capturedBytes.get()).isEqualTo(512);
        assertThat(capturedTotal.get()).isEqualTo(1024);
    }

    @Test
    void noOp_doesNotThrow() {
        assertThatCode(() -> ProgressListener.NO_OP.onProgress(100, 200))
                .doesNotThrowAnyException();
    }
}
