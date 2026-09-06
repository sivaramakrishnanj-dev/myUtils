package com.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Points at a path inside the temp dir so the developer's real config is never read. */
    private static Options optionsWithIsolatedConfig(Path dir, String... extra) {
        String[] args = new String[extra.length + 3];
        args[0] = "generate";
        args[1] = "--config";
        args[2] = dir.resolve("config.json").toString();
        System.arraycopy(extra, 0, args, 3, extra.length);
        return Options.parse(args);
    }

    @Test
    void builtInDefaultsApplyWhenNothingIsConfigured(@TempDir Path dir) {
        Config config = Config.resolve(optionsWithIsolatedConfig(dir), null);
        assertEquals(Config.DEFAULT_MODEL, config.model);
        assertEquals("1K", config.resolution);
        assertEquals("image/png", config.mimeType);
        assertEquals(Config.DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds);
        assertEquals(Config.DEFAULT_RETRIES, config.retries);
        assertNull(config.aspectRatio);
        assertNull(config.thinkingLevel);
        assertFalse(config.configPresent);
    }

    @Test
    void configFileSuppliesDefaultsUnderneathFlags(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.json");
        Files.writeString(file, """
            {"model": "gemini-3-pro-image", "resolution": "4K", "aspectRatio": "16:9", "retries": 4}
            """);

        Config fromFile = Config.resolve(optionsWithIsolatedConfig(dir), null);
        assertEquals("gemini-3-pro-image", fromFile.model);
        assertEquals("4K", fromFile.resolution);
        assertEquals("16:9", fromFile.aspectRatio);
        assertEquals(4, fromFile.retries);
        assertTrue(fromFile.configPresent);

        Config flagWins = Config.resolve(optionsWithIsolatedConfig(dir, "--resolution", "2K"), null);
        assertEquals("2K", flagWins.resolution);
        assertEquals("gemini-3-pro-image", flagWins.model);
    }

    @Test
    void explicitApiKeyBeatsEverythingElse(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.json"), "{\"apiKey\": \"from-file\"}");
        Config config = Config.resolve(optionsWithIsolatedConfig(dir, "--api-key", "from-flag"), null);
        assertEquals("from-flag", config.apiKey);
        assertEquals("--api-key", config.apiKeySource);
    }

    @Test
    void sidecarSettingsAreInheritedButFlagsStillWin(@TempDir Path dir) {
        JsonNode sidecar = MAPPER.createObjectNode()
                .put("model", "gemini-3-pro-image")
                .put("resolution", "2K")
                .put("aspectRatio", "9:16");

        Config inherited = Config.resolve(optionsWithIsolatedConfig(dir), sidecar);
        assertEquals("gemini-3-pro-image", inherited.model);
        assertEquals("2K", inherited.resolution);
        assertEquals("9:16", inherited.aspectRatio);

        Config overridden = Config.resolve(optionsWithIsolatedConfig(dir, "--resolution", "4K"), sidecar);
        assertEquals("4K", overridden.resolution);
        assertEquals("gemini-3-pro-image", overridden.model);
    }

    @Test
    void missingKeyFailsWithAConfigErrorNamingTheFix(@TempDir Path dir) {
        Config config = Config.resolve(optionsWithIsolatedConfig(dir, "--api-key", ""), null);
        if (config.apiKey != null) {
            return; // Developer environment exports a key; precedence is covered elsewhere.
        }
        CliException e = assertThrows(CliException.class, config::requireApiKey);
        assertEquals(ExitCode.CONFIG, e.exitCode());
        assertTrue(e.hint().contains("config --init"), e.hint());
    }

    @Test
    void lowercaseResolutionIsRejectedWithAPointedHint(@TempDir Path dir) {
        CliException e = assertThrows(CliException.class,
                () -> Config.resolve(optionsWithIsolatedConfig(dir, "--resolution", "1k"), null));
        assertEquals(ExitCode.USAGE, e.exitCode());
        assertTrue(e.hint().contains("lowercase"), e.hint());
    }

    @Test
    void unsupportedValuesAreRejected(@TempDir Path dir) {
        assertEquals(ExitCode.USAGE, assertThrows(CliException.class,
                () -> Config.resolve(optionsWithIsolatedConfig(dir, "--resolution", "8K"), null)).exitCode());
        assertEquals(ExitCode.USAGE, assertThrows(CliException.class,
                () -> Config.resolve(optionsWithIsolatedConfig(dir, "--aspect-ratio", "7:3"), null)).exitCode());
        assertEquals(ExitCode.USAGE, assertThrows(CliException.class,
                () -> Config.resolve(optionsWithIsolatedConfig(dir, "--mime", "image/tiff"), null)).exitCode());
        assertEquals(ExitCode.USAGE, assertThrows(CliException.class,
                () -> Config.resolve(optionsWithIsolatedConfig(dir, "--thinking", "medium"), null)).exitCode());
        assertEquals(ExitCode.USAGE, assertThrows(CliException.class,
                () -> Config.resolve(optionsWithIsolatedConfig(dir, "--timeout", "2"), null)).exitCode());
    }

    @Test
    void malformedConfigFileIsAConfigErrorNotACrash(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.json"), "{not json");
        CliException e = assertThrows(CliException.class,
                () -> Config.resolve(optionsWithIsolatedConfig(dir), null));
        assertEquals(ExitCode.CONFIG, e.exitCode());
    }

    @Test
    void initWritesATemplateAndRefusesToClobber(@TempDir Path dir) {
        Path path = dir.resolve("nested").resolve("config.json");
        Config.init(path);
        assertTrue(Files.isRegularFile(path));

        CliException e = assertThrows(CliException.class, () -> Config.init(path));
        assertEquals(ExitCode.CONFIG, e.exitCode());
    }

    @Test
    void redactionRevealsOnlyTheTail() {
        assertEquals("...WXYZ", Config.redact("not-a-real-key-WXYZ"));
        assertEquals("****", Config.redact("abc"));
        assertNull(Config.redact(null));
        assertNull(Config.redact("  "));
    }
}
