package com.srk.myutils.yd.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Function;

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

    /** Default factory: constructs FfmpegMuxer from request's ffmpegLocation. */
    private static final Function<DownloadRequest, FfmpegMuxer> DEFAULT_MUXER_FACTORY =
            req -> req.ffmpegLocation().map(FfmpegMuxer::new).orElseGet(FfmpegMuxer::new);

    private final UrlParser urlParser;
    private final InnerTubeClient innerTubeClient;
    private final FormatSelector formatSelector;
    private final StreamDownloader streamDownloader;
    private final Function<DownloadRequest, FfmpegMuxer> muxerFactory;

    /**
     * Full constructor with all dependencies including muxer factory.
     */
    public YoutubeDownloader(UrlParser urlParser,
                             InnerTubeClient innerTubeClient,
                             FormatSelector formatSelector,
                             StreamDownloader streamDownloader,
                             Function<DownloadRequest, FfmpegMuxer> muxerFactory) {
        this.urlParser = urlParser;
        this.innerTubeClient = innerTubeClient;
        this.formatSelector = formatSelector;
        this.streamDownloader = streamDownloader;
        this.muxerFactory = muxerFactory;
    }

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
        this(urlParser, innerTubeClient, formatSelector, streamDownloader, DEFAULT_MUXER_FACTORY);
    }

    /**
     * Backward-compatible constructor for M1 callers that only need
     * metadata resolution (no stream download).
     */
    public YoutubeDownloader(UrlParser urlParser, InnerTubeClient innerTubeClient) {
        this(urlParser, innerTubeClient, new FormatSelector(), StreamDownloader.create(), DEFAULT_MUXER_FACTORY);
    }

    /** Factory with production defaults. */
    public static YoutubeDownloader create() {
        return new YoutubeDownloader(
                new UrlParser(),
                InnerTubeClient.create(),
                new FormatSelector(),
                StreamDownloader.create(),
                DEFAULT_MUXER_FACTORY);
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
                DownloadRequest.DEFAULT_MAX_HEIGHT,
                Optional.empty(),
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                listener,
                false));
    }

    /**
     * Full download entrypoint supporting audio-only and video+audio modes.
     *
     * <p>Flow A (video+audio, AC-1.6):
     * <ol>
     *   <li>Parse URL → {@link VideoId}</li>
     *   <li>Fetch InnerTube player → raw bytes</li>
     *   <li>Extract → {@link PlayerResponse}, check playability</li>
     *   <li>Select video + audio formats</li>
     *   <li>Probe ffmpeg version (AC-13.1)</li>
     *   <li>Create temp dir, download video + audio to {@code .part} files</li>
     *   <li>Derive output path, check overwrite + disk space</li>
     *   <li>Mux via ffmpeg → {@code .mp4}</li>
     *   <li>Mark success, clean temp dir, return result</li>
     * </ol>
     *
     * <p>Flow B (audio-only, AC-2.1, AC-2.3):
     * <ol>
     *   <li>Parse URL → {@link VideoId}</li>
     *   <li>Fetch InnerTube player → raw bytes</li>
     *   <li>Extract → {@link PlayerResponse}, check playability</li>
     *   <li>Select audio format, download to {@code .part}, move to {@code .m4a}</li>
     * </ol>
     *
     * @param request download request with all options
     * @return download result
     */
    public DownloadResult download(DownloadRequest request) {
        LOGGER.info("Starting download for URL: {}", request.url());
        request.ffmpegLocation().ifPresent(loc ->
                LOGGER.info("Using custom ffmpeg location: {}", loc));

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

        return downloadVideoAudio(request, videoId, player);
    }

    /**
     * Flow A — video + audio download + mux → .mp4 (AC-1.6, state-machine flow A).
     */
    private DownloadResult downloadVideoAudio(DownloadRequest request,
                                              VideoId videoId,
                                              PlayerResponse player) {
        FormatSelection selection = formatSelector.select(
                player.adaptiveFormats(), request.maxHeight());
        Format videoFormat = selection.video();
        Format audioFormat = selection.audio();

        // AC-13.1: probe ffmpeg before downloading (only when mux is needed)
        FfmpegMuxer muxer = muxerFactory.apply(request);
        muxer.probeVersion();

        OutputWriter outputWriter = new OutputWriter(request.output());
        Path outputPath = outputWriter.deriveOutputPath(player.videoDetails(), "mp4");
        outputWriter.assertNotExistsOrForce(outputPath);

        long expectedBytes = videoFormat.contentLength().orElse(0L)
                + audioFormat.contentLength().orElse(0L);
        outputWriter.assertSufficientFreeSpace(outputPath, expectedBytes);

        Path outputDir = outputPath.getParent() != null ? outputPath.getParent() : Path.of(".");

        try (DownloadContext ctx = DownloadContext.create(outputDir, videoId)) {
            Path videoPart = ctx.tempFile("video.part");
            Path audioPart = ctx.tempFile("audio.part");

            LOGGER.info("Downloading video stream: itag={} {}p", videoFormat.itag(),
                    videoFormat.height().orElse(0));
            streamDownloader.download(videoFormat.url(), videoPart, request.listener());

            LOGGER.info("Downloading audio stream: itag={} {}bps", audioFormat.itag(),
                    audioFormat.bitrate());
            streamDownloader.download(audioFormat.url(), audioPart, request.listener());

            LOGGER.info("Muxing video+audio → {}", outputPath);
            muxer.mux(videoPart, audioPart, outputPath, request.debug());

            LOGGER.info("Video written to: {}", outputPath);
            ctx.markSuccess();
        }

        return new DownloadResult(
                videoId,
                player.videoDetails().title(),
                Optional.of(outputPath),
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
