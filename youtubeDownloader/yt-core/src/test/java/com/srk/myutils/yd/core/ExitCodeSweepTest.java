package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * T-5.2 — Exit-code correctness sweep.
 *
 * <p>Verifies that every failure path through the {@link YoutubeDownloader} orchestrator
 * throws the correct exception subclass, and that {@link ErrorMapper} maps it to the
 * documented exit code per {@code cli-exit-codes.md § 3}.
 *
 * <p>Each test injects fake components that trigger a specific failure mode, invokes
 * {@code download()}, catches the exception, and asserts both the exception type and
 * the exit code via {@link ErrorMapper#map(Throwable)}.
 */
class ExitCodeSweepTest {

    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    // ── code 2: UrlParseException ──

    @Test
    @DisplayName("code 2: invalid URL → UrlParseException → exit 2")
    void invalidUrl_throwsUrlParseException_exitCode2() {
        YoutubeDownloader downloader = new YoutubeDownloader(
                new UrlParser(), new InnerTubeClient(fakeHttp(200, "{}")));

        Throwable t = catchThrowable(() -> downloader.download("not-a-url"));

        assertExitCode(t, UrlParseException.class, 2);
    }

    // ── code 10: NetworkException ──

    @Test
    @DisplayName("code 10: HTTP error from InnerTube → NetworkException → exit 10")
    void httpError_throwsNetworkException_exitCode10() {
        YoutubeDownloader downloader = new YoutubeDownloader(
                new UrlParser(), new InnerTubeClient(fakeHttp(503, "")));

        Throwable t = catchThrowable(() -> downloader.download(VALID_URL));

        assertExitCode(t, NetworkException.class, 10);
    }

    // ── code 11: InnerTubeParseException ──

    @Test
    @DisplayName("code 11: malformed JSON → InnerTubeParseException → exit 11")
    void malformedJson_throwsInnerTubeParseException_exitCode11() {
        YoutubeDownloader downloader = new YoutubeDownloader(
                new UrlParser(), new InnerTubeClient(fakeHttp(200, "not json")));

        Throwable t = catchThrowable(() -> downloader.download(VALID_URL));

        assertExitCode(t, InnerTubeParseException.class, 11);
    }

    // ── code 20: VideoUnavailableException ──

    @Test
    @DisplayName("code 20: UNPLAYABLE status → VideoUnavailableException → exit 20")
    void unplayable_throwsVideoUnavailableException_exitCode20() {
        String json = """
                {
                  "playabilityStatus": {"status": "UNPLAYABLE", "reason": "private"},
                  "videoDetails": {"videoId": "dQw4w9WgXcQ", "title": "T", "lengthSeconds": "60", "isLive": false}
                }
                """;
        YoutubeDownloader downloader = new YoutubeDownloader(
                new UrlParser(), new InnerTubeClient(fakeHttp(200, json)));

        Throwable t = catchThrowable(() -> downloader.download(VALID_URL));

        assertExitCode(t, VideoUnavailableException.class, 20);
    }

    // ── code 21: LiveStreamException ──

    @Test
    @DisplayName("code 21: isLive=true → LiveStreamException → exit 21")
    void liveStream_throwsLiveStreamException_exitCode21() {
        String json = """
                {
                  "playabilityStatus": {"status": "OK"},
                  "videoDetails": {"videoId": "dQw4w9WgXcQ", "title": "T", "lengthSeconds": "0", "isLive": true}
                }
                """;
        YoutubeDownloader downloader = new YoutubeDownloader(
                new UrlParser(), new InnerTubeClient(fakeHttp(200, json)));

        Throwable t = catchThrowable(() -> downloader.download(VALID_URL));

        assertExitCode(t, LiveStreamException.class, 21);
    }

    // ── code 22: CipherRequiredException ──

    @Test
    @DisplayName("code 22: all formats cipher-protected → CipherRequiredException → exit 22")
    void allCipher_throwsCipherRequiredException_exitCode22() {
        String json = """
                {
                  "playabilityStatus": {"status": "OK"},
                  "videoDetails": {"videoId": "dQw4w9WgXcQ", "title": "T", "lengthSeconds": "60", "isLive": false},
                  "streamingData": {
                    "adaptiveFormats": [
                      {"itag": 137, "mimeType": "video/mp4; codecs=\\"avc1.640028\\"", "bitrate": 4000000, "width": 1920, "height": 1080, "signatureCipher": "s=abc"},
                      {"itag": 140, "mimeType": "audio/mp4; codecs=\\"mp4a.40.2\\"", "bitrate": 128000, "signatureCipher": "s=def"}
                    ]
                  }
                }
                """;
        YoutubeDownloader downloader = new YoutubeDownloader(
                new UrlParser(), new InnerTubeClient(fakeHttp(200, json)),
                new FormatSelector(), StreamDownloader.create());

        Throwable t = catchThrowable(() -> downloader.download(audioOnlyRequest()));

        assertExitCode(t, CipherRequiredException.class, 22);
    }

    // ── code 30: NoMatchingFormatException ──

    @Test
    @DisplayName("code 30: no audio formats available → NoMatchingFormatException → exit 30")
    void noFormats_throwsNoMatchingFormatException_exitCode30() {
        String json = """
                {
                  "playabilityStatus": {"status": "OK"},
                  "videoDetails": {"videoId": "dQw4w9WgXcQ", "title": "T", "lengthSeconds": "60", "isLive": false},
                  "streamingData": {
                    "adaptiveFormats": [
                      {"itag": 137, "mimeType": "video/mp4; codecs=\\"avc1.640028\\"", "bitrate": 4000000, "url": "http://x", "width": 1920, "height": 2160}
                    ]
                  }
                }
                """;
        YoutubeDownloader downloader = new YoutubeDownloader(
                new UrlParser(), new InnerTubeClient(fakeHttp(200, json)),
                new FormatSelector(), StreamDownloader.create());

        Throwable t = catchThrowable(() -> downloader.download(audioOnlyRequest()));

        assertExitCode(t, NoMatchingFormatException.class, 30);
    }

    // ── code 40: CaptionUnavailableException ──

    @Test
    @DisplayName("code 40: no caption tracks → CaptionUnavailableException → exit 40")
    void noCaptions_throwsCaptionUnavailableException_exitCode40() {
        String json = """
                {
                  "playabilityStatus": {"status": "OK"},
                  "videoDetails": {"videoId": "dQw4w9WgXcQ", "title": "T", "lengthSeconds": "60", "isLive": false},
                  "streamingData": {
                    "adaptiveFormats": [
                      {"itag": 140, "mimeType": "audio/mp4; codecs=\\"mp4a.40.2\\"", "bitrate": 128000, "url": "http://audio", "contentLength": "1000"}
                    ]
                  },
                  "captions": {"playerCaptionsTracklistRenderer": {"captionTracks": []}}
                }
                """;
        YoutubeDownloader downloader = new YoutubeDownloader(
                new UrlParser(), new InnerTubeClient(fakeHttp(200, json)),
                new FormatSelector(), StreamDownloader.create());

        Throwable t = catchThrowable(() -> downloader.download(transcriptOnlyRequest()));

        assertExitCode(t, CaptionUnavailableException.class, 40);
    }

    // ── code 50: OutputExistsException ──

    @Test
    @DisplayName("code 50: OutputExistsException → exit 50")
    void outputExists_mapsToExitCode50() {
        OutputExistsException ex = new OutputExistsException("file '/tmp/out.mp4' already exists");
        ErrorReport report = ErrorMapper.map(ex);

        assertThat(report.exitCode()).isEqualTo(50);
        assertThat(report.message()).startsWith("Error: output: ");
    }

    // ── code 60: FfmpegException ──

    @Test
    @DisplayName("code 60: FfmpegException → exit 60")
    void ffmpegFailure_mapsToExitCode60() {
        FfmpegException ex = new FfmpegException("ffmpeg not found on PATH");
        ErrorReport report = ErrorMapper.map(ex);

        assertThat(report.exitCode()).isEqualTo(60);
        assertThat(report.message()).startsWith("Error: ffmpeg: ");
    }

    // ── code 70: FilesystemException ──

    @Test
    @DisplayName("code 70: FilesystemException → exit 70")
    void filesystemError_mapsToExitCode70() {
        FilesystemException ex = new FilesystemException("disk full", new java.io.IOException("no space"));
        ErrorReport report = ErrorMapper.map(ex);

        assertThat(report.exitCode()).isEqualTo(70);
        assertThat(report.message()).startsWith("Error: filesystem: ");
    }

    // ── Comprehensive: every exception type through ErrorMapper ──

    @Test
    @DisplayName("all 11 exception types map to their documented exit codes through ErrorMapper")
    void allExceptionTypes_mapToDocumentedExitCodes() {
        record Case(YoutubeDownloaderException ex, int expectedCode, String expectedCategory) {}

        var cases = java.util.List.of(
                new Case(new UrlParseException("bad"), 2, "args"),
                new Case(new NetworkException("fail"), 10, "network"),
                new Case(new InnerTubeParseException("parse"), 11, "innertube"),
                new Case(new VideoUnavailableException("private"), 20, "unavailable"),
                new Case(new LiveStreamException("live"), 21, "live"),
                new Case(new CipherRequiredException("cipher"), 22, "cipher"),
                new Case(new NoMatchingFormatException("none"), 30, "format"),
                new Case(new CaptionUnavailableException("no caps"), 40, "captions"),
                new Case(new OutputExistsException("exists"), 50, "output"),
                new Case(new FfmpegException("missing"), 60, "ffmpeg"),
                new Case(new FilesystemException("full"), 70, "filesystem")
        );

        for (var c : cases) {
            ErrorReport report = ErrorMapper.map(c.ex());
            assertThat(report.exitCode())
                    .as("exit code for %s", c.ex().getClass().getSimpleName())
                    .isEqualTo(c.expectedCode());
            assertThat(report.message())
                    .as("category prefix for %s", c.ex().getClass().getSimpleName())
                    .startsWith("Error: " + c.expectedCategory() + ": ");
        }
    }

    // ── Helpers ──

    private static OkHttpClient fakeHttp(int statusCode, String body) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(statusCode)
                        .message("OK")
                        .body(ResponseBody.create(body,
                                MediaType.get("application/json")))
                        .build())
                .build();
    }

    private static DownloadRequest audioOnlyRequest() {
        return new DownloadRequest(
                VALID_URL, true, AudioFormat.M4A, 0, Optional.empty(),
                false, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                ProgressListener.NO_OP, false, false, false);
    }

    private static DownloadRequest transcriptOnlyRequest() {
        return new DownloadRequest(
                VALID_URL, false, AudioFormat.M4A, 0, Optional.empty(),
                true, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.empty(), false),
                ProgressListener.NO_OP, false, false, false);
    }

    private static void assertExitCode(Throwable t, Class<? extends YoutubeDownloaderException> expectedType, int expectedCode) {
        assertThat(t).isInstanceOf(expectedType);
        YoutubeDownloaderException yde = (YoutubeDownloaderException) t;
        assertThat(yde.exitCode()).isEqualTo(expectedCode);
        ErrorReport report = ErrorMapper.map(yde);
        assertThat(report.exitCode()).isEqualTo(expectedCode);
    }
}
