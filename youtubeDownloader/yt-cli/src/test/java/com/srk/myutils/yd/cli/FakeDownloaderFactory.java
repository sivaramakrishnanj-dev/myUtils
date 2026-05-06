package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.CaptionDownloader;
import com.srk.myutils.yd.core.FfmpegMuxer;
import com.srk.myutils.yd.core.FormatSelector;
import com.srk.myutils.yd.core.InnerTubeClient;
import com.srk.myutils.yd.core.StreamDownloader;
import com.srk.myutils.yd.core.ThumbnailDownloader;
import com.srk.myutils.yd.core.UrlParser;
import com.srk.myutils.yd.core.YoutubeDownloader;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds a {@link YoutubeDownloader} backed by a canned InnerTube response
 * and fake stream/ffmpeg/caption/thumbnail for CLI tests that need
 * valid-URL → exit 0 without network I/O or real ffmpeg.
 */
final class FakeDownloaderFactory {

    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_BYTES = {0x00, 0x01, 0x02, 0x03};

    private static final String FAKE_TIMEDTEXT_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <transcript>
              <text start="0.5" dur="2.0">Hello world</text>
            </transcript>
            """;

    private static final byte[] FAKE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    private FakeDownloaderFactory() { }

    /** Returns a {@link YoutubeDownloader} that handles any video URL end-to-end with fakes. */
    static YoutubeDownloader happyPath() {
        String fixture = loadFixture("/fixtures/innertube-response-happy.json");

        OkHttpClient fakeInnerTubeHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(fixture,
                                MediaType.get("application/json")))
                        .build())
                .build();

        OkHttpClient fakeStreamHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Length", String.valueOf(FAKE_BYTES.length))
                        .body(ResponseBody.create(FAKE_BYTES, OCTET))
                        .build())
                .build();

        OkHttpClient fakeCaptionHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(FAKE_TIMEDTEXT_XML,
                                MediaType.get("text/xml")))
                        .build())
                .build();

        OkHttpClient fakeThumbnailHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(FAKE_JPEG, OCTET))
                        .build())
                .build();

        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeInnerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(fakeStreamHttp),
                req -> new FfmpegMuxer(fakeFfmpegPath()),
                new CaptionDownloader(fakeCaptionHttp),
                new ThumbnailDownloader(fakeThumbnailHttp));
    }

    /**
     * Creates a temporary fake ffmpeg shell script that prints a valid version
     * for {@code -version} and exits 0 for mux/transcode invocations.
     */
    private static String fakeFfmpegPath() {
        try {
            Path script = Files.createTempFile("fake-ffmpeg-", ".sh");
            Files.writeString(script, """
                    #!/bin/sh
                    if [ "$1" = "-version" ]; then
                        echo "ffmpeg version 7.1.0 Copyright (c) 2000-2024 the FFmpeg developers"
                        exit 0
                    fi
                    # For mux/transcode: touch the output file (last arg) so it exists
                    OUTPUT="${@: -1}"
                    touch "$OUTPUT"
                    exit 0
                    """);
            script.toFile().setExecutable(true);
            script.toFile().deleteOnExit();
            return script.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create fake ffmpeg script", e);
        }
    }

    private static String loadFixture(String resourcePath) {
        try (InputStream is = FakeDownloaderFactory.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }
}
