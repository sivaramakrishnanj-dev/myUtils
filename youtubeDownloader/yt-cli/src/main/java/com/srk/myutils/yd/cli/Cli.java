package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.AudioFormat;
import com.srk.myutils.yd.core.DownloadRequest;
import com.srk.myutils.yd.core.DownloadResult;
import com.srk.myutils.yd.core.ErrorMapper;
import com.srk.myutils.yd.core.ErrorReport;
import com.srk.myutils.yd.core.OutputConfig;
import com.srk.myutils.yd.core.ProgressListener;
import com.srk.myutils.yd.core.UrlParseException;
import com.srk.myutils.yd.core.YoutubeDownloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Picocli entrypoint for the youtube-downloader CLI.
 * M1 scope: URL positional parameter, {@code --debug}, {@code --quiet},
 * error-handling pipeline (T-1.12, AC-5.1..AC-5.5), orchestrator wiring (T-1.14).
 */
@Command(
        name = "youtube-downloader",
        mixinStandardHelpOptions = true,
        version = {"youtube-downloader 1.0.0"},
        description = "Download video, audio, transcript, or thumbnail from a YouTube URL."
)
public final class Cli implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cli.class);

    private final YoutubeDownloader downloader;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Parameters(index = "0", description = "YouTube URL")
    private String url;

    @Option(names = "--debug", description = "Enable verbose debug logging")
    private boolean debug;

    @Option(names = "--quiet", description = "Suppress progress output")
    private boolean quiet;

    @Option(names = "--audio-only", description = "Download audio only (no video)")
    private boolean audioOnly;

    @Option(names = "--video", description = "Include muxed MP4 output (US-1)")
    private Boolean video;

    @Option(names = "--audio-format", description = "Audio output format: m4a (default) or mp3 (requires ffmpeg)",
            defaultValue = "m4a")
    private String audioFormatStr;

    @Option(names = "--max-height", description = "Maximum video height; 0 = uncapped (default: ${DEFAULT-VALUE})",
            defaultValue = "1080")
    private int maxHeight;

    @Option(names = "--ffmpeg-location", description = "Path to ffmpeg binary (default: use PATH)")
    private String ffmpegLocation;

    @Option(names = "--output-dir", description = "Output directory (default: current directory)")
    private Path outputDir;

    @Option(names = "--output", description = "Output filename (extension auto-applied)")
    private Path outputPath;

    @Option(names = "--force", description = "Overwrite existing output files")
    private boolean force;

    @Option(names = "--transcript", description = "Enable transcript download (AC-6.1)")
    private boolean transcript;

    @Option(names = "--lang", description = "Caption language code (AC-8.2)")
    private String lang;

    @Option(names = "--no-asr", description = "Refuse ASR fallback for captions (AC-7.4)")
    private boolean noAsr;

    @Option(names = "--thumbnail", description = "Download the video thumbnail")
    private boolean thumbnail;

    /** Default constructor — uses production {@link YoutubeDownloader}. */
    public Cli() {
        this(YoutubeDownloader.create());
    }

    /** Package-private constructor for test injection. */
    Cli(YoutubeDownloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public Integer call() {
        ProgressListener listener = quiet ? ProgressListener.NO_OP : new StderrProgressListener();
        try {
            if (maxHeight < 0) {
                throw new UrlParseException("--max-height must be >= 0");
            }
            AudioFormat audioFormat = parseAudioFormat(audioFormatStr);
            if (audioFormat == AudioFormat.MP3 && !audioOnly) {
                LOGGER.warn("--audio-format mp3 implies --audio-only; treating as audio-only (AC-2.4)");
                audioOnly = true;
            }
            boolean effectiveVideo = computeEffectiveVideo();
            DownloadRequest request = new DownloadRequest(
                    url,
                    audioOnly,
                    audioFormat,
                    maxHeight,
                    Optional.ofNullable(ffmpegLocation),
                    transcript,
                    Optional.ofNullable(lang),
                    noAsr,
                    new OutputConfig(
                            Optional.ofNullable(outputPath),
                            Optional.ofNullable(outputDir),
                            force),
                    listener,
                    debug,
                    thumbnail,
                    effectiveVideo);
            DownloadResult result = downloader.download(request);
            LOGGER.info("Downloaded: videoId={} title={}",
                    result.videoId().value(), result.title());
            return 0;
        } catch (Throwable t) {
            PrintWriter err = spec.commandLine().getErr();
            ErrorReport report = ErrorMapper.map(t);
            LOGGER.error(report.message(), t);
            err.println(report.message());
            if (debug) {
                t.printStackTrace(err);
            }
            return report.exitCode();
        } finally {
            if (listener instanceof AutoCloseable ac) {
                try { ac.close(); } catch (Exception ignored) { }
            }
        }
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public static void main(String[] args) {
        configureLogging(args);
        int exitCode = new CommandLine(new Cli()).execute(args);
        System.exit(exitCode);
    }

    private static AudioFormat parseAudioFormat(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "m4a" -> AudioFormat.M4A;
            case "mp3" -> AudioFormat.MP3;
            default -> throw new UrlParseException(
                    "--audio-format must be 'm4a' or 'mp3', got: " + value);
        };
    }

    /**
     * Computes the effective {@code video} flag per 04-apis.md § 3.1.2:
     * default is {@code true} when neither {@code --audio-only} nor
     * {@code --transcript}/{@code --thumbnail} alone is given.
     * Explicit {@code --video} overrides the default — except when
     * {@code audioOnly} is true, which forces video=false (AC-2.5).
     *
     * <p>Must be called AFTER any --audio-format mp3 coercion (since mp3
     * implies audioOnly=true, which in turn forces effectiveVideo=false).
     */
    private boolean computeEffectiveVideo() {
        if (audioOnly) {
            if (Boolean.TRUE.equals(video)) {
                LOGGER.warn("--audio-only combined with --video; ignoring --video (AC-2.5)");
            }
            return false;
        }
        if (video != null) {
            return video;
        }
        if (transcript || thumbnail) {
            return false;
        }
        return true;
    }

    /**
     * Pre-scans args for {@code --debug} and sets the SLF4J SimpleLogger
     * default level to DEBUG before any logger is initialised (AC-10.5).
     */
    static void configureLogging(String[] args) {
        for (String arg : args) {
            if ("--debug".equals(arg)) {
                System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
                return;
            }
        }
    }
}
