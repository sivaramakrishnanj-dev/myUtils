package com.srk.focusguard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages reads/writes to /etc/hosts, including the macOS-specific
 * {@code chflags schg} immutable flag and DNS cache flushing.
 *
 * <p>Callers must ensure the process is running as root; we attempt to
 * detect that and fail fast.
 */
final class HostsFile {

    static final Path PATH = Path.of("/etc/hosts");

    private HostsFile() {}

    // ── Read ──────────────────────────────────────────────────────────────

    static List<String> readLines() throws IOException {
        if (!Files.exists(PATH)) return new ArrayList<>();
        return new ArrayList<>(Files.readAllLines(PATH, StandardCharsets.UTF_8));
    }

    static String readAll() throws IOException {
        if (!Files.exists(PATH)) return "";
        return Files.readString(PATH, StandardCharsets.UTF_8);
    }

    // ── Mutate ────────────────────────────────────────────────────────────

    /**
     * Append a single line to /etc/hosts. Ensures the file ends with a
     * newline before appending so the new entry starts on its own line.
     */
    static void appendLine(String line) throws IOException {
        String existing = readAll();
        boolean needsNewline = !existing.isEmpty() && !existing.endsWith("\n");
        StringBuilder sb = new StringBuilder();
        if (needsNewline) sb.append('\n');
        sb.append(line).append('\n');
        Files.writeString(PATH, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
    }

    /**
     * Rewrite /etc/hosts in place from the given lines (one per element,
     * no trailing newlines on individual entries).
     */
    static void writeLines(List<String> lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append('\n');
        }
        Files.writeString(PATH, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    // ── OS hooks: lock + DNS ──────────────────────────────────────────────

    /** Apply the macOS system-immutable flag on /etc/hosts. */
    static void lock() {
        run("chflags", "schg", PATH.toString());
    }

    /** Remove the macOS system-immutable flag on /etc/hosts. */
    static void unlock() {
        run("chflags", "noschg", PATH.toString());
    }

    /** Flush macOS DNS caches so blocking/unblocking takes effect immediately. */
    static void flushDns() {
        run("dscacheutil", "-flushcache");
        run("killall", "-HUP", "mDNSResponder");
    }

    /** Assert the current process is running as root; exit with code 1 otherwise. */
    static void requireRoot() {
        String uid = System.getenv("SUDO_UID");
        String user = System.getProperty("user.name");
        if (!"root".equals(user) && uid == null) {
            // Fallback: shell wrapper should enforce sudo, but we double-check.
            // On macOS, running under sudo makes user.name == "root".
            Ui.println(Ansi.RED + "Run with sudo." + Ansi.RESET);
            System.exit(1);
        }
    }

    // ── Private ───────────────────────────────────────────────────────────

    private static void run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            // Non-fatal: lock/flush failures shouldn't crash the whole flow.
        }
    }
}
