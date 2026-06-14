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

    @Test
    void androidVrUserAgent_isNonBlankAndContainsExpectedAppId() {
        assertThat(HttpConstants.ANDROID_VR_USER_AGENT)
                .isNotBlank()
                .startsWith("com.google.android.apps.youtube.vr.oculus/");
    }

    @Test
    void iosUserAgent_isNonBlankAndContainsExpectedAppId() {
        assertThat(HttpConstants.IOS_USER_AGENT)
                .isNotBlank()
                .startsWith("com.google.ios.youtube/");
    }
}
