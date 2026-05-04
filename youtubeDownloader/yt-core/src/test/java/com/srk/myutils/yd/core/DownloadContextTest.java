package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link DownloadContext} — happy-path only.
 * Exhaustive coverage (orphan detection, concurrent access, error paths)
 * is the tester's job.
 */
class DownloadContextTest {

    @Test
    void create_makesTempDirectory(@TempDir Path outputDir) {
        DownloadContext ctx = DownloadContext.create(outputDir, VideoId.of("dQw4w9WgXcQ"));

        assertThat(ctx.tempDir()).exists().isDirectory();
        assertThat(ctx.tempDir().getParent().getFileName().toString())
                .isEqualTo(DownloadContext.TEMP_ROOT_NAME);
    }

    @Test
    void markSuccessAndClose_deletesTempDirectory(@TempDir Path outputDir) throws Exception {
        DownloadContext ctx = DownloadContext.create(outputDir, VideoId.of("dQw4w9WgXcQ"));
        // Create a .part file to verify recursive deletion
        Files.writeString(ctx.tempFile("video.part"), "fake data");

        ctx.markSuccess();
        ctx.close();

        assertThat(ctx.tempDir()).doesNotExist();
    }

    @Test
    void closeWithoutMarkSuccess_retainsTempDirectory(@TempDir Path outputDir) throws Exception {
        DownloadContext ctx = DownloadContext.create(outputDir, VideoId.of("dQw4w9WgXcQ"));
        Files.writeString(ctx.tempFile("video.part"), "fake data");

        ctx.close();

        assertThat(ctx.tempDir()).exists().isDirectory();
        assertThat(ctx.tempFile("video.part")).exists();
    }
}
