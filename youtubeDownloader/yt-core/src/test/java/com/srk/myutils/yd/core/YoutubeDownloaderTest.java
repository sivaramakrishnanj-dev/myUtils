package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link YoutubeDownloader} — happy-path only.
 *
 * <p>Injects a fake {@link InnerTubeClient} (via OkHttp interceptor) that
 * returns the canned {@code innertube-response-happy.json} fixture.
 * Verifies that {@code download()} returns a {@link DownloadResult} with
 * the expected videoId and title, and all path fields empty (M1 stub).
 */
class YoutubeDownloaderTest {

    @Test
    void download_givenValidUrl_returnsResultWithMetadataAndNoPaths() throws IOException {
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

        YoutubeDownloader downloader = new YoutubeDownloader(
                new UrlParser(), new InnerTubeClient(fakeHttp));

        DownloadResult result = downloader.download(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(result.videoId().value()).isEqualTo("dQw4w9WgXcQ");
        assertThat(result.title()).isEqualTo(
                "Rick Astley - Never Gonna Give You Up (Official Music Video)");
        assertThat(result.videoPath()).isEmpty();
        assertThat(result.audioPath()).isEmpty();
        assertThat(result.srtPath()).isEmpty();
        assertThat(result.txtPath()).isEmpty();
        assertThat(result.thumbnailPath()).isEmpty();
        assertThat(result.usedAsrFallback()).isFalse();
    }

    private static String loadFixture(String resourcePath) throws IOException {
        try (InputStream is = YoutubeDownloaderTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
