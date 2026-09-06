package com.imagegen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parsed command line. Only records what the user actually passed - {@code null}
 * means "not specified", so {@link Config} can layer defaults underneath without
 * a flag silently beating the config file.
 */
public final class Options {

    private static final Set<String> COMMANDS = Set.of("generate", "edit", "models", "config", "help");

    public String command;
    public String prompt;
    public final List<Path> images = new ArrayList<>();
    public Path outDir;
    public String model;
    public String resolution;
    public String aspectRatio;
    public String mimeType;
    public String thinkingLevel;
    public Integer count;
    public Path continueFrom;
    public boolean emitBase64;
    public boolean dryRun;
    public boolean quiet;
    public String format = "json";
    public Path configPath;
    public String apiKey;
    public Integer timeoutSeconds;
    public Integer retries;
    public Path debugDumpResponse;
    public boolean help;
    public boolean agentHelp;
    public boolean init;
    public boolean show;

    private Options() {
    }

    public static Options parse(String[] args) {
        Options o = new Options();
        if (args.length == 0) {
            o.command = "help";
            return o;
        }

        int i = 0;
        if (!args[0].startsWith("-")) {
            o.command = args[0];
            if (!COMMANDS.contains(o.command)) {
                throw CliException.usage(
                        "Unknown command '" + o.command + "'",
                        "Valid commands: generate, edit, models, config, help. Run 'imagegen help --agent'.");
            }
            i = 1;
        } else {
            o.command = "help";
        }

        while (i < args.length) {
            String arg = args[i];
            String inlineValue = null;
            int eq = arg.indexOf('=');
            if (arg.startsWith("--") && eq > 2) {
                inlineValue = arg.substring(eq + 1);
                arg = arg.substring(0, eq);
            }

            switch (arg) {
                case "-p", "--prompt" -> o.prompt = need(args, i, arg, inlineValue);
                case "--prompt-file" -> o.prompt = readPromptFile(Path.of(need(args, i, arg, inlineValue)));
                case "-i", "--image" -> o.images.add(Path.of(need(args, i, arg, inlineValue)));
                case "-o", "--out-dir" -> o.outDir = Path.of(need(args, i, arg, inlineValue));
                case "-m", "--model" -> o.model = need(args, i, arg, inlineValue);
                case "-r", "--resolution" -> o.resolution = need(args, i, arg, inlineValue);
                case "-a", "--aspect-ratio" -> o.aspectRatio = need(args, i, arg, inlineValue);
                case "--mime" -> o.mimeType = need(args, i, arg, inlineValue);
                case "--thinking" -> o.thinkingLevel = need(args, i, arg, inlineValue);
                case "-n", "--count" -> o.count = parseInt(arg, need(args, i, arg, inlineValue));
                case "--continue-from" -> o.continueFrom = Path.of(need(args, i, arg, inlineValue));
                case "--config" -> o.configPath = Path.of(need(args, i, arg, inlineValue));
                case "--api-key" -> o.apiKey = need(args, i, arg, inlineValue);
                case "--timeout" -> o.timeoutSeconds = parseInt(arg, need(args, i, arg, inlineValue));
                case "--retries" -> o.retries = parseInt(arg, need(args, i, arg, inlineValue));
                case "--format" -> o.format = need(args, i, arg, inlineValue);
                case "--debug-dump-response" -> o.debugDumpResponse = Path.of(need(args, i, arg, inlineValue));
                case "--emit-base64" -> o.emitBase64 = true;
                case "--dry-run" -> o.dryRun = true;
                case "--quiet" -> o.quiet = true;
                case "--init" -> o.init = true;
                case "--show" -> o.show = true;
                case "--agent" -> o.agentHelp = true;
                case "-h", "--help" -> o.help = true;
                default -> throw CliException.usage(
                        "Unknown option '" + arg + "'",
                        "Run 'imagegen help --agent' for the full flag list.");
            }

            // Flags that consumed a separate argv slot advance by two.
            if (inlineValue == null && TAKES_VALUE.contains(arg)) {
                i += 2;
            } else {
                i += 1;
            }
        }
        return o;
    }

    private static final Set<String> TAKES_VALUE = Set.of(
            "-p", "--prompt", "--prompt-file", "-i", "--image", "-o", "--out-dir",
            "-m", "--model", "-r", "--resolution", "-a", "--aspect-ratio", "--mime",
            "--thinking", "-n", "--count", "--continue-from", "--config", "--api-key",
            "--timeout", "--retries", "--format", "--debug-dump-response");

    private static String need(String[] args, int i, String flag, String inline) {
        if (inline != null) {
            return inline;
        }
        if (i + 1 >= args.length) {
            throw CliException.usage(flag + " requires a value", "Example: " + flag + " <value>");
        }
        return args[i + 1];
    }

    private static int parseInt(String flag, String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw CliException.usage(flag + " expects an integer, got '" + raw + "'", "Example: " + flag + " 2");
        }
    }

    private static String readPromptFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            throw CliException.io("Cannot read --prompt-file " + path, "Check the path exists and is readable.", e);
        }
    }
}
