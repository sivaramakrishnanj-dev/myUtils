package com.srk.myutils.yd.core;

/**
 * The result of mapping a {@link Throwable} to a user-facing error output (AC-5.1, AC-5.2).
 *
 * @param exitCode the POSIX exit code per {@code cli-exit-codes.md}
 * @param message  the single-line stderr message per AC-5.1
 */
public record ErrorReport(int exitCode, String message) {}
