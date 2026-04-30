package com.srk.focusguard;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Terminal UI helpers: prompts, hidden input, and countdown timers.
 *
 * <p>Hidden input relies on {@link java.io.Console}. When running under a TTY
 * (i.e. the user launched {@code sudo focus-guard ...} directly), this is
 * available. When there is no TTY (e.g. stdin redirected), we fall back to
 * visible input so the tool still works but without masking.
 */
final class Ui {

    private static final Console CONSOLE = System.console();
    private static final BufferedReader FALLBACK_READER =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    private Ui() {}

    // ── Printing ──────────────────────────────────────────────────────────

    static void println(String s) {
        System.out.println(s);
    }

    static void println() {
        System.out.println();
    }

    static void print(String s) {
        System.out.print(s);
        System.out.flush();
    }

    // ── Prompts ───────────────────────────────────────────────────────────

    /**
     * Ask a question and insist on a non-empty answer. The answer itself is
     * discarded — we only care that the user typed something.
     */
    static void askAndRequireAnswer(String question) {
        println();
        println(Ansi.CYAN + question + Ansi.RESET);
        String answer = "";
        while (answer == null || answer.isBlank()) {
            print("→ ");
            answer = readLineVisible();
            if (answer == null) {
                // stdin closed — treat as empty to force loop (but break to avoid infinite loop if EOF)
                answer = "";
                if (!CONSOLE_AVAILABLE()) return;
            }
        }
        println();
    }

    /** Read a visible line of input. Returns null on EOF. */
    static String readLineVisible() {
        try {
            if (CONSOLE != null) {
                return CONSOLE.readLine();
            }
            return FALLBACK_READER.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Read a line of input with input hidden (no echo). Falls back to visible
     * read if no TTY is available.
     */
    static String readLineHidden() {
        if (CONSOLE != null) {
            char[] chars = CONSOLE.readPassword();
            return chars == null ? null : new String(chars);
        }
        return readLineVisible();
    }

    /**
     * Read a hidden line with a time limit in seconds. Returns null on timeout
     * or EOF. The timer starts when this method is called.
     */
    static String readLineHiddenWithTimeout(int seconds) {
        final String[] result = {null};
        final boolean[] done = {false};
        Thread reader = new Thread(() -> {
            String line = readLineHidden();
            synchronized (done) {
                if (!done[0]) {
                    result[0] = line;
                    done[0] = true;
                    done.notifyAll();
                }
            }
        }, "focus-guard-input");
        reader.setDaemon(true);
        reader.start();

        synchronized (done) {
            long deadline = System.currentTimeMillis() + seconds * 1000L;
            while (!done[0]) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    done[0] = true;
                    return null;
                }
                try {
                    done.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return result[0];
        }
    }

    // ── Timers ────────────────────────────────────────────────────────────

    /**
     * Run a countdown timer for the given number of seconds, updating a single
     * line of output each second. Cannot be skipped — the only way out is to
     * close the terminal.
     */
    static void runTimer(int totalSeconds, String label) {
        println(Ansi.YELLOW + "  ⏳ " + label + " — " + Ansi.BOLD
                + (totalSeconds / 60) + " minutes" + Ansi.RESET
                + Ansi.YELLOW + ". Close terminal to cancel." + Ansi.RESET);
        println();
        for (int i = totalSeconds; i >= 1; i--) {
            int mm = i / 60;
            int ss = i % 60;
            System.out.print(String.format("\r  ⏳ Time remaining: %02d:%02d ", mm, ss));
            System.out.flush();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        println();
        println();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    @SuppressWarnings("PMD.MethodNamingConventions")
    private static boolean CONSOLE_AVAILABLE() {
        return CONSOLE != null;
    }
}
