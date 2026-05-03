package com.srk.myutils.yd.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Picocli entrypoint for the youtube-downloader CLI.
 * M0 scope: {@code --version} and {@code --help} only.
 */
@Command(
        name = "youtube-downloader",
        mixinStandardHelpOptions = true,
        version = {"youtube-downloader 1.0.0"},
        description = "Download video, audio, transcript, or thumbnail from a YouTube URL."
)
public final class Cli implements Callable<Integer> {

    @Override
    public Integer call() {
        // No flags parsed yet (M0). Print help and exit 0.
        new CommandLine(this).usage(System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Cli()).execute(args);
        System.exit(exitCode);
    }
}
