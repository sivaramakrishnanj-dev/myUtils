package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link OutputWriter} — happy-path only.
 * Exhaustive coverage (edge cases, boundary truncation, overwrite, disk-space)
 * is the tester's job.
 */
class OutputWriterTest {

    @Test
    void deriveOutputPath_givenTypicalVideo_returnsSanitizedTruncatedPath(@TempDir Path tempDir) {
        OutputConfig config = new OutputConfig(Optional.empty(), Optional.of(tempDir), false);
        OutputWriter writer = new OutputWriter(config);

        VideoDetails details = new VideoDetails(
                VideoId.of("dQw4w9WgXcQ"),
                "Rick Astley - Never Gonna Give You Up (Official Music Video)",
                false,
                false,
                Optional.empty()
        );

        Path result = writer.deriveOutputPath(details, "mp4");

        assertThat(result.getParent()).isEqualTo(tempDir);
        assertThat(result.getFileName().toString())
                .isEqualTo("Rick Astley - Never Gonna Give You Up (Official Music Video) [dQw4w9WgXcQ].mp4");
    }
}
