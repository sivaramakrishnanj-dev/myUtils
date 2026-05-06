package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Characterization test for T-5.5: verifies that the {@code x-captured-on}
 * metadata field added to every fixture is silently ignored by
 * {@link PlayerResponseExtractor} (ADR-0004: FAIL_ON_UNKNOWN_PROPERTIES=false).
 */
@DisplayName("T-5.5: fixture x-captured-on metadata ignored by parser")
class FixtureMetadataTest {

    @ParameterizedTest(name = "{0} parses cleanly with x-captured-on present")
    @ValueSource(strings = {
            "innertube-response-happy.json",
            "innertube-response-unplayable.json",
            "innertube-response-live.json",
            "innertube-response-cipher.json",
            "innertube-response-asr-only.json",
            "innertube-response-no-captions.json"
    })
    void extract_givenFixtureWithMetadata_parsesWithoutError(String fixture) throws IOException {
        String json = loadFixture(fixture);

        assertThatCode(() -> PlayerResponseExtractor.extract(json))
                .doesNotThrowAnyException();
    }

    private static String loadFixture(String name) throws IOException {
        try (InputStream is = FixtureMetadataTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
