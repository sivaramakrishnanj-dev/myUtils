package com.srk.myutils.yd.core;

/**
 * Immutable request object for {@link YoutubeDownloader#download(DownloadRequest)}.
 *
 * <p>Encapsulates all user-facing options for a single download invocation:
 * the target URL, whether audio-only mode is active (AC-2.1), output
 * configuration, and a progress listener.
 *
 * @param url        raw YouTube URL (required)
 * @param audioOnly  {@code true} for {@code --audio-only} mode (AC-2.1)
 * @param output     output file placement and overwrite config
 * @param listener   progress callback; use {@link ProgressListener#NO_OP} to suppress
 */
public record DownloadRequest(
        String url,
        boolean audioOnly,
        OutputConfig output,
        ProgressListener listener
) {

    /** Convenience: audio-only request with default output config and no progress. */
    public static DownloadRequest audioOnly(String url, OutputConfig output) {
        return new DownloadRequest(url, true, output, ProgressListener.NO_OP);
    }
}
