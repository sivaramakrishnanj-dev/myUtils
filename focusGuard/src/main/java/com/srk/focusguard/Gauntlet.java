package com.srk.focusguard;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * The unblock gauntlet: a deliberately punishing hour-long sequence of
 * reflection questions, a memorized large number, timed math rounds with
 * hidden input, and a final typed-confirmation sentence.
 *
 * <p>Any wrong answer or timeout restarts the entire gauntlet and slightly
 * increases the Round 1 timer as a soft deterrent against retries.
 *
 * <p>This class only ever operates on FOCUS-GUARD entries. It never touches
 * FAST entries — that separation is enforced in {@link Blocker#removeGuardedEntries}.
 */
final class Gauntlet {

    private static final int ROUND_DELAY_SEC = 15 * 60;   // 15 minutes base
    private static final int ROUND1_BONUS_PER_RESTART_SEC = 5 * 60; // +5 min per restart
    private static final int ROUND4_INPUT_TIMEOUT_SEC = 60;

    private static final long N_MIN =  50_000_000_000L;
    private static final long N_MAX = 250_000_000_000L;

    private final SecureRandom rng = new SecureRandom();

    Gauntlet() {}

    /**
     * Run the gauntlet for {@code domain}. Returns only when the user either
     * completes the gauntlet and confirms, or aborts at the final sentence.
     */
    void unblock(String domain) throws IOException {
        if (!HostsFile.readAll().contains(Markers.GUARD + ":" + domain)) {
            Ui.println(Ansi.YELLOW + domain + " is not currently guarded." + Ansi.RESET);
            return;
        }

        int restartCount = 0;

        while (true) {
            printHeader(domain, restartCount);

            // Phase 0 — reflection
            askReflectionQuestions(domain);

            // Memory challenge: pick N in [50B, 250B] divisible by 7
            long n = readMemoryNumber();

            // Round 1 — N / 7 (2 attempts)
            int r1Delay = ROUND_DELAY_SEC + restartCount * ROUND1_BONUS_PER_RESTART_SEC;
            Ui.runTimer(r1Delay, "Round 1 of 4");
            long r1 = n / 7;
            if (!askRound1(r1)) { restartCount++; restartSignal(restartCount); continue; }

            // Round 2 — digit_sum(R1) * 13 (1 attempt)
            Ui.runTimer(ROUND_DELAY_SEC, "Round 2 of 4");
            long r2 = (long) digitSum(r1) * 13L;
            if (!askRoundSingleAttempt(
                    "ROUND 2 MATH — 1 attempt",
                    "Sum all digits of your result, then multiply that sum by 13.",
                    r2)) { restartCount++; restartSignal(restartCount); continue; }

            // Round 3 — (R2 * R2) mod 97 (1 attempt)
            Ui.runTimer(ROUND_DELAY_SEC, "Round 3 of 4");
            long r3 = Math.floorMod(r2 * r2, 97L);
            if (!askRoundSingleAttempt(
                    "ROUND 3 MATH — 1 attempt",
                    "Square your result, then take modulo 97.",
                    r3)) { restartCount++; restartSignal(restartCount); continue; }

            // Round 4 — (R3 * 7 + R3^2) mod 53 (60-sec timed, 1 attempt)
            Ui.runTimer(ROUND_DELAY_SEC, "Round 4 of 4 — Final");
            long r4 = Math.floorMod(r3 * 7L + r3 * r3, 53L);
            if (!askRound4Timed(r4)) { restartCount++; restartSignal(restartCount); continue; }

            break; // gauntlet complete
        }

        // Final reckoning
        finalReckoning(domain);

        if (!confirmSentence()) {
            Ui.println();
            Ui.println(Ansi.GREEN + "🛡️  Good call. " + domain
                    + " stays blocked. Back to work!" + Ansi.RESET);
            return;
        }

        // Remove only guarded entries for this domain.
        HostsFile.unlock();
        try {
            Blocker.removeGuardedEntries(domain);
            HostsFile.flushDns();
        } finally {
            HostsFile.lock();
        }

        Ui.println();
        Ui.println(Ansi.YELLOW + "🔓 " + domain + " is unblocked." + Ansi.RESET);
        Ui.println(Ansi.YELLOW + "   Re-block it when done: " + Ansi.BOLD
                + "sudo focus-guard block " + domain + Ansi.RESET);
    }

    // ── Phases ────────────────────────────────────────────────────────────

    private void printHeader(String domain, int restartCount) {
        Ui.println();
        Ui.println(Ansi.RED + "━".repeat(53) + Ansi.RESET);
        if (restartCount > 0) {
            Ui.println(Ansi.RED + "  🔁 RESTART #" + restartCount + " — You failed the gauntlet." + Ansi.RESET);
            int r1Mins = (ROUND_DELAY_SEC + restartCount * ROUND1_BONUS_PER_RESTART_SEC) / 60;
            Ui.println(Ansi.RED + "  Round 1 timer is now " + r1Mins + " minutes." + Ansi.RESET);
        } else {
            Ui.println(Ansi.RED + "  ⚠️  You are about to unblock: " + Ansi.BOLD + domain + Ansi.RESET);
        }
        Ui.println(Ansi.RED + "━".repeat(53) + Ansi.RESET);
        Ui.println();
    }

    private void askReflectionQuestions(String domain) {
        Ui.println(Ansi.YELLOW + "Answer these honestly before the timer starts:" + Ansi.RESET);
        Ui.askAndRequireAnswer("1. What specific, productive task requires " + domain + " right now?");
        Ui.askAndRequireAnswer("2. Can this wait until your next scheduled break? If not, why?");
        Ui.askAndRequireAnswer("3. Is this a genuine need or a dopamine craving? Be brutally honest.");
        Ui.askAndRequireAnswer("4. Name the last 3 things you accomplished today before this urge hit.");
        Ui.askAndRequireAnswer("5. What will you lose — in focus, time, and self-respect — if you open "
                + domain + " right now?");
    }

    private long readMemoryNumber() {
        Ui.println(Ansi.CYAN + "━".repeat(53) + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  🔢 MEMORY CHALLENGE" + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  Enter a number between 50,000,000,000 and 250,000,000,000" + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  that is divisible by 7. Your input will not be shown." + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  Memorize it — you will need it for the next hour." + Ansi.RESET);
        Ui.println(Ansi.CYAN + "━".repeat(53) + Ansi.RESET);
        Ui.println();

        while (true) {
            Ui.print("→ ");
            String raw = Ui.readLineHidden();
            Ui.println();
            if (raw == null) {
                // EOF — force exit rather than loop forever
                Ui.println(Ansi.RED + "  Input closed. Aborting." + Ansi.RESET);
                System.exit(1);
            }
            raw = raw.trim();
            if (!raw.matches("^[0-9]+$")) {
                Ui.println(Ansi.RED + "  Invalid. Try again." + Ansi.RESET);
                continue;
            }
            long n;
            try {
                n = Long.parseLong(raw);
            } catch (NumberFormatException e) {
                Ui.println(Ansi.RED + "  Invalid. Try again." + Ansi.RESET);
                continue;
            }
            if (n < N_MIN || n > N_MAX || n % 7 != 0) {
                Ui.println(Ansi.RED + "  Invalid. Try again." + Ansi.RESET);
                continue;
            }
            Ui.println(Ansi.GREEN + "  ✓ Number accepted. Memorize it — it will never be shown again." + Ansi.RESET);
            Ui.println();
            // Silence the unused warning on rng; reserved for future randomized prompts.
            if (rng == null) throw new IllegalStateException();
            return n;
        }
    }

    private boolean askRound1(long expected) {
        Ui.println(Ansi.CYAN + "━".repeat(53) + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  🧮 ROUND 1 MATH — 2 attempts" + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  Divide your number by 7. Enter the result." + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  (Your input will not be shown)" + Ansi.RESET);
        Ui.println(Ansi.CYAN + "━".repeat(53) + Ansi.RESET);
        Ui.println();

        for (int attempt = 1; attempt <= 2; attempt++) {
            Ui.print("  Attempt " + attempt + "/2 → ");
            String ans = Ui.readLineHidden();
            Ui.println();
            if (ans != null && ans.trim().equals(Long.toString(expected))) {
                Ui.println(Ansi.GREEN + "  ✓ Correct. Memorize this result — it will not be shown again." + Ansi.RESET);
                Ui.println();
                return true;
            }
            if (attempt < 2) {
                Ui.println(Ansi.RED + "  Wrong. One more attempt." + Ansi.RESET);
            }
        }
        return false;
    }

    private boolean askRoundSingleAttempt(String title, String instruction, long expected) {
        Ui.println(Ansi.CYAN + "━".repeat(53) + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  🧮 " + title + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  " + instruction + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  Enter the final answer. (Input hidden)" + Ansi.RESET);
        Ui.println(Ansi.CYAN + "━".repeat(53) + Ansi.RESET);
        Ui.println();

        Ui.print("  → ");
        String ans = Ui.readLineHidden();
        Ui.println();
        if (ans == null || !ans.trim().equals(Long.toString(expected))) return false;

        Ui.println(Ansi.GREEN + "  ✓ Correct. Memorize this result — it will not be shown again." + Ansi.RESET);
        Ui.println();
        return true;
    }

    private boolean askRound4Timed(long expected) {
        Ui.println(Ansi.CYAN + "━".repeat(53) + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  🧮 ROUND 4 MATH — 1 attempt — ⏱ 60 SECONDS" + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  Multiply your result by 7, add the square of your result," + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  then take modulo 53. Enter the answer. (Input hidden)" + Ansi.RESET);
        Ui.println(Ansi.CYAN + "━".repeat(53) + Ansi.RESET);
        Ui.println();

        Ui.print("  → ");
        String ans = Ui.readLineHiddenWithTimeout(ROUND4_INPUT_TIMEOUT_SEC);
        Ui.println();
        if (ans == null) {
            Ui.println(Ansi.RED + "  ✗ Time's up." + Ansi.RESET);
            return false;
        }
        if (!ans.trim().equals(Long.toString(expected))) {
            Ui.println(Ansi.RED + "  ✗ Incorrect." + Ansi.RESET);
            return false;
        }
        Ui.println(Ansi.GREEN + "  ✓ Correct. Gauntlet complete." + Ansi.RESET);
        Ui.println();
        return true;
    }

    private void restartSignal(int restartCount) {
        Ui.println();
        Ui.println(Ansi.RED + "  ✗ Restarting. [Restart #" + restartCount + "]" + Ansi.RESET);
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void finalReckoning(String domain) {
        Ui.println(Ansi.RED + "━".repeat(53) + Ansi.RESET);
        Ui.println(Ansi.RED + "  ⏰ One hour has passed. Final reckoning:" + Ansi.RESET);
        Ui.println(Ansi.RED + "━".repeat(53) + Ansi.RESET);
        Ui.println();

        Ui.askAndRequireAnswer("6. You waited over an hour. Write down exactly what you will do on "
                + domain + " and for how long — be specific.");
        Ui.askAndRequireAnswer("7. What is the single most important task you are abandoning right now to open "
                + domain + "? Is it worth it?");
        Ui.askAndRequireAnswer("8. If your future self — 5 years from now — could see this moment, "
                + "what would they say to you?");
    }

    private boolean confirmSentence() {
        String expected = "I am making a conscious choice, not a compulsive one.";
        Ui.println(Ansi.RED + Ansi.BOLD
                + "Final confirmation — type the full sentence exactly:" + Ansi.RESET);
        Ui.println(Ansi.CYAN + "  " + expected + Ansi.RESET);
        Ui.println();
        Ui.print("→ ");
        String typed = Ui.readLineVisible();
        return typed != null && typed.equals(expected);
    }

    // ── Math helpers ──────────────────────────────────────────────────────

    private static int digitSum(long n) {
        if (n < 0) n = -n;
        int sum = 0;
        while (n > 0) {
            sum += (int) (n % 10);
            n /= 10;
        }
        return sum;
    }
}
