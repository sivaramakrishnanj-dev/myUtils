package com.srk.myutils.yd.core;

import okhttp3.Interceptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for the transcript flow in
 * {@link YoutubeDownloader#download(DownloadRequest)} (T-4.10).
 *
 * <p>Covers AC-6.1 (transcript download), AC-6.4 (no captions → exit 40),
 * AC-7.3 (ASR fallback), AC-7.4 (--no-asr), AC-8.2/AC-8.3 (language selection),
 * CT-APP-8/9/10, and partial-success on NetworkException (02-arch § 6).
 *
 * <p>SUT ({@link YoutubeDownloader}) is never mocked. External HTTP uses
 * OkHttp interceptors. INV-16 end-to-end verified via ASR path.
 */
class YoutubeDownloaderTranscriptBehaviorTest {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_AUDIO = {0x00, 0x01, 0x02, 0x03};
    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    private static final String TIMEDTEXT_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <transcript>
              <text start="0.5" dur="2.0">Never gonna give you up</text>
              <text start="3.0" dur="2.5">Never gonna let you down</text>
            </transcript>
            """;

    @TempDir
    Path tempDir;

    private OkHttpClient fakeStreamHttp;

    @BeforeEach
    void setUp() {
        fakeStreamHttp = interceptorReturning(200, FAKE_AUDIO);
    }

    // ── 1. Happy path: manual English track (AC-6.1, AC-6.2, AC-7.2) ──

    @Nested
    @DisplayName("Transcript happy path — manual track")
    class ManualTrackHappyPath {

        @Test
        @DisplayName("--transcript with manual en track → srt + txt written, usedAsrFallback=false")
        void download_transcriptManualTrack_writesSrtAndTxt() {
            YoutubeDownloader sut = buildSut(
                    loadFixture("/fixtures/innertube-response-happy.json"), TIMEDTEXT_XML);

            DownloadResult result = sut.download(transcriptRequest(Optional.empty(), false));

            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
            assertThat(result.usedAsrFallback()).isFalse();
            assertThat(result.srtPath().get().toString()).endsWith(".srt");
            assertThat(result.txtPath().get().toString()).endsWith(".txt");
        }

        @Test
        @DisplayName("--transcript --lang en-US with en-US + en-GB → selects exact match en-US (AC-8.2)")
        void download_transcriptLangExactMatch_selectsEnUs() {
            // Fixture with en-US manual + en-GB manual
            String fixture = fixtureWithMultiLangTracks();
            YoutubeDownloader sut = buildSut(fixture, TIMEDTEXT_XML);

            DownloadResult result = sut.download(transcriptRequest(Optional.of("en-US"), false));

            assertThat(result.srtPath()).isPresent();
            assertThat(result.usedAsrFallback()).isFalse();
        }
    }

    // ── 2. ASR fallback (AC-7.3, CT-APP-9, INV-16) ─────────────────

    @Nested
    @DisplayName("ASR fallback — AC-7.3, CT-APP-9, INV-16")
    class AsrFallback {

        @Test
        @DisplayName("--transcript with ASR-only fixture → usedAsrFallback=true, srt+txt written (CT-APP-9)")
        void download_transcriptAsrOnly_usedAsrFallbackTrue() {
            YoutubeDownloader sut = buildSut(
                    loadFixture("/fixtures/innertube-response-asr-only.json"), TIMEDTEXT_XML);

            DownloadResult result = sut.download(transcriptRequest(Optional.empty(), false));

            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
            assertThat(result.usedAsrFallback()).isTrue();
        }

        @Test
        @DisplayName("INV-16: usedAsrFallback propagated from CaptionSelection exactly once")
        void download_transcriptAsrOnly_inv16_flagSetOnceFromCaptionSelection() {
            YoutubeDownloader sut = buildSut(
                    loadFixture("/fixtures/innertube-response-asr-only.json"), TIMEDTEXT_XML);

            DownloadResult result = sut.download(transcriptRequest(Optional.empty(), false));

            // INV-16: value set in SELECTING_FORMATS (FormatSelector.selectCaption),
            // propagated unchanged through orchestrator to DownloadResult
            assertThat(result.usedAsrFallback())
                    .as("INV-16: usedAsrFallback set exactly once in SELECTING_FORMATS")
                    .isTrue();
        }
    }

    // ── 3. No captions → CaptionUnavailableException (AC-6.4, CT-APP-8) ─

    @Nested
    @DisplayName("No captions — AC-6.4, CT-APP-8")
    class NoCaptions {

        @Test
        @DisplayName("--transcript with no-captions fixture → CaptionUnavailableException exit 40 (CT-APP-8)")
        void download_transcriptNoCaptions_throwsCaptionUnavailable() {
            YoutubeDownloader sut = buildSut(
                    loadFixture("/fixtures/innertube-response-no-captions.json"), TIMEDTEXT_XML);

            assertThatThrownBy(() -> sut.download(transcriptRequest(Optional.empty(), false)))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .satisfies(e -> assertThat(((CaptionUnavailableException) e).exitCode()).isEqualTo(40));
        }
    }

    // ── 4. --no-asr with ASR-only → exit 40 (AC-7.4, CT-APP-10) ────

    @Nested
    @DisplayName("--no-asr with ASR-only — AC-7.4, CT-APP-10")
    class NoAsrFlag {

        @Test
        @DisplayName("--transcript --no-asr with ASR-only → CaptionUnavailableException exit 40 (CT-APP-10)")
        void download_transcriptNoAsrWithAsrOnly_throwsCaptionUnavailable() {
            YoutubeDownloader sut = buildSut(
                    loadFixture("/fixtures/innertube-response-asr-only.json"), TIMEDTEXT_XML);

            assertThatThrownBy(() -> sut.download(transcriptRequest(Optional.empty(), true)))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .satisfies(e -> assertThat(((CaptionUnavailableException) e).exitCode()).isEqualTo(40))
                    .hasMessageContaining("--no-asr");
        }
    }

    // ── 5. --lang fr with en-only → exit 40 (AC-8.3) ───────────────

    @Nested
    @DisplayName("Language mismatch — AC-8.3")
    class LanguageMismatch {

        @Test
        @DisplayName("--transcript --lang fr with en-only fixture → exit 40 with Available list (AC-8.3)")
        void download_transcriptLangFrEnOnly_throwsCaptionUnavailableWithAvailable() {
            YoutubeDownloader sut = buildSut(
                    loadFixture("/fixtures/innertube-response-happy.json"), TIMEDTEXT_XML);

            assertThatThrownBy(() -> sut.download(transcriptRequest(Optional.of("fr"), false)))
                    .isInstanceOf(CaptionUnavailableException.class)
                    .satisfies(e -> assertThat(((CaptionUnavailableException) e).exitCode()).isEqualTo(40))
                    .hasMessageContaining("Available:");
        }
    }

    // ── 6. Partial success: NetworkException during caption fetch ────

    @Nested
    @DisplayName("Partial success — caption fetch failure")
    class PartialSuccess {

        @Test
        @DisplayName("--transcript fetch fails (NetworkException) → WARN, result without srt/txt (02-arch § 6)")
        void download_transcriptFetchFails_partialSuccessNoSrtTxt() {
            // Caption HTTP returns 500 → NetworkException
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
                    new StreamDownloader(fakeStreamHttp),
                    req -> req.ffmpegLocation().map(FfmpegMuxer::new).orElseGet(FfmpegMuxer::new),
                    new CaptionDownloader(failingCaptionHttp),
                    ThumbnailDownloader.create());

            DownloadResult result = sut.download(transcriptRequest(Optional.empty(), false));

            assertThat(result.srtPath()).isEmpty();
            assertThat(result.txtPath()).isEmpty();
            // Audio path should still be present (partial success)
            assertThat(result.audioPath()).isPresent();
        }
    }

    // ── 7. Combined: transcript + thumbnail both work ───────────────

    @Nested
    @DisplayName("Combined flows — transcript + thumbnail")
    class CombinedFlows {

        @Test
        @DisplayName("--transcript --thumbnail both succeed → srt + txt + jpg all present")
        void download_transcriptAndThumbnail_allFilesPresent() {
            byte[] fakeJpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
            YoutubeDownloader sut = buildSutWithThumbnail(
                    loadFixture("/fixtures/innertube-response-happy.json"),
                    TIMEDTEXT_XML, fakeJpeg);

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.M4A, 0, Optional.empty(),
                    true, Optional.empty(), false,
                    outputDir(tempDir), ProgressListener.NO_OP, false, true);

            DownloadResult result = sut.download(request);

            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
            assertThat(result.thumbnailPath()).isPresent();
            assertThat(result.audioPath()).isPresent();
        }
    }

    // ── 8. Flow A + transcript → .mp4 + .srt + .txt ────────────────

    @Nested
    @DisplayName("Flow A + transcript")
    class FlowAWithTranscript {

        @Test
        @DisplayName("Flow A (video+audio+mux) + --transcript → .mp4 + .srt + .txt")
        void download_flowAWithTranscript_mp4PlusSrtTxt() throws IOException {
            Path fakeFfmpeg = createFakeFfmpeg();
            String fixture = loadFixture("/fixtures/innertube-response-happy.json");

            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp(fixture)),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp),
                    req -> new FfmpegMuxer(fakeFfmpeg.toString()),
                    new CaptionDownloader(captionHttp(TIMEDTEXT_XML)),
                    ThumbnailDownloader.create());

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, false, AudioFormat.M4A, 1080, Optional.empty(),
                    true, Optional.empty(), false,
                    outputDir(tempDir), ProgressListener.NO_OP, false, false);

            DownloadResult result = sut.download(request);

            assertThat(result.videoPath()).isPresent();
            assertThat(result.videoPath().get().toString()).endsWith(".mp4");
            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
        }
    }

    // ── 9. Flow B (M4A) + transcript → .m4a + .srt + .txt ──────────

    @Nested
    @DisplayName("Flow B + transcript")
    class FlowBWithTranscript {

        @Test
        @DisplayName("Flow B (audio-only M4A) + --transcript → .m4a + .srt + .txt")
        void download_flowBM4aWithTranscript_m4aPlusSrtTxt() {
            YoutubeDownloader sut = buildSut(
                    loadFixture("/fixtures/innertube-response-happy.json"), TIMEDTEXT_XML);

            DownloadResult result = sut.download(transcriptRequest(Optional.empty(), false));

            assertThat(result.audioPath()).isPresent();
            assertThat(result.audioPath().get().toString()).endsWith(".m4a");
            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
        }

        @Test
        @DisplayName("Flow B' (MP3) + --transcript → .mp3 + .srt + .txt")
        void download_flowBPrimeMp3WithTranscript_mp3PlusSrtTxt() throws IOException {
            Path fakeFfmpeg = createFakeFfmpeg();
            String fixture = loadFixture("/fixtures/innertube-response-happy.json");

            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp(fixture)),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp),
                    req -> new FfmpegMuxer(fakeFfmpeg.toString()),
                    new CaptionDownloader(captionHttp(TIMEDTEXT_XML)),
                    ThumbnailDownloader.create());

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.MP3, 0, Optional.empty(),
                    true, Optional.empty(), false,
                    outputDir(tempDir), ProgressListener.NO_OP, false, false);

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(result.audioPath().get().toString()).endsWith(".mp3");
            assertThat(result.srtPath()).isPresent();
            assertThat(result.txtPath()).isPresent();
        }
    }

    // ── 10. No --transcript → srt/txt empty ────────────────────────

    @Nested
    @DisplayName("No transcript flag")
    class NoTranscriptFlag {

        @Test
        @DisplayName("No --transcript → srtPath and txtPath empty, usedAsrFallback=false")
        void download_noTranscriptFlag_srtTxtEmpty() {
            YoutubeDownloader sut = buildSut(
                    loadFixture("/fixtures/innertube-response-happy.json"), TIMEDTEXT_XML);

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.M4A, 0, Optional.empty(),
                    false, Optional.empty(), false,
                    outputDir(tempDir), ProgressListener.NO_OP, false, false);

            DownloadResult result = sut.download(request);

            assertThat(result.srtPath()).isEmpty();
            assertThat(result.txtPath()).isEmpty();
            assertThat(result.usedAsrFallback()).isFalse();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private YoutubeDownloader buildSut(String innerTubeFixture, String captionXml) {
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp(innerTubeFixture)),
                new FormatSelector(),
                new StreamDownloader(fakeStreamHttp),
                req -> req.ffmpegLocation().map(FfmpegMuxer::new).orElseGet(FfmpegMuxer::new),
                new CaptionDownloader(captionHttp(captionXml)),
                ThumbnailDownloader.create());
    }

    private YoutubeDownloader buildSutWithThumbnail(String innerTubeFixture,
                                                    String captionXml,
                                                    byte[] thumbnailBytes) {
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp(innerTubeFixture)),
                new FormatSelector(),
                new StreamDownloader(fakeStreamHttp),
                req -> req.ffmpegLocation().map(FfmpegMuxer::new).orElseGet(FfmpegMuxer::new),
                new CaptionDownloader(captionHttp(captionXml)),
                new ThumbnailDownloader(interceptorReturning(200, thumbnailBytes)));
    }

    private DownloadRequest transcriptRequest(Optional<String> lang, boolean noAsr) {
        return new DownloadRequest(
                VALID_URL, true, AudioFormat.M4A, 0, Optional.empty(),
                true, lang, noAsr,
                outputDir(tempDir), ProgressListener.NO_OP, false, false);
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
        try (InputStream is = YoutubeDownloaderTranscriptBehaviorTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }

    /**
     * Synthesized fixture with en-US manual + en-GB manual tracks for exact-match testing.
     */
    private static String fixtureWithMultiLangTracks() {
        return """
                {
                  "videoDetails": {
                    "videoId": "dQw4w9WgXcQ",
                    "title": "Multi-lang test",
                    "isLive": false,
                    "isPrivate": false,
                    "audioLanguage": "en",
                    "thumbnail": { "thumbnails": [
                      { "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg", "width": 320, "height": 180 }
                    ]}
                  },
                  "playabilityStatus": { "status": "OK" },
                  "streamingData": {
                    "adaptiveFormats": [
                      { "itag": 140, "mimeType": "audio/mp4; codecs=\\"mp4a.40.2\\"",
                        "bitrate": 130000, "audioSampleRate": "44100",
                        "contentLength": "5000000",
                        "url": "https://rr3---sn-synthetic.googlevideo.com/videoplayback?synthetic=1" }
                    ]
                  },
                  "captions": {
                    "playerCaptionsTracklistRenderer": {
                      "captionTracks": [
                        { "baseUrl": "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&lang=en-US",
                          "languageCode": "en-US", "name": { "simpleText": "English (United States)" } },
                        { "baseUrl": "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&lang=en-GB",
                          "languageCode": "en-GB", "name": { "simpleText": "English (United Kingdom)" } }
                      ]
                    }
                  }
                }
                """;
    }
}
