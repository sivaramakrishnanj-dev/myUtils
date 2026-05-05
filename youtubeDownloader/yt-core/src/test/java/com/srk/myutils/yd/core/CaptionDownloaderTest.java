package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link CaptionDownloader} — happy-path only.
 *
 * <p>Injects an OkHttpClient with a short-circuit interceptor that returns
 * canned XML without opening any socket. Verifies the download returns the
 * XML string correctly.
 */
class CaptionDownloaderTest {

    private static final String CANNED_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><transcript>"
                    + "<text start=\"0\" dur=\"5\">Hello world</text></transcript>";

    @Test
    void download_givenValidUrl_returnsXmlBody() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(CANNED_XML,
                                MediaType.get("application/xml")))
                        .build())
                .build();

        CaptionDownloader downloader = new CaptionDownloader(client);
        String result = downloader.download("https://www.youtube.com/api/timedtext?v=test&lang=en");

        assertThat(result).isEqualTo(CANNED_XML);
    }
}
