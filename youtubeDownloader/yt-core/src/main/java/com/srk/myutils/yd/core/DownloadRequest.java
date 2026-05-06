package com.srk.myutils.yd.core;

import java.util.Optional;

/**
 * Immutable request object for {@link YoutubeDownloader#download(DownloadRequest)}.
 *
 * <p>Encapsulates all user-facing options for a single download invocation:
 * the target URL, whether audio-only mode is active (AC-2.1), audio format
 * (AC-2.3, AC-2.4), maximum video height (AC-1.3), ffmpeg binary location
 * (AC-13.2), transcript flags (AC-6.1, AC-7.4, AC-8.2), output configuration,
 * a progress listener, and the debug flag (AC-5.4).
 *
 * @param url              raw YouTube URL (required)
 * @param audioOnly        {@code true} for {@code --audio-only} mode (AC-2.1)
 * @param audioFormat      audio output format; M4A (default) or MP3 (AC-2.3, AC-2.4)
 * @param maxHeight        maximum video height; 0 = uncapped (AC-1.3)
 * @param ffmpegLocation   path to ffmpeg binary; empty = use system PATH (AC-13.2)
 * @param transcript       {@code true} to enable transcript download (AC-6.1)
 * @param lang             language code for caption selection; empty = default chain (AC-8.2)
 * @param noAsr            {@code true} to refuse ASR fallback (AC-7.4)
 * @param output           output file placement and overwrite config
 * @param listener         progress callback; use {@link ProgressListener#NO_OP} to suppress
 * @param debug            {@code true} to enable debug logging and ffmpeg verbose output (AC-5.4)
 * @param thumbnail        {@code true} to download the video thumbnail
 * @param video            {@code true} to include muxed MP4 output (US-1, 04-apis.md § 3.1.2)
 */
public record DownloadRequest(
        String url,
        boolean audioOnly,
        AudioFormat audioFormat,
        int maxHeight,
        Optional<String> ffmpegLocation,
        boolean transcript,
        Optional<String> lang,
        boolean noAsr,
        OutputConfig output,
        ProgressListener listener,
        boolean debug,
        boolean thumbnail,
        boolean video
) {

    /** Default maximum video height per AC-1.3: 1080p unless overridden. */
    public static final int DEFAULT_MAX_HEIGHT = 1080;

    /** Belt-and-braces: reject negative maxHeight regardless of caller. */
    public DownloadRequest {
        if (maxHeight < 0) {
            throw new IllegalArgumentException("maxHeight must be >= 0, was: " + maxHeight);
        }
    }

    /** Convenience: audio-only request with default output config and no progress. */
    public static DownloadRequest audioOnly(String url, OutputConfig output) {
        return new DownloadRequest(url, true, AudioFormat.M4A, 0, Optional.empty(),
                false, Optional.empty(), false, output, ProgressListener.NO_OP, false, false, false);
    }
}
