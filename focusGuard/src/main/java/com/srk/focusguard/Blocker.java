package com.srk.focusguard;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Guarded blocking: adds a domain to /etc/hosts with the FOCUS-GUARD marker
 * and lists all currently-blocked domains. Unblocking is handled separately
 * by {@link Gauntlet}.
 */
final class Blocker {

    private Blocker() {}

    /**
     * Block a domain immediately. Entries are tagged so the unblock gauntlet
     * and the fast-mode cleaners can tell them apart.
     */
    static void block(String domain) throws IOException {
        String marker = Markers.GUARD + ":" + domain;
        String hosts = HostsFile.readAll();
        if (hosts.contains(marker)) {
            Ui.println(Ansi.YELLOW + domain + " is already blocked." + Ansi.RESET);
            return;
        }

        HostsFile.unlock();
        try {
            HostsFile.appendLine("127.0.0.1  " + domain + " " + marker);
            HostsFile.appendLine("127.0.0.1  www." + domain + " " + marker);
            HostsFile.flushDns();
        } finally {
            HostsFile.lock();
        }

        Ui.println(Ansi.GREEN + "✅ Blocked " + Ansi.BOLD + domain + Ansi.RESET
                + Ansi.GREEN + " immediately. Stay focused!" + Ansi.RESET);
    }

    /** Remove all lines tagged for the given guarded domain. Caller must unlock/lock. */
    static void removeGuardedEntries(String domain) throws IOException {
        String marker = Markers.GUARD + ":" + domain;
        List<String> lines = HostsFile.readLines();
        List<String> kept = new java.util.ArrayList<>(lines.size());
        for (String line : lines) {
            if (!line.contains(marker)) kept.add(line);
        }
        // Drop any trailing empty lines that accumulated
        while (!kept.isEmpty() && kept.get(kept.size() - 1).isEmpty()) {
            kept.remove(kept.size() - 1);
        }
        HostsFile.writeLines(kept);
    }

    /** List guarded + fast entries, grouped by kind. */
    static void list() throws IOException {
        Set<String> guarded = new TreeSet<>();
        Set<String> fast = new TreeSet<>();
        for (String line : HostsFile.readLines()) {
            if (line.startsWith("#")) continue;
            if (line.contains(Markers.GUARD + ":")) {
                String dom = extractSecondToken(line);
                if (dom != null && !dom.startsWith("www.")) guarded.add(dom);
            } else if (line.contains(Markers.FAST + ":")) {
                String host = extractSecondToken(line);
                if (host != null) fast.add(host);
            }
        }

        if (guarded.isEmpty() && fast.isEmpty()) {
            Ui.println(Ansi.GREEN + "No entries managed by focus-guard." + Ansi.RESET);
            return;
        }

        if (!guarded.isEmpty()) {
            Ui.println(Ansi.BOLD + "Guarded (gauntlet to remove):" + Ansi.RESET);
            for (String d : guarded) {
                Ui.println("  " + Ansi.RED + "🚫" + Ansi.RESET + " " + d);
            }
        }

        if (!fast.isEmpty()) {
            if (!guarded.isEmpty()) Ui.println();
            Ui.println(Ansi.BOLD + "Fast (instant add/remove):" + Ansi.RESET);
            for (String h : fast) {
                Ui.println("  " + Ansi.CYAN + "⚡" + Ansi.RESET + " " + h);
            }
        }
    }

    /** Extract the second whitespace-separated token (the hostname) from a hosts line. */
    private static String extractSecondToken(String line) {
        String[] parts = line.trim().split("\\s+");
        return parts.length >= 2 ? parts[1] : null;
    }
}
