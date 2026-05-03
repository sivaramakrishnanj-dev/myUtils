package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link UrlParseException} — ensures the exception contract
 * (message propagation, RuntimeException inheritance) is correct.
 */
class UrlParseExceptionTest {

    @Test
    void constructor_givenMessage_getMessageReturnsIt() {
        var ex = new UrlParseException("bad id");
        assertThat(ex.getMessage()).isEqualTo("bad id");
    }

    @Test
    void isRuntimeException() {
        assertThat(new UrlParseException("x")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void isNotCheckedException() {
        assertThat(Exception.class).isAssignableFrom(UrlParseException.class);
        // Verify it's unchecked — RuntimeException, not just Exception
        assertThat(RuntimeException.class).isAssignableFrom(UrlParseException.class);
    }
}
