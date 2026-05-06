package com.srk.myutils.yd.core;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link ThumbnailDownloader} — happy path only.
 *
 * <p>Verifies pickBest selects the largest thumbnail and download writes bytes
 * to disk. Uses an OkHttp short-circuit interceptor (no real network).
 */
@DisplayName("ThumbnailDownloader — characterization")
class ThumbnailDownloaderTest {

    private static final byte[] FAKE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03};

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("pickBest selects thumbnail with largest area")
    void pickBest_selectsLargestArea() throws Exception {
        var small = new ThumbnailUrl("https://i.ytimg.com/vi/x/default.jpg", 120, 90);
        var medium = new ThumbnailUrl("https://i.ytimg.com/vi/x/hqdefault.jpg", 480, 360);
        var large = new ThumbnailUrl("https://i.ytimg.com/vi/x/maxresdefault.jpg", 1280, 720);

        ThumbnailUrl best = ThumbnailDownloader.pickBest(List.of(small, medium, large));

        assertThat(best).isSameAs(large);
    }

    @Test
    @DisplayName("download writes bytes to output path")
    void download_writesBytesToFile() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(FAKE_JPEG, null))
                        .build())
                .build();

        ThumbnailDownloader downloader = new ThumbnailDownloader(client);
        Path output = tempDir.resolve("thumb.jpg");

        downloader.download(
                List.of(new ThumbnailUrl("https://i.ytimg.com/vi/x/maxresdefault.jpg", 1280, 720)),
                output);

        assertThat(output).exists();
        assertThat(Files.readAllBytes(output)).isEqualTo(FAKE_JPEG);
    }
}
