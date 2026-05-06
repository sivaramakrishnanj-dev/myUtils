package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
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
 * Characterization test for Flow B' — audio-only + MP3 transcode (AC-2.4, T-3.9).
 *
 * <p>Verifies: audio downloads, ffmpeg transcode invoked, output is .mp3,
 * .yt-tmp cleaned up on success.
 */
class YoutubeDownloaderMp3Test {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_AUDIO = {0x00, 0x01, 0x02, 0x03};
    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @TempDir
    Path tempDir;

    private Path fakeFfmpegScript;

    @BeforeEach
    void setUp() throws IOException {
        fakeFfmpegScript = tempDir.resolve("fake-ffmpeg");
        Files.writeString(fakeFfmpegScript, """
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
        fakeFfmpegScript.toFile().setExecutable(true);
    }

    @Test
    @DisplayName("--audio-only --audio-format mp3: downloads audio, transcodes to .mp3, cleans .yt-tmp")
    void download_audioOnlyMp3_transcodesAndWritesMp3() {
        OkHttpClient innerTubeHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(loadFixture("/fixtures/innertube-response-happy.json"), JSON))
                        .build())
                .build();

        OkHttpClient streamHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(FAKE_AUDIO, OCTET))
                        .build())
                .build();

        YoutubeDownloader sut = new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(innerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(streamHttp),
                req -> new FfmpegMuxer(fakeFfmpegScript.toString(), 30));

        OutputConfig output = new OutputConfig(Optional.empty(), Optional.of(tempDir), false);
        DownloadRequest request = new DownloadRequest(
                VALID_URL, true, AudioFormat.MP3, 0,
                Optional.of(fakeFfmpegScript.toString()),
                false, Optional.empty(), false,
                output, ProgressListener.NO_OP, false, false);

        DownloadResult result = sut.download(request);

        assertThat(result.audioPath()).isPresent();
        assertThat(result.audioPath().get().getFileName().toString()).endsWith(".mp3");
        assertThat(Files.exists(result.audioPath().get())).isTrue();
        // .yt-tmp cleaned up on success (INV-6)
        assertThat(Files.exists(tempDir.resolve(".yt-tmp"))).isFalse();
    }

    private static String loadFixture(String resourcePath) {
        try (InputStream is = YoutubeDownloaderMp3Test.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }
}
