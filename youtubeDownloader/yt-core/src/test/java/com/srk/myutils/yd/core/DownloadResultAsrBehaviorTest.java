package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavior tests for T-4.7 — {@code DownloadResult.usedAsrFallback} wiring
 * per AC-7.3 and INV-16.
 *
 * <p>Verifies that the field is correctly carried through the record and that
 * current orchestrator paths (Flow A, Flow B M4A, Flow B' MP3) all produce
 * {@code usedAsrFallback == false} since the transcript/caption selection
 * flow that can set it to {@code true} arrives in T-4.10.
 *
 * <p>SUT is not mocked. External HTTP uses OkHttp interceptors.
 */
class DownloadResultAsrBehaviorTest {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_AUDIO = {0x00, 0x01, 0x02, 0x03};
    private static final byte[] FAKE_VIDEO = {0x04, 0x05, 0x06, 0x07};
    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @TempDir
    Path tempDir;

    private OkHttpClient fakeInnerTubeHttp;
    private OkHttpClient fakeStreamHttp;

    @BeforeEach
    void setUp() {
        fakeInnerTubeHttp = interceptorReturning(200, loadFixture("/fixtures/innertube-response-happy.json"));
        fakeStreamHttp = interceptorReturning(200, FAKE_AUDIO);
    }

    // ── 1. Record-level: usedAsrFallback=false (AC-7.3 default) ────

    @Test
    @DisplayName("DownloadResult with usedAsrFallback=false — typical non-ASR path")
    void downloadResult_usedAsrFallbackFalse_typicalPath() {
        DownloadResult result = new DownloadResult(
                VideoId.of("dQw4w9WgXcQ"), "title",
                Optional.of(Path.of("video.mp4")), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                false);

        assertThat(result.usedAsrFallback()).isFalse();
    }

    // ── 2. Record-level: usedAsrFallback=true (AC-7.3 ASR fallback) ─

    @Test
    @DisplayName("DownloadResult with usedAsrFallback=true — ASR fallback path (AC-7.3)")
    void downloadResult_usedAsrFallbackTrue_asrFallbackPath() {
        DownloadResult result = new DownloadResult(
                VideoId.of("dQw4w9WgXcQ"), "title",
                Optional.empty(), Optional.empty(),
                Optional.of(Path.of("out.srt")), Optional.of(Path.of("out.txt")),
                Optional.empty(),
                true);

        assertThat(result.usedAsrFallback()).isTrue();
    }

    // ── 3. Flow A (video+audio) → usedAsrFallback == false ──────────

    @Test
    @DisplayName("Flow A video+audio download → usedAsrFallback is false")
    void download_flowA_usedAsrFallbackIsFalse() throws IOException {
        Path fakeFfmpeg = createFakeFfmpeg();
        YoutubeDownloader sut = new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(interceptorReturning(200, FAKE_VIDEO)),
                req -> new FfmpegMuxer(fakeFfmpeg.toString()));

        DownloadRequest request = new DownloadRequest(
                VALID_URL, false, AudioFormat.M4A, 1080, Optional.empty(),
                false, Optional.empty(), false, outputDir(tempDir),
                ProgressListener.NO_OP, false, false);

        DownloadResult result = sut.download(request);

        assertThat(result.usedAsrFallback()).isFalse();
    }

    // ── 4. Audio-only M4A → usedAsrFallback == false ────────────────

    @Test
    @DisplayName("Audio-only M4A download → usedAsrFallback is false")
    void download_audioOnlyM4a_usedAsrFallbackIsFalse() {
        YoutubeDownloader sut = buildSut();

        DownloadRequest request = new DownloadRequest(
                VALID_URL, true, AudioFormat.M4A, 0, Optional.empty(),
                false, Optional.empty(), false, outputDir(tempDir),
                ProgressListener.NO_OP, false, false);

        DownloadResult result = sut.download(request);

        assertThat(result.usedAsrFallback()).isFalse();
    }

    // ── 5. Audio-only MP3 → usedAsrFallback == false ────────────────

    @Test
    @DisplayName("Audio-only MP3 download → usedAsrFallback is false")
    void download_audioOnlyMp3_usedAsrFallbackIsFalse() throws IOException {
        Path fakeFfmpeg = createFakeFfmpeg();
        YoutubeDownloader sut = new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(fakeStreamHttp),
                req -> new FfmpegMuxer(fakeFfmpeg.toString()));

        DownloadRequest request = new DownloadRequest(
                VALID_URL, true, AudioFormat.MP3, 0, Optional.empty(),
                false, Optional.empty(), false, outputDir(tempDir),
                ProgressListener.NO_OP, false, false);

        DownloadResult result = sut.download(request);

        assertThat(result.usedAsrFallback()).isFalse();
    }

    // ── 6. No orchestrator path currently sets usedAsrFallback=true ─

    @Test
    @DisplayName("Current state: all orchestrator paths produce usedAsrFallback=false (transcript flow in T-4.10)")
    void download_allCurrentPaths_usedAsrFallbackAlwaysFalse() {
        // Documents that until T-4.10 wires CaptionSelection into the orchestrator,
        // no download path can produce usedAsrFallback=true. This test will need
        // updating when T-4.10 adds the caption-selection → DownloadResult wiring.
        YoutubeDownloader sut = buildSut();

        DownloadResult audioResult = sut.download(new DownloadRequest(
                VALID_URL, true, AudioFormat.M4A, 0, Optional.empty(),
                false, Optional.empty(), false, outputDir(tempDir),
                ProgressListener.NO_OP, false, false));

        assertThat(audioResult.usedAsrFallback())
                .as("No orchestrator path flips usedAsrFallback to true until T-4.10")
                .isFalse();
    }

    // ── 7. INV-16 placeholder — pending T-4.10 ─────────────────────

    @Test
    @Disabled("INV-16 end-to-end: usedAsrFallback set exactly once in SELECTING_FORMATS — " +
              "requires CaptionSelection → DownloadResult wiring from T-4.10")
    @DisplayName("INV-16: usedAsrFallback set exactly once during format selection (pending T-4.10)")
    void inv16_setExactlyOnceInSelectingFormats_pendingT4_10() {
        // T-4.10 will enable this test and verify:
        // 1. FormatSelector.selectCaption() returns CaptionSelection with usedAsrFallback=true
        //    when ASR fallback is triggered
        // 2. The orchestrator propagates that flag into DownloadResult exactly once
        // 3. No later state transition modifies the flag
    }

    // ── helpers ──────────────────────────────────────────────────────

    private YoutubeDownloader buildSut() {
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(fakeStreamHttp));
    }

    private static OutputConfig outputDir(Path dir) {
        return new OutputConfig(Optional.empty(), Optional.of(dir), false);
    }

    private Path createFakeFfmpeg() throws IOException {
        Path script = tempDir.resolve("fake-ffmpeg");
        Files.writeString(script, """
                #!/bin/sh
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
        try (InputStream is = DownloadResultAsrBehaviorTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }
}
