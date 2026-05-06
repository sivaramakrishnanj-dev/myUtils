package com.srk.myutils.yd.cli.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the fat-jar is a structurally valid JAR with the expected manifest.
 * Complements {@link FatJarSizeIT} (size bounds) with integrity checks.
 *
 * <p>T-5.9 — runs under {@code mvn verify -P integration}.
 */
@Tag("integration")
class FatJarStructureIT {

    private static final Path FAT_JAR = Path.of("target/youtube-downloader-1.0.0.jar");

    @Test
    void fatJar_isValidJarWithMainClass() throws IOException {
        assertThat(FAT_JAR).exists();

        try (JarFile jar = new JarFile(FAT_JAR.toFile())) {
            Manifest manifest = jar.getManifest();

            assertThat(manifest)
                    .as("JAR must contain a valid MANIFEST.MF")
                    .isNotNull();

            String mainClass = manifest.getMainAttributes().getValue("Main-Class");

            assertThat(mainClass)
                    .as("Main-Class must be the CLI entrypoint")
                    .isEqualTo("com.srk.myutils.yd.cli.Cli");

            assertThat(jar.stream().count())
                    .as("JAR must contain entries (not empty)")
                    .isGreaterThan(10);
        }
    }
}
