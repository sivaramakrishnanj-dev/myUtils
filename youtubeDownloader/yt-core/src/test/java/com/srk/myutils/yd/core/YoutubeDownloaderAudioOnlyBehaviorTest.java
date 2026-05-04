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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for the audio-only download path in
 * {@link YoutubeDownloader#download(DownloadRequest)} (T-2.10).
 *
 * <p>Covers AC-2.1 (audio-only flag), AC-2.3 (default .m4a output),
 * AC-3.1..AC-3.6 (output paths, sanitization, truncation, overwrite),
 * INV-6 (.yt-tmp lifecycle), and error paths (exit 10, 22, 30, 50).
 *
 * <p>SUT is not mocked. {@link UrlParser} and {@link FormatSelector} are real.
 * {@link InnerTubeClient} and {@link StreamDownloader} use OkHttp interceptors
 * returning canned responses — no network I/O.
 */
class YoutubeDownloaderAudioOnlyBehaviorTest {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_AUDIO = {0x00, 0x01, 0x02, 0x03};
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

    // ── 1. Happy path (AC-2.1, AC-2.3) ─────────────────────────────

    @Nested
    @DisplayName("Happy path — AC-2.1, AC-2.3")
    class HappyPath {

        @Test
        @DisplayName("audio-only request writes .m4a, populates audioPath, no videoPath")
        void download_audioOnly_writesM4aAndPopulatesAudioPath() throws IOException {
            YoutubeDownloader sut = buildSut();
            DownloadRequest request = audioOnlyRequest(outputDir(tempDir));

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(result.audioPath().get().getFileName().toString()).endsWith(".m4a");
            assertThat(Files.exists(result.audioPath().get())).isTrue();
            assertThat(Files.readAllBytes(result.audioPath().get())).isEqualTo(FAKE_AUDIO);
            assertThat(result.videoPath()).isEmpty();
            assertThat(result.videoId().value()).isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        @DisplayName("DownloadContext .yt-tmp deleted after successful download (INV-6)")
        void download_audioOnly_success_tempDirCleaned() {
            YoutubeDownloader sut = buildSut();
            DownloadRequest request = audioOnlyRequest(outputDir(tempDir));

            sut.download(request);

            Path ytTmp = tempDir.resolve(".yt-tmp");
            assertThat(Files.exists(ytTmp)).isFalse();
        }
    }

    // ── 2. Output exists — AC-3.6 ──────────────────────────────────

    @Nested
    @DisplayName("Output exists — AC-3.6")
    class OutputExists {

        @Test
        @DisplayName("target .m4a exists, force=false → OutputExistsException (exit 50)")
        void download_audioOnly_targetExists_noForce_throwsOutputExistsException() throws IOException {
            YoutubeDownloader sut = buildSut();
            // Pre-create the expected output file
            String expectedName = "Rick Astley - Never Gonna Give You Up (Official Music Video) [dQw4w9WgXcQ].m4a";
            Files.write(tempDir.resolve(expectedName), new byte[]{0x42});

            DownloadRequest request = audioOnlyRequest(outputDir(tempDir));

            assertThatThrownBy(() -> sut.download(request))
                    .isInstanceOf(OutputExistsException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(50));
        }

        @Test
        @DisplayName("target exists, force=true → file overwritten")
        void download_audioOnly_targetExists_force_overwritesFile() throws IOException {
            YoutubeDownloader sut = buildSut();
            String expectedName = "Rick Astley - Never Gonna Give You Up (Official Music Video) [dQw4w9WgXcQ].m4a";
            Path existing = tempDir.resolve(expectedName);
            Files.write(existing, new byte[]{0x42});

            OutputConfig output = new OutputConfig(Optional.empty(), Optional.of(tempDir), true);
            DownloadRequest request = new DownloadRequest(VALID_URL, true, 0, output, ProgressListener.NO_OP);

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(Files.readAllBytes(result.audioPath().get())).isEqualTo(FAKE_AUDIO);
        }
    }

    // ── 3. StreamDownloader failure — NetworkException (exit 10) ────

    @Nested
    @DisplayName("StreamDownloader failure — NetworkException")
    class StreamFailure {

        @Test
        @DisplayName("StreamDownloader throws NetworkException → propagates, .yt-tmp RETAINED (INV-6)")
        void download_audioOnly_streamFails_propagatesAndRetainsTempDir() {
            OkHttpClient failingStreamHttp = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        throw new IOException("simulated network failure");
                    })
                    .build();
            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(fakeInnerTubeHttp),
                    new FormatSelector(),
                    new StreamDownloader(failingStreamHttp, millis -> {}));

            DownloadRequest request = audioOnlyRequest(outputDir(tempDir));

            assertThatThrownBy(() -> sut.download(request))
                    .isInstanceOf(NetworkException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(10));

            // INV-6: .yt-tmp retained on failure
            Path ytTmp = tempDir.resolve(".yt-tmp");
            assertThat(Files.exists(ytTmp)).isTrue();
        }
    }

    // ── 4. No audio format → NoMatchingFormatException (exit 30) ───

    @Nested
    @DisplayName("No audio format — exit 30")
    class NoAudioFormat {

        @Test
        @DisplayName("response with only video formats → NoMatchingFormatException (exit 30)")
        void download_audioOnly_noAudioFormats_throwsNoMatchingFormatException() {
            // Build a fixture with only video formats (no audio)
            String videoOnlyFixture = """
                    {
                      "videoDetails": {
                        "videoId": "dQw4w9WgXcQ",
                        "title": "Test Video",
                        "isLive": false,
                        "isPrivate": false,
                        "thumbnail": { "thumbnails": [{"url":"u","width":1,"height":1}] }
                      },
                      "playabilityStatus": { "status": "OK" },
                      "streamingData": {
                        "adaptiveFormats": [
                          {
                            "itag": 137,
                            "mimeType": "video/mp4; codecs=\\"avc1.640028\\"",
                            "bitrate": 4500000,
                            "width": 1920,
                            "height": 1080,
                            "fps": 30,
                            "contentLength": "95000000",
                            "url": "https://cdn.example.com/video",
                            "signatureCipher": ""
                          }
                        ]
                      }
                    }""";

            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(interceptorReturning(200, videoOnlyFixture)),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp));

            DownloadRequest request = audioOnlyRequest(outputDir(tempDir));

            assertThatThrownBy(() -> sut.download(request))
                    .isInstanceOf(NoMatchingFormatException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(30));
        }
    }

    // ── 5. Cipher-only audio → CipherRequiredException (exit 22) ───

    @Nested
    @DisplayName("Cipher-only audio — AC-5.3, exit 22")
    class CipherOnly {

        @Test
        @DisplayName("all audio formats cipher-protected → CipherRequiredException (exit 22)")
        void download_audioOnly_cipherOnly_throwsCipherRequiredException() {
            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(interceptorReturning(200,
                            loadFixture("/fixtures/innertube-response-cipher.json"))),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp));

            DownloadRequest request = new DownloadRequest(
                    "https://www.youtube.com/watch?v=aaaaaaaaaaa",
                    true,
                    0,
                    outputDir(tempDir),
                    ProgressListener.NO_OP);

            assertThatThrownBy(() -> sut.download(request))
                    .isInstanceOf(CipherRequiredException.class)
                    .satisfies(e -> assertThat(((YoutubeDownloaderException) e).exitCode()).isEqualTo(22));
        }
    }

    // ── 6. Filename sanitization — AC-3.3 ──────────────────────────

    @Nested
    @DisplayName("Filename sanitization — AC-3.3")
    class FilenameSanitization {

        @Test
        @DisplayName("title with '/' characters → sanitized in output path")
        void download_audioOnly_titleWithSlash_sanitizedFilename() {
            String fixtureWithSlash = fixtureWithTitle("Video / With / Slashes");
            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(interceptorReturning(200, fixtureWithSlash)),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp));

            DownloadRequest request = audioOnlyRequest(outputDir(tempDir));

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            String filename = result.audioPath().get().getFileName().toString();
            assertThat(filename).doesNotContain("/");
            assertThat(filename).endsWith(".m4a");
            assertThat(filename).contains("[dQw4w9WgXcQ]");
        }
    }

    // ── 7. Filename truncation — AC-3.4 ────────────────────────────

    @Nested
    @DisplayName("Filename truncation — AC-3.4")
    class FilenameTruncation {

        @Test
        @DisplayName("very long title → truncated, [videoId] preserved")
        void download_audioOnly_longTitle_truncatedWithVideoIdPreserved() {
            String longTitle = "A".repeat(300);
            String fixtureWithLongTitle = fixtureWithTitle(longTitle);
            YoutubeDownloader sut = new YoutubeDownloader(
                    new UrlParser(),
                    new InnerTubeClient(interceptorReturning(200, fixtureWithLongTitle)),
                    new FormatSelector(),
                    new StreamDownloader(fakeStreamHttp));

            DownloadRequest request = audioOnlyRequest(outputDir(tempDir));

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            String filename = result.audioPath().get().getFileName().toString();
            // Base name (without .m4a) should be ≤ 200 chars per NFR-MAX-FILENAME-LENGTH
            String baseName = filename.substring(0, filename.lastIndexOf('.'));
            assertThat(baseName.length()).isLessThanOrEqualTo(200);
            assertThat(baseName).endsWith("[dQw4w9WgXcQ]");
        }
    }

    // ── 8. Progress listener — AC-4.1, AC-9.3 ─────────────────────

    @Nested
    @DisplayName("Progress listener — AC-4.1, AC-9.3")
    class ProgressListenerTests {

        @Test
        @DisplayName("progress listener invoked during download")
        void download_audioOnly_progressListenerInvoked() {
            YoutubeDownloader sut = buildSut();
            List<long[]> progressEvents = new ArrayList<>();
            ProgressListener capturing = (bytesWritten, totalBytes) ->
                    progressEvents.add(new long[]{bytesWritten, totalBytes});

            DownloadRequest request = new DownloadRequest(
                    VALID_URL, true, 0, outputDir(tempDir), capturing);

            sut.download(request);

            assertThat(progressEvents).isNotEmpty();
            // Last event should report all bytes written
            long[] last = progressEvents.get(progressEvents.size() - 1);
            assertThat(last[0]).isEqualTo(FAKE_AUDIO.length);
        }

        @Test
        @DisplayName("NO_OP listener — download still succeeds")
        void download_audioOnly_noOpListener_succeeds() {
            YoutubeDownloader sut = buildSut();
            DownloadRequest request = audioOnlyRequest(outputDir(tempDir));

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(Files.exists(result.audioPath().get())).isTrue();
        }
    }

    // ── 9. Output path scenarios — AC-3.1, AC-3.2, AC-3.5 ─────────

    @Nested
    @DisplayName("Output path scenarios — AC-3.1, AC-3.2, AC-3.5")
    class OutputPathScenarios {

        @Test
        @DisplayName("--output-dir → file written under specified directory (AC-3.2)")
        void download_audioOnly_outputDir_writesUnderSpecifiedDir() {
            YoutubeDownloader sut = buildSut();
            Path subDir = tempDir.resolve("my-downloads");

            OutputConfig output = new OutputConfig(Optional.empty(), Optional.of(subDir), false);
            DownloadRequest request = new DownloadRequest(VALID_URL, true, 0, output, ProgressListener.NO_OP);

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            assertThat(result.audioPath().get().getParent()).isEqualTo(subDir);
        }

        @Test
        @DisplayName("--output <name> → uses literal name with .m4a extension (AC-3.5)")
        void download_audioOnly_outputPath_usesLiteralName() {
            YoutubeDownloader sut = buildSut();
            Path outputFile = tempDir.resolve("my-audio.wav");

            OutputConfig output = new OutputConfig(Optional.of(outputFile), Optional.empty(), false);
            DownloadRequest request = new DownloadRequest(VALID_URL, true, 0, output, ProgressListener.NO_OP);

            DownloadResult result = sut.download(request);

            assertThat(result.audioPath()).isPresent();
            // AC-3.5: extension stripped and replaced with .m4a
            assertThat(result.audioPath().get().getFileName().toString()).isEqualTo("my-audio.m4a");
        }
    }

    // ── 10. DownloadRequest.audioOnly factory — AC-2.1 ─────────────

    @Nested
    @DisplayName("DownloadRequest.audioOnly factory")
    class AudioOnlyFactory {

        @Test
        @DisplayName("static factory produces equivalent result to manual construction")
        void audioOnlyFactory_producesEquivalentResult() {
            YoutubeDownloader sut = buildSut();
            OutputConfig output = outputDir(tempDir);

            DownloadRequest factoryRequest = DownloadRequest.audioOnly(VALID_URL, output);

            assertThat(factoryRequest.audioOnly()).isTrue();
            assertThat(factoryRequest.listener()).isSameAs(ProgressListener.NO_OP);

            DownloadResult result = sut.download(factoryRequest);

            assertThat(result.audioPath()).isPresent();
            assertThat(result.audioPath().get().getFileName().toString()).endsWith(".m4a");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────

    private YoutubeDownloader buildSut() {
        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(fakeStreamHttp));
    }

    private static DownloadRequest audioOnlyRequest(OutputConfig output) {
        return new DownloadRequest(VALID_URL, true, 0, output, ProgressListener.NO_OP);
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
        try (InputStream is = YoutubeDownloaderAudioOnlyBehaviorTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }

    /**
     * Builds a minimal valid fixture JSON with a custom title and the standard
     * audio format (itag 140). Used for sanitization/truncation tests.
     */
    private static String fixtureWithTitle(String title) {
        String escapedTitle = title.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {
                  "videoDetails": {
                    "videoId": "dQw4w9WgXcQ",
                    "title": "%s",
                    "isLive": false,
                    "isPrivate": false,
                    "thumbnail": { "thumbnails": [{"url":"u","width":1,"height":1}] }
                  },
                  "playabilityStatus": { "status": "OK" },
                  "streamingData": {
                    "adaptiveFormats": [
                      {
                        "itag": 140,
                        "mimeType": "audio/mp4; codecs=\\"mp4a.40.2\\"",
                        "bitrate": 130000,
                        "audioSampleRate": "44100",
                        "contentLength": "5000000",
                        "url": "https://cdn.example.com/audio",
                        "signatureCipher": ""
                      }
                    ]
                  }
                }""".formatted(escapedTitle);
    }
}
