package com.srk.myutils.yd.core;

/**
 * Shared HTTP constants used across all external-boundary components.
 * Single source of truth for NFR-ANDROID-USER-AGENT (AC-12.2).
 */
final class HttpConstants {

    static final String ANDROID_USER_AGENT =
            "com.google.android.youtube/21.02.35 (Linux; U; Android 11) gzip";

    private HttpConstants() {
    }
}
