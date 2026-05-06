package com.srk.myutils.yd.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
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
    private final CaptionDownloader captionDownloader;
    private final ThumbnailDownloader thumbnailDownloader;

    /**
     * Full 7-arg constructor with all dependencies.
     */
    public YoutubeDownloader(UrlParser urlParser,
                             InnerTubeClient innerTubeClient,
                             FormatSelector formatSelector,
                             StreamDownloader streamDownloader,
                             Function<DownloadRequest, FfmpegMuxer> muxerFactory,
                             CaptionDownloader captionDownloader,
                             ThumbnailDownloader thumbnailDownloader) {
        this.urlParser = urlParser;
        this.innerTubeClient = innerTubeClient;
        this.formatSelector = formatSelector;
        this.streamDownloader = streamDownloader;
        this.muxerFactory = muxerFactory;
        this.captionDownloader = captionDownloader;
        this.thumbnailDownloader = thumbnailDownloader;
    }

    /**
     * 5-arg constructor (backward-compatible) — uses production defaults for
     * caption and thumbnail downloaders.
     */
    public YoutubeDownloader(UrlParser urlParser,
                             InnerTubeClient innerTubeClient,
                             FormatSelector formatSelector,
                             StreamDownloader streamDownloader,
                             Function<DownloadRequest, FfmpegMuxer> muxerFactory) {
        this(urlParser, innerTubeClient, formatSelector, streamDownloader, muxerFactory,
                CaptionDownloader.create(), ThumbnailDownloader.create());
    }

    /**
     * 4-arg constructor (backward-compatible).
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
                DEFAULT_MUXER_FACTORY,
                CaptionDownloader.create(),
                ThumbnailDownloader.create());
    }

    /**
     * Resolves a YouTube URL and returns download metadata.
     *
     * <p>Convenience overload that uses {@link ProgressListener#NO_OP}.
     */
    public DownloadResult download(String url) {
        return download(url, ProgressListener.NO_OP);
    }

    /**
     * Resolves a YouTube URL and returns download metadata.
     *
     * <p>Convenience overload — wraps the URL in a non-audio-only
     * {@link DownloadRequest} with default output config.
     */
    public DownloadResult download(String url, ProgressListener listener) {
        return download(new DownloadRequest(
                url,
                false,
                AudioFormat.M4A,
                DownloadRequest.DEFAULT_MAX_HEIGHT,
                Optional.empty(),
                false,
                Optional.empty(),
                false,
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                listener,
                false,
                false));
    }

    /**
     * Full download entrypoint supporting audio-only and video+audio modes,
     * with optional transcript and thumbnail flows.
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

        if (request.transcript() && !request.audioOnly()) {
            return downloadTranscriptOnly(request, videoId, player);
        }

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

        // Orthogonal flows: transcript + thumbnail (partial success on failure)
        TranscriptResult transcript = handleTranscript(request, player, outputPath);
        Optional<Path> thumbnailPath = handleThumbnail(request, player, outputPath);

        return new DownloadResult(
                videoId,
                player.videoDetails().title(),
                Optional.of(outputPath),
                Optional.empty(),
                transcript.srtPath(),
                transcript.txtPath(),
                thumbnailPath,
                transcript.usedAsrFallback()
        );
    }

    /**
     * Flow C — transcript-only (no media download, no ffmpeg). AC-13.5, state-machine Flow C.
     *
     * <p>Invoked when {@code --transcript} is set without {@code --audio-only}.
     * Produces only .srt + .txt (and optionally .jpg thumbnail).
     */
    private DownloadResult downloadTranscriptOnly(DownloadRequest request,
                                                  VideoId videoId,
                                                  PlayerResponse player) {
        LOGGER.info("Flow C: transcript-only (no media download)");

        Optional<LanguageCode> requestedLang = request.lang().map(LanguageCode::of);
        Optional<LanguageCode> audioLanguage = player.videoDetails().audioLanguage();

        // AC-6.4: selectCaption throws CaptionUnavailableException if no tracks — propagates
        CaptionSelection captionSelection = formatSelector.selectCaption(
                player.captionTracks(), requestedLang, audioLanguage, request.noAsr());

        String xml;
        try {
            xml = captionDownloader.download(captionSelection.track().baseUrl());
        } catch (NetworkException e) {
            // In Flow C, transcript IS the primary output — failure is not partial success
            throw new NetworkException(
                    "Caption download failed: " + e.getMessage(), e);
        }

        List<CaptionCue> cues = CaptionConverter.parseXml(xml);
        String srtContent = CaptionConverter.toSrt(cues);
        String txtContent = CaptionConverter.toTxt(cues);

        // Derive output paths from video title (no media file to base on)
        OutputWriter outputWriter = new OutputWriter(request.output());
        Path basePath = outputWriter.deriveOutputPath(player.videoDetails(), "srt");
        Path srtPath = basePath;
        Path txtPath = basePath.resolveSibling(
                stripExtension(basePath).getFileName() + ".txt");

        try {
            Files.writeString(srtPath, srtContent);
            LOGGER.info("SRT written to: {}", srtPath);
            Files.writeString(txtPath, txtContent);
            LOGGER.info("TXT written to: {}", txtPath);
        } catch (IOException e) {
            throw new FilesystemException(
                    "Failed to write transcript files: " + e.getMessage(), e);
        }

        // Optional thumbnail (still part of Flow C per state-machine)
        Optional<Path> thumbnailPath = handleThumbnail(request, player, srtPath);

        return new DownloadResult(
                videoId,
                player.videoDetails().title(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(srtPath),
                Optional.of(txtPath),
                thumbnailPath,
                captionSelection.usedAsrFallback()
        );
    }

    /**
     * Flow B — audio-only download (AC-2.1, AC-2.3).
     * Flow B' — audio-only + MP3 transcode (AC-2.4).
     */
    private DownloadResult downloadAudioOnly(DownloadRequest request,
                                             VideoId videoId,
                                             PlayerResponse player) {
        FormatSelection selection = formatSelector.selectAudioOnly(player.adaptiveFormats());
        Format audioFmt = selection.audio();

        String extension = request.audioFormat() == AudioFormat.MP3 ? "mp3" : "m4a";
        OutputWriter outputWriter = new OutputWriter(request.output());
        Path outputPath = outputWriter.deriveOutputPath(player.videoDetails(), extension);
        outputWriter.assertNotExistsOrForce(outputPath);

        long expectedBytes = audioFmt.contentLength().orElse(0L);
        outputWriter.assertSufficientFreeSpace(outputPath, expectedBytes);

        Path outputDir = outputPath.getParent() != null ? outputPath.getParent() : Path.of(".");

        if (request.audioFormat() == AudioFormat.MP3) {
            FfmpegMuxer muxer = muxerFactory.apply(request);
            muxer.probeVersion();

            try (DownloadContext ctx = DownloadContext.create(outputDir, videoId)) {
                Path audioPart = ctx.tempFile("audio.part");

                LOGGER.info("Downloading audio stream: itag={} {}bps", audioFmt.itag(), audioFmt.bitrate());
                streamDownloader.download(audioFmt.url(), audioPart, request.listener());

                LOGGER.info("Transcoding audio → MP3: {}", outputPath);
                muxer.transcodeMp3(audioPart, outputPath, request.debug());

                Files.delete(audioPart);
                LOGGER.info("Audio written to: {}", outputPath);
                ctx.markSuccess();
            } catch (IOException e) {
                throw new FilesystemException(
                        "Failed to delete audio.part after transcode: " + e.getMessage(), e);
            }
        } else {
            try (DownloadContext ctx = DownloadContext.create(outputDir, videoId)) {
                Path audioPart = ctx.tempFile("audio.part");

                LOGGER.info("Downloading audio stream: itag={} url={}", audioFmt.itag(),
                        audioFmt.url().substring(0, Math.min(60, audioFmt.url().length())) + "...");

                streamDownloader.download(audioFmt.url(), audioPart, request.listener());

                Files.move(audioPart, outputPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Audio written to: {}", outputPath);

                ctx.markSuccess();
            } catch (IOException e) {
                throw new FilesystemException(
                        "Failed to move audio.part to " + outputPath + ": " + e.getMessage(), e);
            }
        }

        // Orthogonal flows: transcript + thumbnail (partial success on failure)
        TranscriptResult transcript = handleTranscript(request, player, outputPath);
        Optional<Path> thumbnailPath = handleThumbnail(request, player, outputPath);

        return new DownloadResult(
                videoId,
                player.videoDetails().title(),
                Optional.empty(),
                Optional.of(outputPath),
                transcript.srtPath(),
                transcript.txtPath(),
                thumbnailPath,
                transcript.usedAsrFallback()
        );
    }

    /**
     * Handles the optional transcript flow: select caption → download XML →
     * convert to SRT + TXT → write files. Returns empty paths on failure (WARN, partial success).
     *
     * <p>CaptionUnavailableException from selectCaption propagates (AC-6.4: exit 40).
     * NetworkException during download is caught as partial success (02-arch § 6).
     */
    private TranscriptResult handleTranscript(DownloadRequest request,
                                              PlayerResponse player,
                                              Path mediaOutputPath) {
        if (!request.transcript()) {
            return TranscriptResult.EMPTY;
        }

        Optional<LanguageCode> requestedLang = request.lang().map(LanguageCode::of);
        Optional<LanguageCode> audioLanguage = player.videoDetails().audioLanguage();

        // AC-6.4: selectCaption throws CaptionUnavailableException if no tracks — propagates
        CaptionSelection captionSelection = formatSelector.selectCaption(
                player.captionTracks(), requestedLang, audioLanguage, request.noAsr());

        String xml;
        try {
            xml = captionDownloader.download(captionSelection.track().baseUrl());
        } catch (NetworkException e) {
            LOGGER.warn("Caption download failed (partial success): {}", e.getMessage());
            return new TranscriptResult(Optional.empty(), Optional.empty(),
                    captionSelection.usedAsrFallback());
        }

        List<CaptionCue> cues = CaptionConverter.parseXml(xml);
        String srtContent = CaptionConverter.toSrt(cues);
        String txtContent = CaptionConverter.toTxt(cues);

        // Derive .srt and .txt paths from the media output path
        Path basePath = stripExtension(mediaOutputPath);
        Path srtPath = basePath.resolveSibling(basePath.getFileName() + ".srt");
        Path txtPath = basePath.resolveSibling(basePath.getFileName() + ".txt");

        try {
            Files.writeString(srtPath, srtContent);
            LOGGER.info("SRT written to: {}", srtPath);
            Files.writeString(txtPath, txtContent);
            LOGGER.info("TXT written to: {}", txtPath);
        } catch (IOException e) {
            LOGGER.warn("Failed to write transcript files (partial success): {}", e.getMessage());
            return new TranscriptResult(Optional.empty(), Optional.empty(),
                    captionSelection.usedAsrFallback());
        }

        return new TranscriptResult(Optional.of(srtPath), Optional.of(txtPath),
                captionSelection.usedAsrFallback());
    }

    /**
     * Handles the optional thumbnail flow: download highest-res thumbnail to .jpg.
     * Returns empty on failure (WARN, partial success per 02-arch § 6).
     */
    private Optional<Path> handleThumbnail(DownloadRequest request,
                                           PlayerResponse player,
                                           Path mediaOutputPath) {
        if (!request.thumbnail()) {
            return Optional.empty();
        }

        List<ThumbnailUrl> thumbnails = player.thumbnails();
        if (thumbnails == null || thumbnails.isEmpty()) {
            LOGGER.warn("No thumbnails available in player response");
            return Optional.empty();
        }

        Path basePath = stripExtension(mediaOutputPath);
        Path thumbnailPath = basePath.resolveSibling(basePath.getFileName() + ".jpg");

        try {
            thumbnailDownloader.download(thumbnails, thumbnailPath);
            return Optional.of(thumbnailPath);
        } catch (NetworkException e) {
            LOGGER.warn("Thumbnail download failed (partial success): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Strips the file extension from a path, returning the base path. */
    private static Path stripExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return path.resolveSibling(fileName.substring(0, dot));
        }
        return path;
    }

    /**
     * Internal result holder for the transcript flow.
     */
    private record TranscriptResult(Optional<Path> srtPath, Optional<Path> txtPath,
                                    boolean usedAsrFallback) {
        static final TranscriptResult EMPTY = new TranscriptResult(
                Optional.empty(), Optional.empty(), false);
    }
}
