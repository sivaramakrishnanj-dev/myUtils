package com.imagegen;

/**
 * Process exit codes. Stable contract - an agent branches on these instead of
 * parsing message text, so the numbers must not be reshuffled between versions.
 */
public enum ExitCode {
    /** Everything worked; stdout holds the result JSON. */
    OK(0),
    /** Bad or missing arguments. Fix the command line and retry. */
    USAGE(2),
    /** Missing/invalid API key or unreadable config. Not retryable as-is. */
    CONFIG(3),
    /** Transient API failure (429, 5xx, timeout). Safe to retry later. */
    API_RETRYABLE(4),
    /** Permanent API failure (bad request, safety block). Change the prompt/inputs. */
    API_PERMANENT(5),
    /** Local filesystem problem reading inputs or writing outputs. */
    IO(6);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
