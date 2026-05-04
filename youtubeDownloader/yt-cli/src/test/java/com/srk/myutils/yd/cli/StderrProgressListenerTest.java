package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.ProgressListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Characterization test for {@link StderrProgressListener} — happy-path only.
 */
class StderrProgressListenerTest {

    @Test
    void stderrProgressListener_implementsProgressListener() {
        try (StderrProgressListener listener = new StderrProgressListener()) {
            assertThat(listener).isInstanceOf(ProgressListener.class);
            assertThatCode(() -> listener.onProgress(256, 512))
                    .doesNotThrowAnyException();
        }
    }
}
