package com.srk.focusguard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fast mode: instant add/remove for arbitrary hosts entries used for testing
 * or temporary routing. These entries are tagged with {@link Markers#FAST} so
 * they are isolated from guarded entries. No gauntlet, no friction.
 *
 * <p><b>Isolation rule:</b> fast-mode operations only ever touch lines
 * carrying the FAST marker. They never read, modify, or delete FOCUS-GUARD
 * lines. Conversely, the gauntlet never touches FAST lines.
 */
final class FastMode {

    private FastMode() {}

    /**
     * Add a fast entry mapping {@code host} to {@code ip}. If an entry for
     * the same host already exists (under the fast marker), it is replaced.
     */
    static void fastAdd(String ip, String host) throws IOException {
        if (!isValidIp(ip)) {
            Ui.println(Ansi.RED + "Invalid IP address: " + ip + Ansi.RESET);
            System.exit(1);
        }
        if (!isValidHost(host)) {
            Ui.println(Ansi.RED + "Invalid host: " + host + Ansi.RESET);
            System.exit(1);
        }

        String markerTag = Markers.FAST + ":" + host;

        HostsFile.unlock();
        try {
            // Remove any pre-existing fast entry for this host (idempotent add).
            removeFastEntry(host);
            HostsFile.appendLine(ip + "  " + host + " " + markerTag);
            HostsFile.flushDns();
        } finally {
            HostsFile.lock();
        }

        Ui.println(Ansi.GREEN + "⚡ Fast-added " + Ansi.BOLD + host + Ansi.RESET
                + Ansi.GREEN + " → " + ip + Ansi.RESET);
    }

    /** Remove the fast entry for a single host. Guarded entries are never touched. */
    static void fastDel(String host) throws IOException {
        String markerTag = Markers.FAST + ":" + host;
        if (!HostsFile.readAll().contains(markerTag)) {
            Ui.println(Ansi.YELLOW + "No fast entry found for " + host + "." + Ansi.RESET);
            return;
        }

        HostsFile.unlock();
        try {
            removeFastEntry(host);
            HostsFile.flushDns();
        } finally {
            HostsFile.lock();
        }

        Ui.println(Ansi.GREEN + "⚡ Removed fast entry for " + Ansi.BOLD + host
                + Ansi.RESET + Ansi.GREEN + "." + Ansi.RESET);
    }

    /**
     * Remove all fast entries. Guarded entries are never touched. Useful for
     * cleaning up after a testing session.
     */
    static void fastClear() throws IOException {
        List<String> lines = HostsFile.readLines();
        List<String> kept = new ArrayList<>(lines.size());
        int removed = 0;
        for (String line : lines) {
            // Safety: never touch guard entries, regardless of how this loop mutates.
            if (line.contains(Markers.GUARD + ":")) {
                kept.add(line);
                continue;
            }
            if (line.contains(Markers.FAST + ":")) {
                removed++;
                continue;
            }
            kept.add(line);
        }
        if (removed == 0) {
            Ui.println(Ansi.YELLOW + "No fast entries to clear." + Ansi.RESET);
            return;
        }

        trimTrailingBlanks(kept);
        HostsFile.unlock();
        try {
            HostsFile.writeLines(kept);
            HostsFile.flushDns();
        } finally {
            HostsFile.lock();
        }

        Ui.println(Ansi.GREEN + "⚡ Cleared " + removed + " fast entr"
                + (removed == 1 ? "y" : "ies") + "." + Ansi.RESET);
    }

    // ── Internals ─────────────────────────────────────────────────────────

    /** Remove lines carrying the fast marker for a specific host. Caller handles lock/flush. */
    private static void removeFastEntry(String host) throws IOException {
        String marker = Markers.FAST + ":" + host;
        List<String> lines = HostsFile.readLines();
        List<String> kept = new ArrayList<>(lines.size());
        for (String line : lines) {
            // Defence-in-depth: never remove guard lines, even if a host name happened to collide.
            if (line.contains(Markers.GUARD + ":")) {
                kept.add(line);
                continue;
            }
            if (line.contains(marker)) continue;
            kept.add(line);
        }
        trimTrailingBlanks(kept);
        HostsFile.writeLines(kept);
    }

    private static void trimTrailingBlanks(List<String> lines) {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
    }

    private static boolean isValidIp(String ip) {
        // Very permissive: IPv4 dotted-quad or a bracketed/plain IPv6-looking string.
        // Refuses whitespace and obvious injection attempts.
        if (ip == null || ip.isBlank()) return false;
        if (ip.contains(" ") || ip.contains("\t")) return false;
        return ip.matches("^[0-9A-Fa-f\\.:]+$");
    }

    private static boolean isValidHost(String host) {
        if (host == null || host.isBlank()) return false;
        if (host.contains(" ") || host.contains("\t")) return false;
        // Hostnames: letters, digits, dot, hyphen, underscore.
        return host.matches("^[A-Za-z0-9._-]+$");
    }
}
