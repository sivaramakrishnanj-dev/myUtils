package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.DownloadResult;
import com.srk.myutils.yd.core.ErrorMapper;
import com.srk.myutils.yd.core.ErrorReport;
import com.srk.myutils.yd.core.YoutubeDownloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
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
        try {
            DownloadResult result = downloader.download(url);
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
