package com.srk.myutils.yd.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Library entrypoint for youtubeDownloader (AC-9.1).
 *
 * <p>Parses URL → fetches InnerTube player → extracts
 * {@link PlayerResponse} → checks playability → selects formats →
 * downloads streams → writes output files → returns {@link DownloadResult}.
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
    private final FormatSelector formatSelector;
    private final StreamDownloader streamDownloader;

    /**
     * @param urlParser        parses raw URL to {@link VideoId}
     * @param innerTubeClient  fetches InnerTube player response
     * @param formatSelector   selects best format(s) from adaptive formats
     * @param streamDownloader downloads a stream to a {@code .part} file
     */
    public YoutubeDownloader(UrlParser urlParser,
                             InnerTubeClient innerTubeClient,
                             FormatSelector formatSelector,
                             StreamDownloader streamDownloader) {
        this.urlParser = urlParser;
        this.innerTubeClient = innerTubeClient;
        this.formatSelector = formatSelector;
        this.streamDownloader = streamDownloader;
    }

    /**
     * Backward-compatible constructor for M1 callers that only need
     * metadata resolution (no stream download).
     */
    public YoutubeDownloader(UrlParser urlParser, InnerTubeClient innerTubeClient) {
        this(urlParser, innerTubeClient, new FormatSelector(), StreamDownloader.create());
    }

    /** Factory with production defaults. */
    public static YoutubeDownloader create() {
        return new YoutubeDownloader(
                new UrlParser(),
                InnerTubeClient.create(),
                new FormatSelector(),
                StreamDownloader.create());
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
     * <p>Convenience overload — wraps the URL in a non-audio-only
     * {@link DownloadRequest} with default output config.
     *
     * @param url      raw YouTube URL
     * @param listener progress callback; use {@link ProgressListener#NO_OP} to suppress
     * @return download result with video metadata (no media files yet — video+audio path is M3)
     */
    public DownloadResult download(String url, ProgressListener listener) {
        return download(new DownloadRequest(
                url,
                false,
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                listener));
    }

    /**
     * Full download entrypoint supporting audio-only mode (AC-2.1, AC-2.3).
     *
     * <p>Flow:
     * <ol>
     *   <li>Parse URL → {@link VideoId}</li>
     *   <li>Fetch InnerTube player → raw bytes</li>
     *   <li>Extract → {@link PlayerResponse}, check playability</li>
     *   <li>If {@code audioOnly}: select audio format, download to {@code .part},
     *       move to final {@code .m4a}, return result with {@code audioPath}</li>
     *   <li>Otherwise: return metadata-only stub (video+audio mux is M3)</li>
     * </ol>
     *
     * @param request download request with all options
     * @return download result
     */
    public DownloadResult download(DownloadRequest request) {
        LOGGER.info("Starting download for URL: {}", request.url());

        VideoId videoId = urlParser.parse(request.url());
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

        if (request.audioOnly()) {
            return downloadAudioOnly(request, videoId, player);
        }

        // Non-audio-only stub — video+audio mux path is M3
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

    /**
     * Flow B — audio-only download (AC-2.1, AC-2.3).
     *
     * <p>Selects audio format only (no video), downloads to {@code .part},
     * moves to final {@code .m4a} path via {@link OutputWriter}.
     */
    private DownloadResult downloadAudioOnly(DownloadRequest request,
                                             VideoId videoId,
                                             PlayerResponse player) {
        FormatSelection selection = formatSelector.selectAudioOnly(player.adaptiveFormats());
        Format audioFormat = selection.audio();

        OutputWriter outputWriter = new OutputWriter(request.output());
        Path outputPath = outputWriter.deriveOutputPath(player.videoDetails(), "m4a");
        outputWriter.assertNotExistsOrForce(outputPath);

        long expectedBytes = audioFormat.contentLength().orElse(0L);
        outputWriter.assertSufficientFreeSpace(outputPath, expectedBytes);

        Path outputDir = outputPath.getParent() != null ? outputPath.getParent() : Path.of(".");

        try (DownloadContext ctx = DownloadContext.create(outputDir, videoId)) {
            Path audioPart = ctx.tempFile("audio.part");

            LOGGER.info("Downloading audio stream: itag={} url={}", audioFormat.itag(),
                    audioFormat.url().substring(0, Math.min(60, audioFormat.url().length())) + "...");

            streamDownloader.download(audioFormat.url(), audioPart, request.listener());

            Files.move(audioPart, outputPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Audio written to: {}", outputPath);

            ctx.markSuccess();
        } catch (IOException e) {
            throw new FilesystemException(
                    "Failed to move audio.part to " + outputPath + ": " + e.getMessage(), e);
        }

        return new DownloadResult(
                videoId,
                player.videoDetails().title(),
                Optional.empty(),
                Optional.of(outputPath),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false
        );
    }
}
