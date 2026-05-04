package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link StreamDownloader} — happy-path only.
 *
 * <p>Injects an OkHttpClient with a short-circuit interceptor that returns
 * canned bytes without opening any socket. Verifies the {@code .part} file
 * contents match and the progress callback is invoked.
 */
class StreamDownloaderTest {

    private static final byte[] CANNED_BODY = "hello-stream-bytes-1234567890".getBytes();

    @Test
    void download_givenCannedResponse_writesPartFileAndInvokesCallback(@TempDir Path tempDir) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Length", String.valueOf(CANNED_BODY.length))
                        .body(ResponseBody.create(CANNED_BODY,
                                MediaType.get("video/mp4")))
                        .build())
                .build();

        StreamDownloader downloader = new StreamDownloader(client);
        Path partFile = tempDir.resolve("video.part");

        AtomicLong lastBytesWritten = new AtomicLong(-1);
        AtomicLong lastTotalBytes = new AtomicLong(-1);

        downloader.download(
                "https://rr1---sn-abc.googlevideo.com/videoplayback?id=test",
                partFile,
                (bytesWritten, totalBytes) -> {
                    lastBytesWritten.set(bytesWritten);
                    lastTotalBytes.set(totalBytes);
                });

        assertThat(Files.readAllBytes(partFile)).isEqualTo(CANNED_BODY);
        assertThat(lastBytesWritten.get()).isEqualTo(CANNED_BODY.length);
        assertThat(lastTotalBytes.get()).isEqualTo(CANNED_BODY.length);
    }
}
