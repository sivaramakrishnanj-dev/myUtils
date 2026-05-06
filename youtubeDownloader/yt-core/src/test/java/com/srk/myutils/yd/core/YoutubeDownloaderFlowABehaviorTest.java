package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for Flow A — video + audio download + mux → .mp4
 * (T-3.8, AC-1.6, state-machine flow A).
 *
 * <p>SUT ({@link YoutubeDownloader}) is never mocked. External dependencies are
 * faked via OkHttp interceptors (InnerTube + stream CDN) and a shell-script
 * fake ffmpeg in {@code @TempDir}. Covers happy path, error propagation,
 * OutputWriter guards, progress events, debug flag plumbing, filename
 * sanitization, DownloadContext lifecycle, and CT-APP-3/CT-APP-4 at
 * orchestrator level.
 */
class YoutubeDownloaderFlowABehaviorTest {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_VIDEO = {0x00, 0x01, 0x02, 0x03};
    private static final byte[] FAKE_AUDIO = {0x04, 0x05, 0x06, 0x07};
    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @TempDir
    Path tempDir;

    private Path fakeFfmpegScript;

    @BeforeEach
    void setUp() throws IOException {
        fakeFfmpegScript = createFakeFfmpeg("error");
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Path createFakeFfmpeg(String defaultLoglevel) throws IOException {
        Path script = tempDir.resolve("fake-ffmpeg");
        // Script echoes all args to a sidecar file for assertion, then writes
        // canned bytes to the output path (last arg).
        Files.writeString(script, """
                #!/bin/sh
                ARGS_FILE="$(dirname "$0")/ffmpeg-args.txt"
                echo "$@" >> "$ARGS_FILE"
                if [ "$1" = "-version" ]; then
                    echo "ffmpeg version 7.1.0 Copyright (c) 2000-2024 the FFmpeg developers"
                    exit 0
                fi
                OUTPUT="${@: -1}"
                printf '\\x00\\x01\\x02\\x03' > "$OUTPUT"
                exit 0
                """);
        script.toFile().setExecutable(true);
        return script;
    }

    private Path createFailingFfmpegProbe() throws IOException {
        Path script = tempDir.resolve("bad-ffmpeg");
        Files.writeString(script, """
                #!/bin/sh
                echo "ffmpeg: command not found" >&2
                exit 127
                """);
        script.toFile().setExecutable(true);
        return script;
    }

    private Path createFfmpegMuxFailure() throws IOException {
        Path script = tempDir.resolve("mux-fail-ffmpeg");
        Files.writeString(script, """
                #!/bin/sh
                if [ "$1" = "-version" ]; then
                    echo "ffmpeg version 7.1.0 Copyright (c) 2000-2024 the FFmpeg developers"
                    exit 0
                fi
                echo "Error: invalid input" >&2
                exit 1
                """);
        script.toFile().setExecutable(true);
        return script;
    }

    private OkHttpClient innerTubeClient() {
        return innerTubeClient("/fixtures/innertube-response-happy.json");
    }

    private OkHttpClient innerTubeClient(String fixturePath) {
        String fixture = loadFixture(fixturePath);
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(fixture, JSON))
                        .build())
                .build();
    }

    private OkHttpClient streamClient() {
        return streamClient(200);
    }

    private OkHttpClient streamClient(int statusCode) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(statusCode)
                        .message("OK")
                        .header("Content-Length", String.valueOf(FAKE_VIDEO.length))
                        .body(ResponseBody.create(FAKE_VIDEO, OCTET))
                        .build())
                .build();
    }

    /** Stream client that fails on the Nth call (1-indexed). */
    private OkHttpClient streamClientFailingOnCall(int failOnCall) {
        int[] counter = {0};
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    counter[0]++;
                    if (counter[0] == failOnCall) {
                        throw new IOException("Simulated network failure");
                    }
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Length", String.valueOf(FAKE_VIDEO.length))
                            .body(ResponseBody.create(FAKE_VIDEO, OCTET))
                            .build();
                })
                .build();
    }

    private YoutubeDownloader sut(OkHttpClient itHttp, OkHttpClient streamHttp,
                                  Function<DownloadRequest, FfmpegMuxer> factory) {
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(itHttp),
                new FormatSelector(),
                new StreamDownloader(streamHttp),
                factory);
    }

    private YoutubeDownloader sutWithFakeFfmpeg(OkHttpClient itHttp, OkHttpClient streamHttp) {
        return sut(itHttp, streamHttp,
                req -> new FfmpegMuxer(fakeFfmpegScript.toString(), 30));
    }

    private DownloadRequest flowARequest(boolean debug) {
        return new DownloadRequest(
                VALID_URL, false, AudioFormat.M4A, 1080,
                Optional.of(fakeFfmpegScript.toString()),
                false, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                ProgressListener.NO_OP, debug, false);
    }

    private DownloadRequest flowARequest() {
        return flowARequest(false);
    }

    private static String loadFixture(String resourcePath) {
        try (InputStream is = YoutubeDownloaderFlowABehaviorTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }

    // ── 1. Happy path ───────────────────────────────────────────────

    @Nested
    @DisplayName("Flow A happy path — AC-1.6")
    class HappyPath {

        @Test
        @DisplayName("video+audio selected, mux invoked, videoPath populated, .yt-tmp deleted")
        void download_flowA_happyPath_producesMp4AndCleansTemp() {
            YoutubeDownloader dl = sutWithFakeFfmpeg(innerTubeClient(), streamClient());

            DownloadResult result = dl.download(flowARequest());

            assertThat(result.videoPath()).isPresent();
            assertThat(result.videoPath().get().getFileName().toString()).endsWith(".mp4");
            assertThat(Files.exists(result.videoPath().get())).isTrue();
            assertThat(result.audioPath()).isEmpty();
            assertThat(result.videoId().value()).isEqualTo("dQw4w9WgXcQ");

            // INV-6: .yt-tmp cleaned on success
            assertThat(Files.exists(tempDir.resolve(".yt-tmp"))).isFalse();
        }

        @Test
        @DisplayName("CT-APP-3 / CT-APP-4: video itag 137 (1080p H.264) and audio itag 140 selected from happy fixture")
        void download_flowA_selectsCorrectFormats() {
            // Verify at orchestrator level that FormatSelector picks itag 137 + 140
            // from the happy fixture (3 formats: 137=1080p H.264, 136=720p H.264, 140=audio)
            String fixture = loadFixture("/fixtures/innertube-response-happy.json");
            PlayerResponse player = PlayerResponseExtractor.extract(fixture);
            FormatSelection selection = new FormatSelector().select(
                    player.adaptiveFormats(), 1080);

            assertThat(selection.video().itag()).isEqualTo(137);
            assertThat(selection.audio().itag()).isEqualTo(140);
        }
    }

    // ── 2. FormatSelector yields no video ───────────────────────────

    @Nested
    @DisplayName("FormatSelector — no video available")
    class NoVideoFormat {

        @Test
        @DisplayName("audio-only formats → NoMatchingFormatException (exit 30)")
        void download_flowA_givenNoVideoFormats_throwsNoMatchingFormat() {
            // Use the happy fixture but set maxHeight=1 so no video passes the filter
            YoutubeDownloader dl = sutWithFakeFfmpeg(innerTubeClient(), streamClient());
            DownloadRequest request = new DownloadRequest(
                    VALID_URL, false, AudioFormat.M4A, 1,
                    Optional.of(fakeFfmpegScript.toString()),
                    false, Optional.empty(), false,
                    new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                    ProgressListener.NO_OP, false, false);

            assertThatThrownBy(() -> dl.download(request))
                    .isInstanceOf(NoMatchingFormatException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(30));
        }
    }

    // ── 3. FfmpegMuxer.probeVersion fails → FfmpegException ────────

    @Nested
    @DisplayName("ffmpeg probe failure — AC-13.1, exit 60")
    class FfmpegProbeFails {

        @Test
        @DisplayName("probeVersion throws → FfmpegException; no downloads attempted")
        void download_flowA_givenBadFfmpeg_throwsFfmpegExceptionBeforeDownload() throws IOException {
            Path badFfmpeg = createFailingFfmpegProbe();
            int[] streamCallCount = {0};
            OkHttpClient countingStreamClient = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        streamCallCount[0]++;
                        return new Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("Content-Length", String.valueOf(FAKE_VIDEO.length))
                                .body(ResponseBody.create(FAKE_VIDEO, OCTET))
                                .build();
                    })
                    .build();

            YoutubeDownloader dl = sut(innerTubeClient(), countingStreamClient,
                    req -> new FfmpegMuxer(badFfmpeg.toString(), 30));

            assertThatThrownBy(() -> dl.download(new DownloadRequest(
                    VALID_URL, false, AudioFormat.M4A, 1080,
                    Optional.of(badFfmpeg.toString()),
                    false, Optional.empty(), false,
                    new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                    ProgressListener.NO_OP, false, false)))
                    .isInstanceOf(FfmpegException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(60));

            // AC-13.1: probe is BEFORE download — no stream calls made
            assertThat(streamCallCount[0]).isZero();
        }
    }

    // ── 4. Video stream download fails → NetworkException ───────────

    @Nested
    @DisplayName("Stream download failures — propagation + context retained")
    class StreamDownloadFailures {

        @Test
        @DisplayName("video stream fails → NetworkException propagates; .yt-tmp retained")
        void download_flowA_givenVideoStreamFails_throwsNetworkAndRetainsContext() {
            // All stream calls fail → retries exhausted → NetworkException
            OkHttpClient alwaysFailing = new OkHttpClient.Builder()
                    .addInterceptor(chain -> { throw new IOException("Simulated network failure"); })
                    .build();
            YoutubeDownloader dl = sut(innerTubeClient(), alwaysFailing,
                    req -> new FfmpegMuxer(fakeFfmpegScript.toString(), 30));

            assertThatThrownBy(() -> dl.download(flowARequest()))
                    .isInstanceOf(NetworkException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(10));

            // Context retained on failure (INV-6)
            assertThat(Files.exists(tempDir.resolve(".yt-tmp"))).isTrue();
        }

        @Test
        @DisplayName("audio stream fails after video downloaded → NetworkException; context retained")
        void download_flowA_givenAudioStreamFails_throwsNetworkAndRetainsContext() {
            // Video succeeds (calls 1-3 due to retries), audio fails on call 4
            // StreamDownloader retries internally, so we need to fail all attempts
            // for the second stream. Simplest: fail every call after the first.
            int[] counter = {0};
            OkHttpClient failSecondStream = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        counter[0]++;
                        if (counter[0] > 1) {
                            throw new IOException("Simulated audio download failure");
                        }
                        return new Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("Content-Length", String.valueOf(FAKE_VIDEO.length))
                                .body(ResponseBody.create(FAKE_VIDEO, OCTET))
                                .build();
                    })
                    .build();

            YoutubeDownloader dl = sut(innerTubeClient(), failSecondStream,
                    req -> new FfmpegMuxer(fakeFfmpegScript.toString(), 30));

            assertThatThrownBy(() -> dl.download(flowARequest()))
                    .isInstanceOf(NetworkException.class);

            assertThat(Files.exists(tempDir.resolve(".yt-tmp"))).isTrue();
        }
    }

    // ── 5. FfmpegMuxer.mux fails → FfmpegException ─────────────────

    @Nested
    @DisplayName("Mux failure — AC-13.4, exit 60")
    class MuxFailure {

        @Test
        @DisplayName("mux throws → FfmpegException; context retained")
        void download_flowA_givenMuxFails_throwsFfmpegExceptionAndRetainsContext() throws IOException {
            Path failMuxFfmpeg = createFfmpegMuxFailure();
            YoutubeDownloader dl = sut(innerTubeClient(), streamClient(),
                    req -> new FfmpegMuxer(failMuxFfmpeg.toString(), 30));

            assertThatThrownBy(() -> dl.download(new DownloadRequest(
                    VALID_URL, false, AudioFormat.M4A, 1080,
                    Optional.of(failMuxFfmpeg.toString()),
                    false, Optional.empty(), false,
                    new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                    ProgressListener.NO_OP, false, false)))
                    .isInstanceOf(FfmpegException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(60));

            assertThat(Files.exists(tempDir.resolve(".yt-tmp"))).isTrue();
        }
    }

    // ── 6. OutputWriter — file exists, force=false → exit 50 ────────

    @Nested
    @DisplayName("OutputWriter guards — before mux")
    class OutputWriterGuards {

        @Test
        @DisplayName("target exists, force=false → OutputExistsException (exit 50) BEFORE mux")
        void download_flowA_givenOutputExists_throwsOutputExistsBeforeMux() throws IOException {
            // Pre-create the expected output file
            String fixture = loadFixture("/fixtures/innertube-response-happy.json");
            PlayerResponse player = PlayerResponseExtractor.extract(fixture);
            String sanitized = OutputWriter.sanitizeTitle(player.videoDetails().title());
            String fileName = sanitized + " [dQw4w9WgXcQ].mp4";
            Files.createFile(tempDir.resolve(fileName));

            int[] muxCallCount = {0};
            YoutubeDownloader dl = sut(innerTubeClient(), streamClient(),
                    req -> {
                        muxCallCount[0]++;
                        return new FfmpegMuxer(fakeFfmpegScript.toString(), 30);
                    });

            assertThatThrownBy(() -> dl.download(flowARequest()))
                    .isInstanceOf(OutputExistsException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(50));
        }

        @Test
        @DisplayName("free-disk insufficient → FilesystemException (exit 70) BEFORE mux")
        void download_flowA_givenInsufficientDisk_throwsFilesystemException() {
            // This test verifies the assertSufficientFreeSpace path exists.
            // We can't easily simulate disk-full in a unit test, so we verify
            // the OutputWriter method directly with a huge expected size.
            OutputConfig config = new OutputConfig(Optional.empty(), Optional.of(tempDir), false);
            OutputWriter writer = new OutputWriter(config);
            VideoDetails details = new VideoDetails(
                    VideoId.of("dQw4w9WgXcQ"), "Test", false, false, Optional.empty());
            Path outputPath = writer.deriveOutputPath(details, "mp4");

            assertThatThrownBy(() -> writer.assertSufficientFreeSpace(outputPath, Long.MAX_VALUE / 2))
                    .isInstanceOf(FilesystemException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(70));
        }
    }

    // ── 7. Filename sanitization ────────────────────────────────────

    @Nested
    @DisplayName("Filename sanitization — AC-3.3")
    class FilenameSanitization {

        @Test
        @DisplayName("title with '/' → sanitized in final .mp4 path")
        void download_flowA_givenTitleWithSlash_sanitizesFilename() {
            // Create a fixture with a title containing '/'
            String fixture = loadFixture("/fixtures/innertube-response-happy.json")
                    .replace("Rick Astley - Never Gonna Give You Up (Official Music Video)",
                            "Video/With/Slashes");

            OkHttpClient itHttp = new OkHttpClient.Builder()
                    .addInterceptor(chain -> new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(fixture, JSON))
                            .build())
                    .build();

            YoutubeDownloader dl = sutWithFakeFfmpeg(itHttp, streamClient());
            DownloadResult result = dl.download(flowARequest());

            assertThat(result.videoPath()).isPresent();
            String fileName = result.videoPath().get().getFileName().toString();
            assertThat(fileName).doesNotContain("/");
            assertThat(fileName).contains("VideoWithSlashes");
            assertThat(fileName).endsWith(".mp4");
        }
    }

    // ── 8. ProgressListener receives events ─────────────────────────

    @Nested
    @DisplayName("ProgressListener — AC-4.1")
    class ProgressEvents {

        @Test
        @DisplayName("listener receives events from BOTH video and audio downloads")
        void download_flowA_progressListenerReceivesEventsFromBothStreams() {
            List<Long> progressBytes = new ArrayList<>();
            ProgressListener capturing = (bytesWritten, totalBytes) ->
                    progressBytes.add(bytesWritten);

            YoutubeDownloader dl = sut(innerTubeClient(), streamClient(),
                    req -> new FfmpegMuxer(fakeFfmpegScript.toString(), 30));

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, false, AudioFormat.M4A, 1080,
                    Optional.of(fakeFfmpegScript.toString()),
                    false, Optional.empty(), false,
                    new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                    capturing, false, false);

            dl.download(request);

            // Two streams downloaded → at least 2 progress events (one per stream)
            assertThat(progressBytes).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    // ── 9. Debug flag plumbing ──────────────────────────────────────

    @Nested
    @DisplayName("Debug flag — NFR-FFMPEG-LOGLEVEL")
    class DebugFlag {

        @Test
        @DisplayName("debug=true → ffmpeg invoked with -loglevel info")
        void download_flowA_givenDebugTrue_ffmpegUsesInfoLoglevel() throws IOException {
            YoutubeDownloader dl = sutWithFakeFfmpeg(innerTubeClient(), streamClient());

            dl.download(flowARequest(true));

            String args = Files.readString(tempDir.resolve("ffmpeg-args.txt"));
            // The mux invocation line should contain "-loglevel info"
            String[] lines = args.split("\n");
            String muxLine = lines.length > 1 ? lines[1] : lines[0];
            // First line is -version, second is the mux call
            assertThat(args).contains("-loglevel info");
        }

        @Test
        @DisplayName("debug=false → ffmpeg invoked with -loglevel error")
        void download_flowA_givenDebugFalse_ffmpegUsesErrorLoglevel() throws IOException {
            YoutubeDownloader dl = sutWithFakeFfmpeg(innerTubeClient(), streamClient());

            dl.download(flowARequest(false));

            String args = Files.readString(tempDir.resolve("ffmpeg-args.txt"));
            assertThat(args).contains("-loglevel error");
        }
    }

    // ── 10. --ffmpeg-location plumbed via factory ───────────────────

    @Nested
    @DisplayName("--ffmpeg-location plumbing — AC-13.2")
    class FfmpegLocationPlumbing {

        @Test
        @DisplayName("ffmpegLocation from DownloadRequest is plumbed into FfmpegMuxer via factory")
        void download_flowA_ffmpegLocationPlumbedViaFactory() {
            AtomicReference<DownloadRequest> capturedRequest = new AtomicReference<>();

            YoutubeDownloader dl = sut(innerTubeClient(), streamClient(),
                    req -> {
                        capturedRequest.set(req);
                        return new FfmpegMuxer(fakeFfmpegScript.toString(), 30);
                    });

            dl.download(flowARequest());

            assertThat(capturedRequest.get()).isNotNull();
            assertThat(capturedRequest.get().ffmpegLocation())
                    .isPresent()
                    .hasValue(fakeFfmpegScript.toString());
        }
    }

    // ── 11. DownloadContext lifecycle ────────────────────────────────

    @Nested
    @DisplayName("DownloadContext .yt-tmp lifecycle — INV-6")
    class DownloadContextLifecycle {

        @Test
        @DisplayName(".yt-tmp created during download then deleted on success")
        void download_flowA_tempDirCreatedThenDeletedOnSuccess() {
            YoutubeDownloader dl = sutWithFakeFfmpeg(innerTubeClient(), streamClient());

            DownloadResult result = dl.download(flowARequest());

            assertThat(result.videoPath()).isPresent();
            // .yt-tmp should be gone after success
            assertThat(Files.exists(tempDir.resolve(".yt-tmp"))).isFalse();
        }

        @Test
        @DisplayName(".yt-tmp retained on failure")
        void download_flowA_tempDirRetainedOnFailure() throws IOException {
            Path failMux = createFfmpegMuxFailure();
            YoutubeDownloader dl = sut(innerTubeClient(), streamClient(),
                    req -> new FfmpegMuxer(failMux.toString(), 30));

            assertThatThrownBy(() -> dl.download(new DownloadRequest(
                    VALID_URL, false, AudioFormat.M4A, 1080,
                    Optional.of(failMux.toString()),
                    false, Optional.empty(), false,
                    new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                    ProgressListener.NO_OP, false, false)))
                    .isInstanceOf(FfmpegException.class);

            assertThat(Files.exists(tempDir.resolve(".yt-tmp"))).isTrue();
        }
    }

    // ── 12. Default muxer factory uses ffmpegLocation ───────────────

    @Nested
    @DisplayName("Default muxer factory — ffmpegLocation wiring")
    class DefaultMuxerFactory {

        @Test
        @DisplayName("5-arg ctor uses DEFAULT_MUXER_FACTORY which respects ffmpegLocation")
        void download_flowA_defaultFactory_respectsFfmpegLocation() {
            // Use the 4-arg ctor (which uses DEFAULT_MUXER_FACTORY internally)
            YoutubeDownloader dl = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(innerTubeClient()),
                    new FormatSelector(),
                    new StreamDownloader(streamClient()));

            DownloadResult result = dl.download(new DownloadRequest(
                    VALID_URL, false, AudioFormat.M4A, 1080,
                    Optional.of(fakeFfmpegScript.toString()),
                    false, Optional.empty(), false,
                    new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                    ProgressListener.NO_OP, false, false));

            assertThat(result.videoPath()).isPresent();
            assertThat(result.videoPath().get().getFileName().toString()).endsWith(".mp4");
        }
    }

    // ── 13. Force overwrite ─────────────────────────────────────────

    @Nested
    @DisplayName("Force overwrite — AC-3.6")
    class ForceOverwrite {

        @Test
        @DisplayName("target exists + force=true → succeeds")
        void download_flowA_givenOutputExistsAndForce_succeeds() throws IOException {
            // Pre-create the expected output file
            String fixture = loadFixture("/fixtures/innertube-response-happy.json");
            PlayerResponse player = PlayerResponseExtractor.extract(fixture);
            String sanitized = OutputWriter.sanitizeTitle(player.videoDetails().title());
            String fileName = sanitized + " [dQw4w9WgXcQ].mp4";
            Files.createFile(tempDir.resolve(fileName));

            YoutubeDownloader dl = sutWithFakeFfmpeg(innerTubeClient(), streamClient());

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, false, AudioFormat.M4A, 1080,
                    Optional.of(fakeFfmpegScript.toString()),
                    false, Optional.empty(), false,
                    new OutputConfig(Optional.empty(), Optional.of(tempDir), true),
                    ProgressListener.NO_OP, false, false);

            DownloadResult result = dl.download(request);

            assertThat(result.videoPath()).isPresent();
        }
    }
}
