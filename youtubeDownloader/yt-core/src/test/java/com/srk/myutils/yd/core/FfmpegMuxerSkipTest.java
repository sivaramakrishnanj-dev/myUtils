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
 * Characterization test for T-3.10 (AC-13.5, INV-10): audio-only M4A
 * download succeeds even when ffmpeg is unavailable — probeVersion is
 * never invoked on the M4A path.
 *
 * <p>Uses a muxer factory that throws {@link AssertionError} if called,
 * proving the orchestrator never touches ffmpeg for M4A downloads.
 */
class FfmpegMuxerSkipTest {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_AUDIO = {0x00, 0x01, 0x02, 0x03};

    @TempDir
    Path tempDir;

    @Test
    void download_audioOnlyM4a_succeedsWithoutInvokingFfmpeg() {
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

        // Muxer factory that explodes if ever called — proves M4A path skips ffmpeg entirely
        YoutubeDownloader sut = new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(innerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(streamHttp),
                req -> { throw new AssertionError("muxerFactory must not be invoked for M4A path"); });

        DownloadRequest request = new DownloadRequest(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                true,
                AudioFormat.M4A,
                0,
                Optional.of("/nonexistent/ffmpeg"),
                new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                ProgressListener.NO_OP,
                false);

        DownloadResult result = sut.download(request);

        assertThat(result.audioPath()).isPresent();
        assertThat(result.audioPath().get().getFileName().toString()).endsWith(".m4a");
        assertThat(Files.exists(result.audioPath().get())).isTrue();
    }

    private static String loadFixture(String resourcePath) {
        try (InputStream is = FfmpegMuxerSkipTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }
}
