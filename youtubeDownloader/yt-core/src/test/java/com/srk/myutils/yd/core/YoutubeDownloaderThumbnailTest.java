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
 * Characterization test for {@link YoutubeDownloader} thumbnail integration —
 * happy-path: thumbnail downloaded to .jpg alongside the media file.
 */
class YoutubeDownloaderThumbnailTest {

    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_BYTES = {0x00, 0x01, 0x02, 0x03};
    private static final byte[] FAKE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @TempDir
    Path tempDir;

    @Test
    void download_withThumbnail_producesJpgFile() throws IOException {
        String fixture = loadFixture("/fixtures/innertube-response-happy.json");

        OkHttpClient fakeHttp = new OkHttpClient.Builder()
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

        OkHttpClient fakeThumbnailHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(FAKE_JPEG, OCTET))
                        .build())
                .build();

        YoutubeDownloader downloader = new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeHttp),
                new FormatSelector(),
                new StreamDownloader(fakeStreamHttp),
                req -> req.ffmpegLocation().map(FfmpegMuxer::new).orElseGet(FfmpegMuxer::new),
                CaptionDownloader.create(),
                new ThumbnailDownloader(fakeThumbnailHttp));

        DownloadRequest request = new DownloadRequest(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                true, AudioFormat.M4A, 0, Optional.empty(),
                false, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                ProgressListener.NO_OP, false, true);

        DownloadResult result = downloader.download(request);

        assertThat(result.thumbnailPath()).isPresent();
        Path thumbFile = result.thumbnailPath().get();
        assertThat(Files.exists(thumbFile)).isTrue();
        assertThat(Files.readAllBytes(thumbFile)).isEqualTo(FAKE_JPEG);
    }

    private static String loadFixture(String resourcePath) throws IOException {
        try (InputStream is = YoutubeDownloaderThumbnailTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
