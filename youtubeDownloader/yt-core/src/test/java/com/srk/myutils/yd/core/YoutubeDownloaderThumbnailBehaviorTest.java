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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive behavior tests for the thumbnail flow in
 * {@link YoutubeDownloader#download(DownloadRequest)} (T-4.10).
 *
 * <p>Covers --thumbnail happy path, partial-success on NetworkException,
 * partial-success on empty thumbnails list, and DownloadResult.thumbnailPath
 * population when flag is/isn't set.
 *
 * <p>SUT ({@link YoutubeDownloader}) is never mocked. External HTTP uses
 * OkHttp interceptors.
 */
class YoutubeDownloaderThumbnailBehaviorTest {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_AUDIO = {0x00, 0x01, 0x02, 0x03};
    private static final byte[] FAKE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @TempDir
    Path tempDir;

    private OkHttpClient fakeStreamHttp;

    @BeforeEach
    void setUp() {
        fakeStreamHttp = interceptorReturning(200, FAKE_AUDIO);
    }

    // ── 1. Happy path: thumbnail downloaded (AC-9.1) ────────────────

    @Nested
    @DisplayName("Thumbnail happy path")
    class HappyPath {

        @Test
        @DisplayName("--thumbnail with happy fixture → .jpg written, thumbnailPath populated")
        void download_thumbnail_writesJpgAndPopulatesThumbnailPath() throws IOException {
            YoutubeDownloader sut = buildSut(
                    loadFixture("/fixtures/innertube-response-happy.json"), FAKE_JPEG);

            DownloadRequest request = thumbnailRequest();

            DownloadResult result = sut.download(request);

            assertThat(result.thumbnailPath()).isPresent();
            Path thumbFile = result.thumbnailPath().get();
            assertThat(thumbFile.toString()).endsWith(".jpg");
            assertThat(Files.exists(thumbFile)).isTrue();
            assertThat(Files.readAllBytes(thumbFile)).isEqualTo(FAKE_JPEG);
        }
    }

    // ── 2. Partial success: thumbnail fetch fails (NetworkException) ─

    @Nested
    @DisplayName("Partial success — thumbnail fetch failure")
    class FetchFailure {

        @Test
        @DisplayName("--thumbnail fetch fails (NetworkException) → WARN, result without thumbnailFile (02-arch § 6)")
        void download_thumbnailFetchFails_partialSuccessNoThumbnail() {
            OkHttpClient failingThumbnailHttp = new OkHttpClient.Builder()
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
                    CaptionDownloader.create(),
                    new ThumbnailDownloader(failingThumbnailHttp));

            DownloadRequest request = thumbnailRequest();

            DownloadResult result = sut.download(request);

            assertThat(result.thumbnailPath()).isEmpty();
            // Audio still succeeds (partial success)
            assertThat(result.audioPath()).isPresent();
        }
    }

    // ── 3. Partial success: empty thumbnails list ───────────────────

    @Nested
    @DisplayName("Partial success — empty thumbnails list")
    class EmptyThumbnails {

        @Test
        @DisplayName("--thumbnail with empty thumbnails list → WARN, result without thumbnailFile")
        void download_thumbnailEmptyList_partialSuccessNoThumbnail() {
            String fixtureNoThumbnails = fixtureWithEmptyThumbnails();
            YoutubeDownloader sut = buildSut(fixtureNoThumbnails, FAKE_JPEG);

            DownloadRequest request = thumbnailRequest();

            DownloadResult result = sut.download(request);

            assertThat(result.thumbnailPath()).isEmpty();
            assertThat(result.audioPath()).isPresent();
        }
    }

    // ── 4. No --thumbnail flag → thumbnailPath empty ────────────────

    @Nested
    @DisplayName("No thumbnail flag")
    class NoThumbnailFlag {

        @Test
        @DisplayName("No --thumbnail → thumbnailPath empty")
        void download_noThumbnailFlag_thumbnailPathEmpty() {
            YoutubeDownloader sut = buildSut(
                    loadFixture("/fixtures/innertube-response-happy.json"), FAKE_JPEG);

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, AudioFormat.M4A, 0, Optional.empty(),
                    false, Optional.empty(), false,
                    outputDir(tempDir), ProgressListener.NO_OP, false, false);

            DownloadResult result = sut.download(request);

            assertThat(result.thumbnailPath()).isEmpty();
        }
    }

    // ── 5. --thumbnail only (no --transcript) → audio + .jpg ────────

    @Nested
    @DisplayName("Thumbnail only — no transcript")
    class ThumbnailOnly {

        @Test
        @DisplayName("--thumbnail only (no --transcript, audio-only) → .m4a + .jpg")
        void download_thumbnailOnlyNoTranscript_audioAndJpg() {
            YoutubeDownloader sut = buildSut(
                    loadFixture("/fixtures/innertube-response-happy.json"), FAKE_JPEG);

            DownloadRequest request = thumbnailRequest();

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(result.thumbnailPath()).isPresent();
            assertThat(result.srtPath()).isEmpty();
            assertThat(result.txtPath()).isEmpty();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private YoutubeDownloader buildSut(String innerTubeFixture, byte[] thumbnailBytes) {
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp(innerTubeFixture)),
                new FormatSelector(),
                new StreamDownloader(fakeStreamHttp),
                req -> req.ffmpegLocation().map(FfmpegMuxer::new).orElseGet(FfmpegMuxer::new),
                CaptionDownloader.create(),
                new ThumbnailDownloader(interceptorReturning(200, thumbnailBytes)));
    }

    private DownloadRequest thumbnailRequest() {
        return new DownloadRequest(
                VALID_URL, true, AudioFormat.M4A, 0, Optional.empty(),
                false, Optional.empty(), false,
                outputDir(tempDir), ProgressListener.NO_OP, false, true);
    }

    private static OutputConfig outputDir(Path dir) {
        return new OutputConfig(Optional.empty(), Optional.of(dir), false);
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
        try (InputStream is = YoutubeDownloaderThumbnailBehaviorTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }

    /**
     * Synthesized fixture with empty thumbnails list for partial-success testing.
     */
    private static String fixtureWithEmptyThumbnails() {
        return """
                {
                  "videoDetails": {
                    "videoId": "dQw4w9WgXcQ",
                    "title": "No thumbnails test",
                    "isLive": false,
                    "isPrivate": false,
                    "audioLanguage": "en",
                    "thumbnail": { "thumbnails": [] }
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
                        { "baseUrl": "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&lang=en",
                          "languageCode": "en", "name": { "simpleText": "English" } }
                      ]
                    }
                  }
                }
                """;
    }
}
