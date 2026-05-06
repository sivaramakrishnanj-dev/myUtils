package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpConstantsTest {

    @Test
    void androidUserAgent_isNonBlankAndContainsExpectedAppId() {
        assertThat(HttpConstants.ANDROID_USER_AGENT)
                .isNotBlank()
                .startsWith("com.google.android.youtube/");
    }
}
