package com.srk.myutils.yd.cli.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the shaded fat-jar stays within the 10 MB hard cap (05-operations.md § 6.1).
 * Runs under {@code mvn verify -P integration} — after the shade plugin produces the jar.
 *
 * <p>T-5.9.
 */
@Tag("integration")
class FatJarSizeIT {

    private static final Path FAT_JAR = Path.of("target/youtube-downloader-1.0.0.jar");
    private static final long ONE_MB = 1_024L * 1_024L;
    private static final long MIN_SIZE = ONE_MB;          // sanity: jar must be at least 1 MB
    private static final long MAX_SIZE = 10L * ONE_MB;    // hard cap per 05-operations.md § 6.1

    @Test
    void fatJarSizeWithinBudget() throws IOException {
        assertThat(FAT_JAR)
                .as("Fat jar must exist (shade plugin must have run)")
                .exists();

        long size = Files.size(FAT_JAR);

        assertThat(size)
                .as("Fat jar size (%d bytes) must be between 1 MB and 10 MB", size)
                .isGreaterThanOrEqualTo(MIN_SIZE)
                .isLessThanOrEqualTo(MAX_SIZE);
    }
}
