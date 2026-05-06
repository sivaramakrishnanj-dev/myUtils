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
 * Characterization test for {@link YoutubeDownloader} transcript integration —
 * happy-path: manual English track → .srt + .txt files produced.
 */
class YoutubeDownloaderTranscriptTest {

    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_BYTES = {0x00, 0x01, 0x02, 0x03};

    private static final String TIMEDTEXT_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <transcript>
              <text start="0.5" dur="2.0">Never gonna give you up</text>
              <text start="3.0" dur="2.5">Never gonna let you down</text>
            </transcript>
            """;

    @TempDir
    Path tempDir;

    @Test
    void download_withTranscript_producesSrtAndTxtFiles() throws IOException {
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

        OkHttpClient fakeCaptionHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(TIMEDTEXT_XML,
                                MediaType.get("text/xml")))
                        .build())
                .build();

        YoutubeDownloader downloader = new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(fakeHttp),
                new FormatSelector(),
                new StreamDownloader(fakeStreamHttp),
                req -> req.ffmpegLocation().map(FfmpegMuxer::new).orElseGet(FfmpegMuxer::new),
                new CaptionDownloader(fakeCaptionHttp),
                ThumbnailDownloader.create());

        DownloadRequest request = new DownloadRequest(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                true, AudioFormat.M4A, 0, Optional.empty(),
                true, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                ProgressListener.NO_OP, false, false);

        DownloadResult result = downloader.download(request);

        assertThat(result.srtPath()).isPresent();
        assertThat(result.txtPath()).isPresent();
        assertThat(result.usedAsrFallback()).isFalse();

        String srt = Files.readString(result.srtPath().get());
        assertThat(srt).contains("Never gonna give you up");
        assertThat(srt).contains("-->");

        String txt = Files.readString(result.txtPath().get());
        assertThat(txt).contains("Never gonna give you up");
        assertThat(txt).contains("Never gonna let you down");
    }

    private static String loadFixture(String resourcePath) throws IOException {
        try (InputStream is = YoutubeDownloaderTranscriptTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
