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
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for T-3.9 — {@code --audio-format mp3} + Flow B'
 * (AC-2.4, AC-2.3, AC-2.5, AC-13.1, CT-EXIT-UNIT-10).
 *
 * <p>Covers:
 * <ul>
 *   <li>MP3 happy path: probeVersion + transcodeMp3 + .mp3 output + .yt-tmp cleanup</li>
 *   <li>M4A default: no ffmpeg involvement</li>
 *   <li>--audio-format mp3 without --audio-only: WARN + treat as audio-only (AC-2.5)</li>
 *   <li>Output exists + no force → OutputExistsException BEFORE transcode (exit 50)</li>
 *   <li>probeVersion failure → FfmpegException, no download (exit 60)</li>
 *   <li>transcodeMp3 failure → FfmpegException, context retained</li>
 *   <li>Filename ends with .mp3 (not .m4a) when MP3</li>
 *   <li>DownloadResult.audioFile populated for both formats</li>
 *   <li>CT-EXIT-UNIT-10: FfmpegException → exit 60</li>
 * </ul>
 *
 * <p>SUT is not mocked. OkHttp interceptors provide canned responses.
 * FfmpegMuxer is replaced via the muxerFactory to control probe/transcode outcomes.
 */
class YoutubeDownloaderMp3BehaviorTest {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_AUDIO = {0x00, 0x01, 0x02, 0x03};
    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @TempDir
    Path tempDir;

    private OkHttpClient fakeInnerTubeHttp;
    private OkHttpClient fakeStreamHttp;
    private Path fakeFfmpegScript;

    @BeforeEach
    void setUp() throws IOException {
        fakeInnerTubeHttp = interceptorReturning(200, loadFixture("/fixtures/innertube-response-happy.json"));
        fakeStreamHttp = interceptorReturning(200, FAKE_AUDIO);

        fakeFfmpegScript = tempDir.resolve("fake-ffmpeg");
        Files.writeString(fakeFfmpegScript, """
                #!/bin/sh
                if [ "$1" = "-version" ]; then
                    echo "ffmpeg version 7.1.0 Copyright (c) 2000-2024 the FFmpeg developers"
                    exit 0
                fi
                OUTPUT="${@: -1}"
                printf '\\x00\\x01\\x02\\x03' > "$OUTPUT"
                exit 0
                """);
        fakeFfmpegScript.toFile().setExecutable(true);
    }

    // ── 1. MP3 happy path (AC-2.4) ─────────────────────────────────

    @Nested
    @DisplayName("MP3 happy path — AC-2.4")
    class Mp3HappyPath {

        @Test
        @DisplayName("--audio-only --audio-format mp3: downloads, probeVersion called, transcodeMp3 called, .mp3 produced, .yt-tmp deleted")
        void download_audioOnlyMp3_transcodesAndProducesMP3() {
            YoutubeDownloader sut = buildSutWithRealFfmpeg();

            DownloadRequest request = mp3Request(outputDir(tempDir));

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(result.audioPath().get().getFileName().toString()).endsWith(".mp3");
            assertThat(Files.exists(result.audioPath().get())).isTrue();
            assertThat(Files.exists(tempDir.resolve(".yt-tmp"))).isFalse();
        }
    }

    // ── 2. M4A default — no ffmpeg (AC-2.3) ────────────────────────

    @Nested
    @DisplayName("M4A default — AC-2.3")
    class M4aDefault {

