package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
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
 * Characterization test for the audio-only download path in
 * {@link YoutubeDownloader#download(DownloadRequest)} (T-2.10).
 *
 * <p>Happy-path only: InnerTubeClient returns the canned fixture via
 * OkHttp interceptor. StreamDownloader uses a second interceptor that
 * returns fake audio bytes. Verifies the final {@code .m4a} file exists
 * and {@link DownloadResult#audioPath()} is populated.
 */
class YoutubeDownloaderAudioOnlyTest {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_AUDIO = {0x00, 0x01, 0x02, 0x03};

    @TempDir
    Path tempDir;

    @Test
    void download_audioOnly_happyPath_writesM4aAndReturnsAudioPath() {
        // Fake InnerTubeClient — returns happy fixture
        OkHttpClient innerTubeHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(
                                loadFixture("/fixtures/innertube-response-happy.json"), JSON))
                        .build())
                .build();

        // Fake StreamDownloader HTTP — returns fake audio bytes
        OkHttpClient streamHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Length", String.valueOf(FAKE_AUDIO.length))
                        .body(ResponseBody.create(FAKE_AUDIO, OCTET))
                        .build())
                .build();

        YoutubeDownloader sut = new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(innerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(streamHttp));

        OutputConfig output = new OutputConfig(
                Optional.empty(),
                Optional.of(tempDir),
                false);

        DownloadRequest request = new DownloadRequest(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                true,
                0,
                output,
                ProgressListener.NO_OP);

        DownloadResult result = sut.download(request);

        assertThat(result.audioPath()).isPresent();
        assertThat(result.audioPath().get().getFileName().toString()).endsWith(".m4a");
        assertThat(Files.exists(result.audioPath().get())).isTrue();
        assertThat(result.videoPath()).isEmpty();
        assertThat(result.videoId().value()).isEqualTo("dQw4w9WgXcQ");
    }

    private static String loadFixture(String resourcePath) {
        try (InputStream is = YoutubeDownloaderAudioOnlyTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }
}
