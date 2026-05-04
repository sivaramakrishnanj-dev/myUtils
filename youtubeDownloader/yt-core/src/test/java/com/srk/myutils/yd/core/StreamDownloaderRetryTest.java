package com.srk.myutils.yd.core;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link StreamDownloader} retry logic (T-2.4).
 *
 * <p>Verifies that transient 500 errors trigger retries with byte-0 restart
 * and the download succeeds after the transient failure clears.
 * Uses a no-op {@link StreamDownloader.Sleeper} to avoid real delays.
 */
class StreamDownloaderRetryTest {

    private static final String CDN_URL =
            "https://rr1---sn-abc.googlevideo.com/videoplayback?id=retry-test";
    private static final MediaType MP4 = MediaType.get("video/mp4");
    private static final byte[] BODY = "retry-success-payload".getBytes();

    @Test
    void download_givenTransient500ThenSuccess_retriesAndWritesBody(@TempDir Path tmp)
            throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        List<Long> sleepDelays = new ArrayList<>();

        Interceptor interceptor = chain -> {
            int n = callCount.incrementAndGet();
            if (n == 1) {
                // First attempt: transient 500
                return new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(500).message("Internal Server Error")
                        .body(ResponseBody.create(new byte[0], MP4))
                        .build();
            }
            // Second attempt: success
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .header("Content-Length", String.valueOf(BODY.length))
                    .body(ResponseBody.create(BODY, MP4))
                    .build();
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(interceptor).build();
        StreamDownloader sut = new StreamDownloader(client,
                millis -> sleepDelays.add(millis));

        Path part = tmp.resolve("video.part");
        sut.download(CDN_URL, part, StreamDownloader.NO_OP);

        assertThat(callCount.get()).isEqualTo(2);
        assertThat(Files.readAllBytes(part)).isEqualTo(BODY);
        assertThat(sleepDelays).containsExactly(StreamDownloader.BACKOFF_BASE_MS);
    }
}
