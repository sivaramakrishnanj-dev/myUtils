package com.srk.myutils.yd.testutil;

import org.junit.jupiter.api.Test;

import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization test for {@link NoNetworkExtension}.
 * Verifies that the SecurityManager blocks socket connections during unit tests
 * and produces a clear message (AC-11.4).
 */
class NoNetworkExtensionTest {

    @Test
    void socketConnect_blockedWithClearMessage() {
        assertThatThrownBy(() -> new Socket("example.com", 80))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("example.com")
                .hasMessageContaining("network I/O is forbidden in unit tests");
    }
}
