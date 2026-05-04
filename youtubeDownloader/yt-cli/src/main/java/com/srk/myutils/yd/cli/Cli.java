package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.ErrorMapper;
import com.srk.myutils.yd.core.ErrorReport;
import com.srk.myutils.yd.core.UrlParser;
import com.srk.myutils.yd.core.VideoId;
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
 * error-handling pipeline (T-1.12, AC-5.1..AC-5.5).
 */
@Command(
        name = "youtube-downloader",
        mixinStandardHelpOptions = true,
        version = {"youtube-downloader 1.0.0"},
        description = "Download video, audio, transcript, or thumbnail from a YouTube URL."
)
public final class Cli implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cli.class);

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Parameters(index = "0", description = "YouTube URL")
    private String url;

    @Option(names = "--debug", description = "Enable verbose debug logging")
    private boolean debug;

    @Option(names = "--quiet", description = "Suppress progress output")
    private boolean quiet;

    @Override
    public Integer call() {
        try {
            VideoId videoId = new UrlParser().parse(url);
            LOGGER.info("Parsed video id: {}", videoId.value());
            // T-1.14 adds: YoutubeDownloader.download(videoId, config)
            return 0;
        } catch (Throwable t) {
            PrintWriter err = spec.commandLine().getErr();
            ErrorReport report = ErrorMapper.map(t);
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
        int exitCode = new CommandLine(new Cli()).execute(args);
        System.exit(exitCode);
    }
}
