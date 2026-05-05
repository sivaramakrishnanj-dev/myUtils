package com.srk.myutils.yd.core;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for T-3.10 (AC-13.5, INV-10):
 * M4A audio-only and transcript-only paths NEVER invoke FfmpegMuxer
 * (probe or otherwise), so machines without ffmpeg work for those paths.
 *
 * <p>SUT is {@link YoutubeDownloader} — never mocked. External HTTP is
 * faked via OkHttp interceptors. The muxer factory is a throwing lambda
 * that proves the M4A path never touches ffmpeg.
 */
class FfmpegMuxerSkipBehaviorTest {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final byte[] FAKE_AUDIO = {0x00, 0x01, 0x02, 0x03};
    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @TempDir
    Path tempDir;

    // ── Helpers ──────────────────────────────────────────────────────

    private static final Function<DownloadRequest, FfmpegMuxer> THROWING_FACTORY =
            req -> { throw new AssertionError("muxerFactory must not be invoked on this path"); };

    private YoutubeDownloader buildSut(Function<DownloadRequest, FfmpegMuxer> factory) {
        OkHttpClient innerTubeHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(loadFixture("/fixtures/innertube-response-happy.json"), JSON))
                        .build())
                .build();

        OkHttpClient streamHttp = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Length", String.valueOf(FAKE_AUDIO.length))
                        .body(ResponseBody.create(FAKE_AUDIO, OCTET))
                        .build())
                .build();

        return new YoutubeDownloader(
                new UrlParser(),
                new InnerTubeClient(innerTubeHttp),
                new FormatSelector(),
                new StreamDownloader(streamHttp),
                factory);
    }

    private DownloadRequest m4aRequest(Optional<String> ffmpegLocation) {
        return new DownloadRequest(
                VALID_URL, true, AudioFormat.M4A, 0, ffmpegLocation,
                new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                ProgressListener.NO_OP, false);
    }

    private DownloadRequest mp3Request() {
        return new DownloadRequest(
                VALID_URL, true, AudioFormat.MP3, 0, Optional.empty(),
                new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                ProgressListener.NO_OP, false);
    }

    private DownloadRequest videoAudioRequest() {
        return new DownloadRequest(
                VALID_URL, false, AudioFormat.M4A, 1080, Optional.empty(),
                new OutputConfig(Optional.empty(), Optional.of(tempDir), false),
                ProgressListener.NO_OP, false);
    }

    // ── Tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-13.5: M4A audio-only with throwing factory → success (factory never called)")
    void download_m4aAudioOnly_withThrowingFactory_succeedsWithoutInvokingFactory() {
        YoutubeDownloader sut = buildSut(THROWING_FACTORY);

        DownloadResult result = sut.download(m4aRequest(Optional.empty()));

        assertThat(result.audioPath()).isPresent();
        assertThat(result.audioPath().get().getFileName().toString()).endsWith(".m4a");
    }

    @Test
    @DisplayName("AC-13.5: M4A audio-only with bogus /nonexistent/ffmpeg → success (probeVersion never reached)")
    void download_m4aAudioOnly_withBogusPath_succeedsWithoutProbingFfmpeg() {
        // Factory returns a real FfmpegMuxer pointing at a nonexistent binary.
        // If probeVersion() were called, it would throw FfmpegException.
        YoutubeDownloader sut = buildSut(req -> new FfmpegMuxer("/nonexistent/ffmpeg"));

        DownloadResult result = sut.download(m4aRequest(Optional.of("/nonexistent/ffmpeg")));

        assertThat(result.audioPath()).isPresent();
        assertThat(result.audioPath().get().getFileName().toString()).endsWith(".m4a");
    }

    @Test
    @DisplayName("AC-13.5: M4A flow does not log 'probeVersion' or 'ffmpeg version'")
    void download_m4aAudioOnly_noFfmpegLogLines() {
        // The throwing factory guarantees no ffmpeg interaction at all.
        // If the factory is never called, no probeVersion log can appear.
        // This test verifies the factory is truly never invoked (same as test 1,
        // but with an AtomicBoolean witness for explicit assertion).
        AtomicBoolean factoryCalled = new AtomicBoolean(false);
        YoutubeDownloader sut = buildSut(req -> {
            factoryCalled.set(true);
            throw new AssertionError("factory invoked");
        });

        sut.download(m4aRequest(Optional.empty()));

        assertThat(factoryCalled).isFalse();
    }

    @Test
    @DisplayName("INV-10 negative: MP3 audio-only WITH throwing factory → throws (factory IS consulted)")
    void download_mp3AudioOnly_withThrowingFactory_throwsBecauseFactoryIsCalled() {
        YoutubeDownloader sut = buildSut(THROWING_FACTORY);

        assertThatThrownBy(() -> sut.download(mp3Request()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("muxerFactory must not be invoked");
    }

    @Test
    @DisplayName("INV-10 negative: Flow A (video+audio) WITH throwing factory → throws (ffmpeg IS invoked)")
    void download_videoAudio_withThrowingFactory_throwsBecauseFactoryIsCalled() {
        YoutubeDownloader sut = buildSut(THROWING_FACTORY);

        assertThatThrownBy(() -> sut.download(videoAudioRequest()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("muxerFactory must not be invoked");
    }

    @Test
    @DisplayName("AC-13.5: M4A with --ffmpeg-location set → stored in request but factory never called")
    void download_m4aAudioOnly_withFfmpegLocationSet_factoryNeverCalled() {
        AtomicBoolean factoryCalled = new AtomicBoolean(false);
        YoutubeDownloader sut = buildSut(req -> {
            factoryCalled.set(true);
            throw new AssertionError("factory invoked");
        });

        DownloadResult result = sut.download(m4aRequest(Optional.of("/usr/local/bin/ffmpeg")));

        assertThat(factoryCalled).isFalse();
        assertThat(result.audioPath()).isPresent();
    }

    @Test
    @Disabled("Transcript-only path not yet implemented — M4 will satisfy AC-13.5 for transcripts")
    @DisplayName("AC-13.5: Transcript-only with throwing factory → success (ffmpeg never invoked)")
    void download_transcriptOnly_withThrowingFactory_succeedsWithoutInvokingFactory() {
        // Placeholder — transcript-only download is not yet implemented.
        // When M4 lands, this test should construct a transcript-only DownloadRequest
        // and verify the throwing factory is never called.
    }

    // ── Fixture loader ───────────────────────────────────────────────

    private static String loadFixture(String resourcePath) {
        try (InputStream is = FfmpegMuxerSkipBehaviorTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, e);
        }
    }
}
