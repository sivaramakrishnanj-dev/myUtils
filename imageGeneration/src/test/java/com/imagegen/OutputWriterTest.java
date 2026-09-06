package com.imagegen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputWriterTest {

    private static ObjectNode emptyMetadata() {
        return new ObjectMapper().createObjectNode();
    }

    @Test
    void seqStartsAtOneInAnEmptyDirectory(@TempDir Path dir) {
        assertEquals(1, OutputWriter.nextSeq(dir));
    }

    @Test
    void seqContinuesPastTheHighestExistingOutput(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("out_001_photo.png"));
        Files.createFile(dir.resolve("out_007_photo.png"));
        Files.createFile(dir.resolve("unrelated.png"));
        assertEquals(8, OutputWriter.nextSeq(dir));
    }

    @Test
    void seqIgnoresFilesThatOnlyLookLikeOutputs(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("output_9_photo.png"));
        Files.createFile(dir.resolve("out_photo.png"));
        assertEquals(1, OutputWriter.nextSeq(dir));
    }

    @Test
    void sidecarsCountTowardsTheSequence(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("out_003_photo.json"));
        assertEquals(4, OutputWriter.nextSeq(dir));
    }

    @Test
    void baseNameDropsTheExtension() {
        assertEquals("photo", OutputWriter.baseNameOf(Path.of("/tmp/photo.jpg")));
    }

    @Test
    void baseNameStripsAnExistingOutPrefixSoEditsDoNotNest() {
        assertEquals("photo", OutputWriter.baseNameOf(Path.of("/tmp/out_001_photo.png")));
        assertEquals("photo", OutputWriter.baseNameOf(Path.of("/tmp/out_012_photo.png")));
    }

    @Test
    void baseNameSanitisesAwkwardCharacters() {
        assertEquals("my-photo_2", OutputWriter.baseNameOf(Path.of("/tmp/my photo_2.png")));
    }

    @Test
    void slugKebabCasesThePrompt() {
        assertEquals("a-red-bicycle", OutputWriter.slug("A red bicycle"));
    }

    @Test
    void slugStripsPunctuationAndCollapsesSeparators() {
        assertEquals("a-red-bicycle", OutputWriter.slug("  A  red,  bicycle!! "));
    }

    @Test
    void slugTruncatesWithoutLeavingAPartialWordOrTrailingDash() {
        String slug = OutputWriter.slug(
                "a futuristic city built inside a giant glass bottle floating in space");
        assertTrue(slug.length() <= 40, "was " + slug.length() + ": " + slug);
        assertTrue(slug.endsWith("e") || !slug.endsWith("-"), slug);
        assertEquals("a-futuristic-city-built-inside-a-giant", slug);
    }

    @Test
    void slugFallsBackWhenThePromptHasNoUsableCharacters() {
        assertEquals("image", OutputWriter.slug("!!! ???"));
        assertEquals("image", OutputWriter.slug(""));
        assertEquals("image", OutputWriter.slug(null));
    }

    @Test
    void writeUsesTheOutputMimeExtensionNotTheInputs(@TempDir Path dir) {
        OutputWriter.Written written = OutputWriter.write(dir, "photo", "image/png",
                new byte[]{1, 2, 3}, emptyMetadata());
        assertEquals("out_001_photo.png", written.image().getFileName().toString());
        assertEquals("out_001_photo.json", written.sidecar().getFileName().toString());
        assertEquals(3, written.bytes());
        assertTrue(Files.exists(written.sidecar()));
    }

    @Test
    void consecutiveWritesIncrementTheSequence(@TempDir Path dir) {
        var first = OutputWriter.write(dir, "photo", "image/jpeg", new byte[]{1}, emptyMetadata());
        var second = OutputWriter.write(dir, "photo", "image/jpeg", new byte[]{1}, emptyMetadata());
        assertEquals("out_001_photo.jpg", first.image().getFileName().toString());
        assertEquals("out_002_photo.jpg", second.image().getFileName().toString());
    }

    @Test
    void previewPredictsPathsWithoutWriting(@TempDir Path dir) {
        Path predicted = OutputWriter.preview(dir, "cat", "image/png", 0);
        Path next = OutputWriter.preview(dir, "cat", "image/png", 1);
        assertEquals("out_001_cat.png", predicted.getFileName().toString());
        assertEquals("out_002_cat.png", next.getFileName().toString());
        assertTrue(Files.notExists(predicted));
    }
}
