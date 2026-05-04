package com.srk.myutils.yd.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Library entrypoint for youtubeDownloader (AC-9.1).
 *
 * <p>M1 scope: parses URL → fetches InnerTube player → extracts
 * {@link PlayerResponse} → checks playability → returns a stub
 * {@link DownloadResult} with metadata only (no media download).
 *
 * <p>Dependencies are constructor-injected for testability (AC-11.1).
 * Production callers use {@link #create()}.
 *
 * @see <a href="design/02-architecture.md">02-architecture.md § 1.2.4 DownloadOrchestrator</a>
 */
public final class YoutubeDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(YoutubeDownloader.class);

    private final UrlParser urlParser;
    private final InnerTubeClient innerTubeClient;

    /**
     * @param urlParser       parses raw URL to {@link VideoId}
     * @param innerTubeClient fetches InnerTube player response
     */
    public YoutubeDownloader(UrlParser urlParser, InnerTubeClient innerTubeClient) {
        this.urlParser = urlParser;
        this.innerTubeClient = innerTubeClient;
    }

    /** Factory with production defaults. */
    public static YoutubeDownloader create() {
        return new YoutubeDownloader(new UrlParser(), InnerTubeClient.create());
    }

    /**
     * Resolves a YouTube URL and returns download metadata.
     *
     * <p>Convenience overload that uses {@link ProgressListener#NO_OP}.
     *
     * @param url raw YouTube URL
     * @return download result with video metadata
     * @throws UrlParseException         if the URL is invalid (exit 2)
     * @throws NetworkException          on network failure (exit 10)
     * @throws InnerTubeParseException   on response parse error (exit 11)
     * @throws VideoUnavailableException if the video is unavailable (exit 20)
     * @throws LiveStreamException       if the video is live (exit 21)
     */
    public DownloadResult download(String url) {
        return download(url, ProgressListener.NO_OP);
    }

    /**
     * Resolves a YouTube URL and returns download metadata.
     *
     * <p>M1: parse URL, fetch InnerTube, extract player response, check
     * playability, return stub result. Media download (M2+) will forward
     * {@code listener} to {@code StreamDownloader}.
     *
     * @param url      raw YouTube URL
     * @param listener progress callback; use {@link ProgressListener#NO_OP} to suppress
     * @return download result with video metadata (no files in M1)
     * @throws UrlParseException         if the URL is invalid (exit 2)
     * @throws NetworkException          on network failure (exit 10)
     * @throws InnerTubeParseException   on response parse error (exit 11)
     * @throws VideoUnavailableException if the video is unavailable (exit 20)
     * @throws LiveStreamException       if the video is live (exit 21)
     */
    public DownloadResult download(String url, ProgressListener listener) {
        LOGGER.info("Starting download for URL: {}", url);

        VideoId videoId = urlParser.parse(url);
        LOGGER.info("Parsed video id: {}", videoId.value());

        InnerTubeResponse response = innerTubeClient.fetchPlayer(videoId);
        if (response.httpStatus() != 200) {
            throw new NetworkException(
                    "InnerTube returned HTTP " + response.httpStatus() + " for " + videoId.value());
        }

        PlayerResponse player = PlayerResponseExtractor.extract(response.body());
        PlayerResponseExtractor.checkPlayability(player);

        LOGGER.info("Metadata resolved: videoId={} title={}",
                videoId.value(), player.videoDetails().title());

        return new DownloadResult(
                videoId,
                player.videoDetails().title(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false
        );
    }
}
