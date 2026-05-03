package com.srk.myutils.yd.core;

/**
 * Root of the youtubeDownloader exception hierarchy (AC-9.4).
 *
 * <p>Each AC-5.2 failure category maps to exactly one sealed subtype.
 * Library callers catch by category; the CLI maps {@link #exitCode()} to
 * a POSIX exit code via {@code ExitCodeMapper} (T-1.10).
 *
 * <p>Unchecked (extends {@link RuntimeException}) — callers catch at the
 * top-level orchestration boundary, not at every intermediate call site.
 *
 * @see <a href="design/06-formal/cli-exit-codes.md">cli-exit-codes.md § 3</a>
 */
public abstract sealed class YoutubeDownloaderException extends RuntimeException
        permits UrlParseException, NetworkException, InnerTubeParseException,
                VideoUnavailableException, LiveStreamException, CipherRequiredException,
                NoMatchingFormatException, CaptionUnavailableException,
                OutputExistsException, FfmpegException, FilesystemException {

    protected YoutubeDownloaderException(String message) {
        super(message);
    }

    protected YoutubeDownloaderException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The POSIX exit code for this failure category (AC-5.2). */
    public abstract int exitCode();
}
