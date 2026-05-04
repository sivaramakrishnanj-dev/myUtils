package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization test for {@link FormatSelector} cipher check (T-2.2, AC-5.3).
 *
 * <p>Verifies that when all candidate formats have {@code signatureCipher},
 * {@link CipherRequiredException} is thrown with the AC-5.3 message.
 *
 * <p>Contract test satisfied: CT-APP-5.
 */
class FormatSelectorCipherTest {

    @Test
    void select_givenAllCipherFormats_throwsCipherRequiredException() throws IOException {
        PlayerResponse response = PlayerResponseExtractor.extract(loadFixture("innertube-response-cipher.json"));

        assertThatThrownBy(() -> new FormatSelector().select(response.adaptiveFormats(), 1080))
                .isInstanceOf(CipherRequiredException.class)
                .hasMessageContaining("JavaScript signature deciphering");
    }

    private static String loadFixture(String name) throws IOException {
        try (InputStream is = FormatSelectorCipherTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
