package com.srk.myutils.yd.cli;

import com.srk.myutils.yd.core.InnerTubeClient;
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

/**
 * Builds a {@link YoutubeDownloader} backed by a canned InnerTube response
 * for CLI tests that need valid-URL → exit 0 without network I/O.
 */
final class FakeDownloaderFactory {

    private FakeDownloaderFactory() { }

    /** Returns a {@link YoutubeDownloader} that returns the happy-path fixture for any video. */
    static YoutubeDownloader happyPath() {
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

        return new YoutubeDownloader(new UrlParser(), new InnerTubeClient(fakeHttp));
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
