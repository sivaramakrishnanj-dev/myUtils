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
 * Characterization test for Flow C (transcript-only, no media download).
 *
 * <p>Verifies that {@code --transcript} without {@code --audio-only} produces
 * only .srt + .txt files with no media download (state-machine Flow C, AC-13.5).
 */
class YoutubeDownloaderFlowCTest {

    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

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
    void download_transcriptOnlyFlowC_producesSrtAndTxtWithoutMedia() throws IOException {
        String fixture = loadFixture("/fixtures/innertube-response-happy.json");

        OkHttpClient innerTubeHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(fixture, MediaType.get("application/json")))
                        .build())
                .build();

        OkHttpClient captionHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(TIMEDTEXT_XML, MediaType.get("text/xml")))
                        .build())
                .build();

        YoutubeDownloader sut = new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(innerTubeHttp),
                new FormatSelector(),
                StreamDownloader.create(),
                req -> { throw new AssertionError("muxerFactory must not be invoked in Flow C"); },
                new CaptionDownloader(captionHttp),
                ThumbnailDownloader.create());

        DownloadRequest request = new DownloadRequest(
                VALID_URL, false, AudioFormat.M4A, 1080, Optional.empty(),
                true, Optional.empty(), false,
                new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                ProgressListener.NO_OP, false, false, false);

        DownloadResult result = sut.download(request);

        assertThat(result.videoPath()).isEmpty();
        assertThat(result.audioPath()).isEmpty();
        assertThat(result.srtPath()).isPresent();
        assertThat(result.txtPath()).isPresent();

        String srt = Files.readString(result.srtPath().get());
        assertThat(srt).contains("Never gonna give you up");
        assertThat(srt).contains("-->");
    }

    private static String loadFixture(String resourcePath) throws IOException {
        try (InputStream is = YoutubeDownloaderFlowCTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
