package com.srk.myutils.yd.core;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Immutable output of {@link YoutubeDownloader#download(String)}.
 *
 * <p>On successful return, {@code videoId} and {@code title} are always
 * populated. Optional path fields are present only when the corresponding
 * artifact was produced. On failure, the caller sees an exception, not a
 * {@code DownloadResult} with nothing in it.
 *
 * <p>M1 scope: only {@code videoId} and {@code title} are populated;
 * all path fields are empty. Media download arrives in M2+.
 *
 * @param videoId          always populated
 * @param title            from InnerTube; may differ from output filename after sanitization
 * @param videoPath        .mp4 (present when video mux succeeded)
 * @param audioPath        .m4a or .mp3 (present when audio-only download succeeded)
 * @param srtPath          .srt (present when transcript was produced)
 * @param txtPath          .txt (present when transcript was produced)
 * @param thumbnailPath    .jpg (present when thumbnail was downloaded)
 * @param usedAsrFallback  true if ASR was substituted for manual captions (AC-7.3)
 * @see <a href="design/03-data-model.md">03-data-model.md § 2.4</a>
 */
public record DownloadResult(
        VideoId videoId,
        String title,
        Optional<Path> videoPath,
        Optional<Path> audioPath,
        Optional<Path> srtPath,
        Optional<Path> txtPath,
        Optional<Path> thumbnailPath,
        boolean usedAsrFallback
) { }
