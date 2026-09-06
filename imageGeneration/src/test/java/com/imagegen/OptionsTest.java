package com.imagegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionsTest {

    @Test
    void noArgumentsShowsHelp() {
        assertEquals("help", Options.parse(new String[]{}).command);
    }

    @Test
    void parsesGenerateWithLongFlags() {
        Options o = Options.parse(new String[]{"generate", "--prompt", "a cat", "--resolution", "2K"});
        assertEquals("generate", o.command);
        assertEquals("a cat", o.prompt);
        assertEquals("2K", o.resolution);
    }

    @Test
    void parsesShortFlags() {
        Options o = Options.parse(new String[]{"edit", "-p", "brighter", "-i", "a.png", "-r", "4K", "-m", "x"});
        assertEquals("brighter", o.prompt);
        assertEquals(Path.of("a.png"), o.images.get(0));
        assertEquals("4K", o.resolution);
        assertEquals("x", o.model);
    }

    @Test
    void parsesEqualsForm() {
        Options o = Options.parse(new String[]{"generate", "--prompt=a dog", "--resolution=1K"});
        assertEquals("a dog", o.prompt);
        assertEquals("1K", o.resolution);
    }

    @Test
    void repeatedImageFlagsAccumulateInOrder() {
        Options o = Options.parse(new String[]{"edit", "-p", "x", "-i", "a.png", "-i", "b.png", "-i", "c.png"});
        assertEquals(3, o.images.size());
        assertEquals(Path.of("b.png"), o.images.get(1));
    }

    @Test
    void booleanFlagsDoNotConsumeTheNextArgument() {
        Options o = Options.parse(new String[]{"generate", "--dry-run", "-p", "a cat"});
        assertTrue(o.dryRun);
        assertEquals("a cat", o.prompt);
    }

    @Test
    void promptsCanComeFromAFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("prompt.txt");
        Files.writeString(file, "  a long multi-line\nprompt  \n");
        Options o = Options.parse(new String[]{"generate", "--prompt-file", file.toString()});
        assertEquals("a long multi-line\nprompt", o.prompt);
    }

    @Test
    void defaultsAreJsonAndUnset() {
        Options o = Options.parse(new String[]{"generate", "-p", "x"});
        assertEquals("json", o.format);
        assertNull(o.resolution);
        assertNull(o.model);
        assertNull(o.count);
        assertFalse(o.emitBase64);
        assertFalse(o.dryRun);
    }

    @Test
    void unknownCommandIsAUsageError() {
        CliException e = assertThrows(CliException.class, () -> Options.parse(new String[]{"paint", "-p", "x"}));
        assertEquals(ExitCode.USAGE, e.exitCode());
    }

    @Test
    void unknownOptionIsAUsageError() {
        CliException e = assertThrows(CliException.class,
                () -> Options.parse(new String[]{"generate", "--nope", "1"}));
        assertEquals(ExitCode.USAGE, e.exitCode());
    }

    @Test
    void missingFlagValueIsAUsageError() {
        CliException e = assertThrows(CliException.class,
                () -> Options.parse(new String[]{"generate", "--prompt"}));
        assertEquals(ExitCode.USAGE, e.exitCode());
    }

    @Test
    void nonNumericCountIsAUsageError() {
        CliException e = assertThrows(CliException.class,
                () -> Options.parse(new String[]{"generate", "-p", "x", "-n", "many"}));
        assertEquals(ExitCode.USAGE, e.exitCode());
    }

    @Test
    void helpAcceptsTheAgentFlag() {
        Options o = Options.parse(new String[]{"help", "--agent"});
        assertEquals("help", o.command);
        assertTrue(o.agentHelp);
    }
}
