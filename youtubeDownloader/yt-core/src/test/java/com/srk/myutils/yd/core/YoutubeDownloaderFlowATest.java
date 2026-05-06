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
 * Characterization test for Flow A — video + audio download + mux → .mp4
 * (T-3.8, AC-1.6, state-machine flow A).
 *
 * <p>Happy-path only: InnerTubeClient returns the canned fixture via
 * OkHttp interceptor. StreamDownloader returns fake bytes. FfmpegMuxer
 * is replaced by a fake that writes canned bytes to the output path.
 * Verifies {@link DownloadResult#videoPath()} is populated and temp dir
 * is cleaned (INV-6).
 */
class YoutubeDownloaderFlowATest {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_VIDEO = {0x00, 0x01, 0x02, 0x03};
    private static final byte[] FAKE_AUDIO = {0x04, 0x05, 0x06, 0x07};

    @TempDir
    Path tempDir;

    @Test
    void download_flowA_happyPath_writesMp4AndPopulatesVideoPath() {
        OkHttpClient innerTubeHttp = interceptorReturning(200,
                loadFixture("/fixtures/innertube-response-happy.json"));

        // StreamDownloader returns fake bytes for both video and audio
        OkHttpClient streamHttp = interceptorReturning(200, FAKE_VIDEO);

        YoutubeDownloader sut = new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(innerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(streamHttp));

        OutputConfig output = new OutputConfig(Optional.empty(), Optional.of(tempDir), false);
        DownloadRequest request = new DownloadRequest(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                false,
                AudioFormat.M4A,
                1080,
                Optional.of(fakeFfmpegPath()),
                false,
                Optional.empty(),
                false,
                output,
                ProgressListener.NO_OP,
                false);

        DownloadResult result = sut.download(request);

        assertThat(result.videoPath()).isPresent();
        assertThat(result.videoPath().get().getFileName().toString()).endsWith(".mp4");
        assertThat(Files.exists(result.videoPath().get())).isTrue();
        assertThat(result.audioPath()).isEmpty();
        assertThat(result.videoId().value()).isEqualTo("dQw4w9WgXcQ");

        // INV-6: .yt-tmp deleted after success
        Path ytTmp = tempDir.resolve(".yt-tmp");
        assertThat(Files.exists(ytTmp)).isFalse();
    }

    /**
     * Returns the path to a fake ffmpeg script that:
     * <ol>
     *   <li>Prints "ffmpeg version 7.1.0" for {@code -version}</li>
     *   <li>Copies input to output for mux invocations</li>
     * </ol>
     */
    private String fakeFfmpegPath() {
        try {
            Path script = tempDir.resolve("fake-ffmpeg");
            Files.writeString(script, """
                    #!/bin/sh
                    if [ "$1" = "-version" ]; then
                        echo "ffmpeg version 7.1.0 Copyright (c) 2000-2024 the FFmpeg developers"
                        exit 0
                    fi
                    # For mux: find the -y flag and the output path (last arg), write bytes
                    OUTPUT="${@: -1}"
                    printf '\\x00\\x01\\x02\\x03' > "$OUTPUT"
                    exit 0
                    """);
            script.toFile().setExecutable(true);
            return script.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create fake ffmpeg script", e);
        }
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
        try (InputStream is = YoutubeDownloaderFlowATest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }
}
