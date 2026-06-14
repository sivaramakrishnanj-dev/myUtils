package com.srk.myutils.yd.core;

/**
 * Shared HTTP constants used across all external-boundary components.
 * Single source of truth for NFR-ANDROID-USER-AGENT (AC-12.2).
 */
final class HttpConstants {

    static final String ANDROID_USER_AGENT =
            "com.google.android.youtube/21.02.35 (Linux; U; Android 11) gzip";

    /** ANDROID_VR client UA — yt-dlp INNERTUBE_CLIENTS['android_vr'] (SABR-bypass fallback). */
    static final String ANDROID_VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.65.10 "
                    + "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip";

    /**
     * IOS client UA — yt-dlp INNERTUBE_CLIENTS['ios']. Primary client: returns
     * direct CDN URLs without a PO Token, including for Shorts that ANDROID_VR
     * and TVHTML5 reject with LOGIN_REQUIRED ("confirm you're not a bot").
     */
    static final String IOS_USER_AGENT =
            "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)";

    private HttpConstants() {
    }
}
