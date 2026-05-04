package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.UrlParser;
import com.srk.myutils.yd.core.VideoId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * Picocli entrypoint for the youtube-downloader CLI.
 * M1 scope (T-1.11): URL positional parameter, {@code --debug}, {@code --quiet}.
 */
@Command(
        name = "youtube-downloader",
        mixinStandardHelpOptions = true,
        version = {"youtube-downloader 1.0.0"},
        description = "Download video, audio, transcript, or thumbnail from a YouTube URL."
)
public final class Cli implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cli.class);

    @Parameters(index = "0", description = "YouTube URL")
    private String url;

    @Option(names = "--debug", description = "Enable verbose debug logging")
    private boolean debug;

    @Option(names = "--quiet", description = "Suppress progress output")
    private boolean quiet;

    @Override
    public Integer call() {
        VideoId videoId = new UrlParser().parse(url);
        LOGGER.info("Parsed video id: {}", videoId.value());
        return 0;
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