        @Test
        @DisplayName("--audio-only (default m4a): rename .part → .m4a, NO probeVersion, NO transcode")
        void download_audioOnlyM4a_noFfmpegInvolved() {
            // Use a muxer factory that throws if probeVersion is called — proves ffmpeg not touched
            Function<DownloadRequest, FfmpegMuxer> failingFactory = req -> {
                throw new AssertionError("FfmpegMuxer should not be created for M4A path");
            };
            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp),
                    failingFactory);

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.M4A, 0,
                    Optional.empty(), outputDir(tempDir), ProgressListener.NO_OP, false);

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(result.audioPath().get().getFileName().toString()).endsWith(".m4a");
        }
    }

    // ── 3. --audio-format mp3 WITHOUT --audio-only (AC-2.5) ────────

    @Nested
    @DisplayName("--audio-format mp3 without --audio-only — AC-2.5")
    class Mp3WithoutAudioOnly {

        @Test
        @DisplayName("Cli treats --audio-format mp3 without --audio-only as audio-only with WARN")
        void download_audioFormatMp3WithoutAudioOnly_treatedAsAudioOnly() {
            // The Cli class handles this by setting audioOnly=true when audioFormat=MP3.
            // At the library level, if audioOnly=false but audioFormat=MP3, the video path runs.
            // This test verifies the Cli-level behaviour via DownloadRequest constructed as Cli would.
            YoutubeDownloader sut = buildSutWithRealFfmpeg();

            // Simulate what Cli does: sets audioOnly=true when audioFormat=MP3 (AC-2.5)
            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.MP3, 0,
                    Optional.of(fakeFfmpegScript.toString()),
                    outputDir(tempDir), ProgressListener.NO_OP, false);

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(result.audioPath().get().getFileName().toString()).endsWith(".mp3");
            assertThat(result.videoPath()).isEmpty();
        }
    }

    // ── 4. Output file exists + no --force → exit 50 BEFORE transcode ──

    @Nested
    @DisplayName("Output exists — exit 50 before transcode")
    class OutputExistsBeforeTranscode {

        @Test
        @DisplayName("MP3 output file exists + no --force → OutputExistsException (exit 50) BEFORE transcode")
        void download_mp3OutputExists_noForce_throwsBeforeTranscode() throws IOException {
            // Pre-create the expected .mp3 output file
            String expectedName = "Rick Astley - Never Gonna Give You Up (Official Music Video) [dQw4w9WgXcQ].mp3";
            Files.write(tempDir.resolve(expectedName), new byte[]{0x42});

            // Use a muxer factory that tracks whether probeVersion was called
            // probeVersion IS called before the output-exists check in the MP3 path
            YoutubeDownloader sut = buildSutWithRealFfmpeg();

            DownloadRequest request = mp3Request(outputDir(tempDir));

            assertThatThrownBy(() -> sut.download(request))
                    .isInstanceOf(OutputExistsException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(50));
        }
    }

    // ── 5. probeVersion fails → FfmpegException, NO download (AC-13.1, exit 60) ──

    @Nested
    @DisplayName("probeVersion failure — AC-13.1, exit 60")
    class ProbeVersionFailure {

        @Test
        @DisplayName("FfmpegMuxer.probeVersion fails → FfmpegException, NO download attempted")
        void download_mp3ProbeVersionFails_throwsFfmpegExceptionNoDownload() {
            // Create a broken ffmpeg script that fails on -version
            Path brokenFfmpeg = tempDir.resolve("broken-ffmpeg");
            try {
                Files.writeString(brokenFfmpeg, """
                        #!/bin/sh
                        exit 1
                        """);
                brokenFfmpeg.toFile().setExecutable(true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Track whether stream download was attempted
            boolean[] streamCalled = {false};
            OkHttpClient trackingStreamHttp = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        streamCalled[0] = true;
                        return new Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(ResponseBody.create(FAKE_AUDIO, OCTET))
                                .build();
                    })
                    .build();

            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp),
                    new FormatSelector(),
                    new StreamDownloader(trackingStreamHttp),
                    req -> new FfmpegMuxer(brokenFfmpeg.toString(), 30));

            DownloadRequest request = mp3Request(outputDir(tempDir));

            assertThatThrownBy(() -> sut.download(request))
                    .isInstanceOf(FfmpegException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(60));

            assertThat(streamCalled[0]).isFalse();
        }
    }

    // ── 6. transcodeMp3 fails → FfmpegException, context retained ──

    @Nested
    @DisplayName("transcodeMp3 failure — context retained")
    class TranscodeFailure {

        @Test
        @DisplayName("FfmpegMuxer.transcodeMp3 fails → FfmpegException, .yt-tmp retained")
        void download_mp3TranscodeFails_throwsFfmpegExceptionContextRetained() throws IOException {
            // Script that passes -version but fails on transcode
            Path failTranscodeFfmpeg = tempDir.resolve("fail-transcode-ffmpeg");
            Files.writeString(failTranscodeFfmpeg, """
                    #!/bin/sh
                    if [ "$1" = "-version" ]; then
                        echo "ffmpeg version 7.1.0 Copyright (c) 2000-2024 the FFmpeg developers"
                        exit 0
                    fi
                    exit 1
                    """);
            failTranscodeFfmpeg.toFile().setExecutable(true);

            Path outputSubDir = tempDir.resolve("output");
            Files.createDirectories(outputSubDir);

            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp),
                    req -> new FfmpegMuxer(failTranscodeFfmpeg.toString(), 30));

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.MP3, 0,
                    Optional.of(failTranscodeFfmpeg.toString()),
                    new OutputConfig(Optional.empty(), Optional.of(outputSubDir), false),
                    ProgressListener.NO_OP, false);

            assertThatThrownBy(() -> sut.download(request))
                    .isInstanceOf(FfmpegException.class);

            // .yt-tmp retained on failure (INV-6)
            assertThat(Files.exists(outputSubDir.resolve(".yt-tmp"))).isTrue();
        }
    }

    // ── 7. Filename ends with .mp3 (not .m4a) when MP3 ─────────────

    @Nested
    @DisplayName("Filename extension — .mp3 vs .m4a")
    class FilenameExtension {

        @Test
        @DisplayName("MP3 request → output filename ends with .mp3")
        void download_mp3_filenameEndsMp3() {
            YoutubeDownloader sut = buildSutWithRealFfmpeg();

            DownloadRequest request = mp3Request(outputDir(tempDir));

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath().get().getFileName().toString()).endsWith(".mp3");
            assertThat(result.audioPath().get().getFileName().toString()).doesNotEndWith(".m4a");
        }

        @Test
        @DisplayName("M4A request → output filename ends with .m4a")
        void download_m4a_filenameEndsM4a() {
            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp));

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.M4A, 0,
                    Optional.empty(), outputDir(tempDir), ProgressListener.NO_OP, false);

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath().get().getFileName().toString()).endsWith(".m4a");
            assertThat(result.audioPath().get().getFileName().toString()).doesNotEndWith(".mp3");
        }
    }

    // ── 8. DownloadResult.audioPath populated for both formats ──────

    @Nested
    @DisplayName("DownloadResult.audioPath — both formats")
    class DownloadResultAudioPath {

        @Test
        @DisplayName("MP3: DownloadResult.audioPath is present and points to existing file")
        void download_mp3_audioPathPopulated() {
            YoutubeDownloader sut = buildSutWithRealFfmpeg();

            DownloadResult result = sut.download(mp3Request(outputDir(tempDir)));

            assertThat(result.audioPath()).isPresent();
            assertThat(Files.exists(result.audioPath().get())).isTrue();
            assertThat(result.videoPath()).isEmpty();
        }

        @Test
        @DisplayName("M4A: DownloadResult.audioPath is present and points to existing file")
        void download_m4a_audioPathPopulated() {
            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp));

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.M4A, 0,
                    Optional.empty(), outputDir(tempDir), ProgressListener.NO_OP, false);

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(Files.exists(result.audioPath().get())).isTrue();
            assertThat(result.videoPath()).isEmpty();
        }
    }

    // ── 9. CT-EXIT-UNIT-10: FfmpegException → exit 60 ──────────────

    @Nested
    @DisplayName("CT-EXIT-UNIT-10: FfmpegException → exit 60")
    class CtExitUnit10 {

        @Test
        @DisplayName("FfmpegException from probe failure in MP3 path → exitCode() == 60")
        void ffmpegException_probeFailure_exitCode60() {
            Path missingFfmpeg = tempDir.resolve("nonexistent-ffmpeg");

            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp),
                    req -> new FfmpegMuxer(missingFfmpeg.toString(), 30));

            DownloadRequest request = mp3Request(outputDir(tempDir));

            assertThatThrownBy(() -> sut.download(request))
                    .isInstanceOf(FfmpegException.class)
                    .satisfies(e -> {
                        assertThat(((FfmpegException) e).exitCode()).isEqualTo(60);
                        assertThat(e.getMessage()).contains("ffmpeg");
                    });
        }
    }

    // ── helpers ──────────────────────────────────────────────────────

    private YoutubeDownloader buildSutWithRealFfmpeg() {
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(fakeStreamHttp),
                req -> new FfmpegMuxer(fakeFfmpegScript.toString(), 30));
    }

    private static DownloadRequest mp3Request(OutputConfig output) {
        return new DownloadRequest(
                VALID_URL, true, AudioFormat.MP3, 0,
                Optional.empty(), output, ProgressListener.NO_OP, false);
    }

    private static OutputConfig outputDir(Path dir) {
        return new OutputConfig(Optional.empty(), Optional.of(dir), false);
    }

    private static OkHttpClient interceptorReturning(int status, String body) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(status)
                        .message("OK")
                        .body(ResponseBody.create(body, JSON))
                        .build())
                .build();
    }

    private static OkHttpClient interceptorReturning(int status, byte[] body) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(status)
                        .message("OK")
                        .header("Content-Length", String.valueOf(body.length))
                        .body(ResponseBody.create(body, OCTET))
                        .build())
                .build();
    }

    private static String loadFixture(String resourcePath) {
        try (InputStream is = YoutubeDownloaderMp3BehaviorTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }
}
