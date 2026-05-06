package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for T-4.11 — Flow C (transcript-only).
 *
 * <p>Flow C is triggered when {@code request.transcript() && !request.audioOnly()}.
 * It produces only .srt + .txt (+ optional .jpg). No media download. Never
 * invokes muxerFactory (AC-13.5). Never invokes streamDownloader.
 *
 * <p>SUT ({@link YoutubeDownloader}) is never mocked. External HTTP is faked
 * via OkHttp interceptors. Throwing factories verify invariants.
 */
class YoutubeDownloaderFlowCBehaviorTest {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    private static final String TIMEDTEXT_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <transcript>
              <text start="0.5" dur="2.0">Never gonna give you up</text>
              <text start="3.0" dur="2.5">Never gonna let you down</text>
            </transcript>
            """;

    private static final Function<DownloadRequest, FfmpegMuxer> THROWING_MUXER_FACTORY =
            req -> { throw new AssertionError("muxerFactory must not be invoked in Flow C"); };

    @TempDir
    Path tempDir;

    // ── 1. Flow routing: --transcript alone → Flow C ────────────────

    @Nested
    @DisplayName("Flow C routing — --transcript alone")
    class FlowCRouting {

        @Test
        @DisplayName("--transcript alone → Flow C: only srt + txt; videoPath/audioPath/thumbnailPath empty")
        void download_transcriptAlone_flowC_onlySrtTxt() {
            YoutubeDownloader sut = buildFlowCSut(
                    loadFixture("/fixtures/innertube-response-happy.json"), TIMEDTEXT_XML);

            DownloadResult result = sut.download(flowCRequest(Optional.empty(), false, false));

            assertThat(result.videoPath()).isEmpty();
            assertThat(result.audioPath()).isEmpty();
            assertThat(result.thumbnailPath()).isEmpty();
            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
        }

        @Test
        @DisplayName("--transcript --thumbnail → Flow C + jpg; no video/audio")
        void download_transcriptWithThumbnail_flowC_srtTxtJpg() {
            byte[] fakeJpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
            YoutubeDownloader sut = buildFlowCSutWithThumbnail(
                    loadFixture("/fixtures/innertube-response-happy.json"), TIMEDTEXT_XML, fakeJpeg);

            DownloadResult result = sut.download(flowCRequest(Optional.empty(), false, true));

            assertThat(result.videoPath()).isEmpty();
            assertThat(result.audioPath()).isEmpty();
            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
            assertThat(result.thumbnailPath()).isPresent();
            assertThat(result.thumbnailPath().get().toString()).endsWith(".jpg");
        }
    }

    // ── 2. Flow B takes precedence when --audio-only is set ─────────

    @Nested
    @DisplayName("Flow B precedence — --transcript + --audio-only")
    class FlowBPrecedence {

        @Test
        @DisplayName("--transcript --audio-only → Flow B (M4A) + transcript side-effect; media PRESENT")
        void download_transcriptAndAudioOnly_flowB_mediaPresent() {
            YoutubeDownloader sut = buildFlowBSut(
                    loadFixture("/fixtures/innertube-response-happy.json"), TIMEDTEXT_XML);

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.M4A, 0, Optional.empty(),
                    true, Optional.empty(), false,
                    outputDir(), ProgressListener.NO_OP, false, false, false);

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(result.audioPath().get().toString()).endsWith(".m4a");
            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
        }

        @Test
        @DisplayName("--transcript --audio-only --audio-format mp3 → Flow B' + transcript")
        void download_transcriptAudioOnlyMp3_flowBPrime_mp3PlusTranscript() throws IOException {
            Path fakeFfmpeg = createFakeFfmpeg();
            YoutubeDownloader sut = buildFlowBSutWithFfmpeg(
                    loadFixture("/fixtures/innertube-response-happy.json"), TIMEDTEXT_XML, fakeFfmpeg);

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.MP3, 0, Optional.empty(),
                    true, Optional.empty(), false,
                    outputDir(), ProgressListener.NO_OP, false, false, false);

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(result.audioPath().get().toString()).endsWith(".mp3");
            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
        }
    }

    // ── 3. Unchanged behavior: no --transcript → Flow A/B as before ─

    @Nested
    @DisplayName("Unchanged behavior — no --transcript")
    class UnchangedBehavior {

        @Test
        @DisplayName("No --transcript, no --audio-only → Flow A (video+mux); unchanged")
        void download_noTranscriptNoAudioOnly_flowA() throws IOException {
            Path fakeFfmpeg = createFakeFfmpeg();
            YoutubeDownloader sut = buildFlowASut(
                    loadFixture("/fixtures/innertube-response-happy.json"), fakeFfmpeg);

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, false, AudioFormat.M4A, 1080, Optional.empty(),
                    false, Optional.empty(), false,
                    outputDir(), ProgressListener.NO_OP, false, false, false);

            DownloadResult result = sut.download(request);

            assertThat(result.videoPath()).isPresent();
            assertThat(result.srtPath()).isEmpty();
            assertThat(result.txtPath()).isEmpty();
        }

        @Test
        @DisplayName("--audio-only without --transcript → Flow B; unchanged")
        void download_audioOnlyNoTranscript_flowB() {
            YoutubeDownloader sut = buildFlowBSut(
                    loadFixture("/fixtures/innertube-response-happy.json"), TIMEDTEXT_XML);

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.M4A, 0, Optional.empty(),
                    false, Optional.empty(), false,
                    outputDir(), ProgressListener.NO_OP, false, false, false);

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(result.srtPath()).isEmpty();
            assertThat(result.txtPath()).isEmpty();
        }
    }

    // ── 4. Flow C happy path with fixture content ───────────────────

    @Nested
    @DisplayName("Flow C happy path — content verification")
    class FlowCHappyPath {

        @Test
        @DisplayName("Flow C with manual en track → srt + txt populated with cue content")
        void download_flowC_manualTrack_srtTxtPopulated() throws IOException {
            YoutubeDownloader sut = buildFlowCSut(
                    loadFixture("/fixtures/innertube-response-happy.json"), TIMEDTEXT_XML);

            DownloadResult result = sut.download(flowCRequest(Optional.empty(), false, false));

            String srt = Files.readString(result.srtPath().get());
            String txt = Files.readString(result.txtPath().get());

            assertThat(srt).contains("Never gonna give you up");
            assertThat(srt).contains("-->");
            assertThat(txt).contains("Never gonna give you up");
            assertThat(txt).contains("Never gonna let you down");
            assertThat(result.usedAsrFallback()).isFalse();
        }

        @Test
        @DisplayName("Flow C with asr-only fixture, noAsr=false → ASR track used, usedAsrFallback=true")
        void download_flowC_asrOnly_usedAsrFallbackTrue() {
            YoutubeDownloader sut = buildFlowCSut(
                    loadFixture("/fixtures/innertube-response-asr-only.json"), TIMEDTEXT_XML);

            DownloadResult result = sut.download(flowCRequest(Optional.empty(), false, false));

            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
            assertThat(result.usedAsrFallback()).isTrue();
        }
    }

    // ── 5. Flow C error paths ───────────────────────────────────────

    @Nested
    @DisplayName("Flow C error paths")
    class FlowCErrors {

        @Test
        @DisplayName("Flow C with asr-only + noAsr=true → CaptionUnavailableException exit 40")
        void download_flowC_asrOnlyNoAsr_throwsExit40() {
            YoutubeDownloader sut = buildFlowCSut(
                    loadFixture("/fixtures/innertube-response-asr-only.json"), TIMEDTEXT_XML);

            assertThatThrownBy(() -> sut.download(flowCRequest(Optional.empty(), true, false)))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .satisfies(e -> assertThat(((CaptionUnavailableException) e).exitCode()).isEqualTo(40))
                    .hasMessageContaining("--no-asr");
        }

        @Test
        @DisplayName("Flow C with no-captions fixture → CaptionUnavailableException exit 40")
        void download_flowC_noCaptions_throwsExit40() {
            YoutubeDownloader sut = buildFlowCSut(
                    loadFixture("/fixtures/innertube-response-no-captions.json"), TIMEDTEXT_XML);

            assertThatThrownBy(() -> sut.download(flowCRequest(Optional.empty(), false, false)))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .satisfies(e -> assertThat(((CaptionUnavailableException) e).exitCode()).isEqualTo(40));
        }

        @Test
        @DisplayName("Flow C CaptionDownloader fails (NetworkException) → propagates (not partial success)")
        void download_flowC_captionFails_propagatesNetworkException() {
            OkHttpClient failingCaptionHttp = new OkHttpClient.Builder()
                    .addInterceptor(chain -> new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("Internal Server Error")
                            .body(ResponseBody.create("error", MediaType.get("text/plain")))
                            .build())
                    .build();

            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp(loadFixture("/fixtures/innertube-response-happy.json"))),
                    new FormatSelector(),
                    throwingStreamDownloader(),
                    THROWING_MUXER_FACTORY,
                    new CaptionDownloader(failingCaptionHttp),
                    ThumbnailDownloader.create());

            assertThatThrownBy(() -> sut.download(flowCRequest(Optional.empty(), false, false)))
                    .isInstanceOf(NetworkException.class)
                    .hasMessageContaining("Caption download failed");
        }
    }

    // ── 6. Flow C invariants: no muxer, no stream download ──────────

    @Nested
    @DisplayName("Flow C invariants — AC-13.5")
    class FlowCInvariants {

        @Test
        @DisplayName("AC-13.5: Flow C DOES NOT invoke muxerFactory — throwing factory succeeds")
        void download_flowC_throwingMuxerFactory_succeeds() {
            AtomicBoolean factoryCalled = new AtomicBoolean(false);
            Function<DownloadRequest, FfmpegMuxer> witnessFactory = req -> {
                factoryCalled.set(true);
                throw new AssertionError("muxerFactory invoked in Flow C");
            };

            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp(loadFixture("/fixtures/innertube-response-happy.json"))),
                    new FormatSelector(),
                    throwingStreamDownloader(),
                    witnessFactory,
                    new CaptionDownloader(captionHttp(TIMEDTEXT_XML)),
                    ThumbnailDownloader.create());

            DownloadResult result = sut.download(flowCRequest(Optional.empty(), false, false));

            assertThat(factoryCalled).isFalse();
            assertThat(result.srtPath()).isPresent();
        }

        @Test
        @DisplayName("Flow C DOES NOT download video/audio streams — throwing StreamDownloader succeeds")
        void download_flowC_throwingStreamDownloader_succeeds() {
            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp(loadFixture("/fixtures/innertube-response-happy.json"))),
                    new FormatSelector(),
                    throwingStreamDownloader(),
                    THROWING_MUXER_FACTORY,
                    new CaptionDownloader(captionHttp(TIMEDTEXT_XML)),
                    ThumbnailDownloader.create());

            DownloadResult result = sut.download(flowCRequest(Optional.empty(), false, false));

            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
            assertThat(result.videoPath()).isEmpty();
            assertThat(result.audioPath()).isEmpty();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private YoutubeDownloader buildFlowCSut(String innerTubeFixture, String captionXml) {
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp(innerTubeFixture)),
                new FormatSelector(),
                throwingStreamDownloader(),
                THROWING_MUXER_FACTORY,
                new CaptionDownloader(captionHttp(captionXml)),
                ThumbnailDownloader.create());
    }

    private YoutubeDownloader buildFlowCSutWithThumbnail(String innerTubeFixture,
                                                         String captionXml,
                                                         byte[] thumbnailBytes) {
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp(innerTubeFixture)),
                new FormatSelector(),
                throwingStreamDownloader(),
                THROWING_MUXER_FACTORY,
                new CaptionDownloader(captionHttp(captionXml)),
                new ThumbnailDownloader(interceptorReturning(200, thumbnailBytes)));
    }

    private YoutubeDownloader buildFlowBSut(String innerTubeFixture, String captionXml) {
        byte[] fakeAudio = {0x00, 0x01, 0x02, 0x03};
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp(innerTubeFixture)),
                new FormatSelector(),
                new StreamDownloader(interceptorReturning(200, fakeAudio)),
                req -> req.ffmpegLocation().map(FfmpegMuxer::new).orElseGet(FfmpegMuxer::new),
                new CaptionDownloader(captionHttp(captionXml)),
                ThumbnailDownloader.create());
    }

    private YoutubeDownloader buildFlowBSutWithFfmpeg(String innerTubeFixture,
                                                      String captionXml,
                                                      Path fakeFfmpeg) {
        byte[] fakeAudio = {0x00, 0x01, 0x02, 0x03};
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp(innerTubeFixture)),
                new FormatSelector(),
                new StreamDownloader(interceptorReturning(200, fakeAudio)),
                req -> new FfmpegMuxer(fakeFfmpeg.toString()),
                new CaptionDownloader(captionHttp(captionXml)),
                ThumbnailDownloader.create());
    }

    private YoutubeDownloader buildFlowASut(String innerTubeFixture, Path fakeFfmpeg) {
        byte[] fakeMedia = {0x00, 0x01, 0x02, 0x03};
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp(innerTubeFixture)),
                new FormatSelector(),
                new StreamDownloader(interceptorReturning(200, fakeMedia)),
                req -> new FfmpegMuxer(fakeFfmpeg.toString()),
                new CaptionDownloader(captionHttp(TIMEDTEXT_XML)),
                ThumbnailDownloader.create());
    }

    private DownloadRequest flowCRequest(Optional<String> lang, boolean noAsr, boolean thumbnail) {
        return new DownloadRequest(
                VALID_URL, false, AudioFormat.M4A, 1080, Optional.empty(),
                true, lang, noAsr,
                outputDir(), ProgressListener.NO_OP, false, thumbnail, false);
    }

    private OutputConfig outputDir() {
        return new OutputConfig(Optional.empty(), Optional.of(tempDir), false);
    }

    private StreamDownloader throwingStreamDownloader() {
        OkHttpClient throwingHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    throw new AssertionError("StreamDownloader must not be invoked in Flow C");
                })
                .build();
        return new StreamDownloader(throwingHttp);
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

    private static OkHttpClient fakeInnerTubeHttp(String fixture) {
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

    private static OkHttpClient captionHttp(String xml) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(xml, MediaType.get("text/xml")))
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
        try (InputStream is = YoutubeDownloaderFlowCBehaviorTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }
}
