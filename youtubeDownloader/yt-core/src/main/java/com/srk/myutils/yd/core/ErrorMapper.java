package com.srk.myutils.yd.core;

/**
 * Translates exceptions into user-facing {@link ErrorReport} tuples (AC-5.1, AC-5.2, INV-11).
 *
 * <p>Every path into {@code TERMINATED} carries a {@link YoutubeDownloaderException} whose
 * {@link YoutubeDownloaderException#exitCode()} is the authoritative exit code. This mapper
 * adds the category prefix required by AC-5.1 and handles the defensive case of an unexpected
 * (non-domain) throwable.
 *
 * @see <a href="design/06-formal/cli-exit-codes.md">cli-exit-codes.md § 1–3</a>
 */
public final class ErrorMapper {

    private ErrorMapper() {}

    /**
     * Maps a throwable to an {@link ErrorReport}.
     *
     * <p>Domain exceptions produce {@code "Error: <category>: <message>"} per AC-5.1.
     * Unexpected exceptions produce {@code "Error: internal: <className>: <message>"} with exit code 1.
     */
    public static ErrorReport map(Throwable t) {
        if (t instanceof YoutubeDownloaderException yde) {
            return new ErrorReport(yde.exitCode(), "Error: " + categoryOf(yde) + ": " + yde.getMessage());
        }
        return new ErrorReport(1, "Error: internal: " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }

    @SuppressWarnings("ChainOfInstanceofChecks") // sealed hierarchy — exhaustive by design
    private static String categoryOf(YoutubeDownloaderException e) {
        if (e instanceof UrlParseException)           return "args";
        if (e instanceof NetworkException)            return "network";
        if (e instanceof InnerTubeParseException)     return "innertube";
        if (e instanceof VideoUnavailableException)   return "unavailable";
        if (e instanceof LiveStreamException)         return "live";
        if (e instanceof CipherRequiredException)     return "cipher";
        if (e instanceof NoMatchingFormatException)   return "format";
        if (e instanceof CaptionUnavailableException) return "captions";
        if (e instanceof OutputExistsException)       return "output";
        if (e instanceof FfmpegException)             return "ffmpeg";
        if (e instanceof FilesystemException)         return "filesystem";
        // Unreachable — sealed hierarchy covers all subtypes.
        throw new AssertionError("Unknown YoutubeDownloaderException subtype: " + e.getClass().getName());
    }
}
