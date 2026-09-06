package com.imagegen;

/**
 * Human-facing progress and warnings. Always stderr - stdout is reserved for the
 * result document so a calling agent can parse it without filtering.
 */
public final class Log {

    private static boolean quiet;

    private Log() {
    }

    public static void setQuiet(boolean value) {
        quiet = value;
    }

    public static void info(String message) {
        if (!quiet) {
            System.err.println("[imagegen] " + message);
        }
    }

    public static void warn(String message) {
        System.err.println("[imagegen] WARN " + message);
    }

    public static void error(String message) {
        System.err.println("[imagegen] ERROR " + message);
    }
}
