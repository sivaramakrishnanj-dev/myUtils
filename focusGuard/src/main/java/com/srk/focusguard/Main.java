package com.srk.focusguard;

import java.io.IOException;

/**
 * CLI dispatcher.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code block &lt;domain&gt;}            — instant guarded block</li>
 *   <li>{@code unblock &lt;domain&gt;}          — hour-long gauntlet to unblock</li>
 *   <li>{@code list}                       — list guarded + fast entries</li>
 *   <li>{@code fast-add &lt;ip&gt; &lt;host&gt;}      — instant add (testing)</li>
 *   <li>{@code fast-del &lt;host&gt;}           — instant remove of a fast entry</li>
 *   <li>{@code fast-clear}                 — remove all fast entries</li>
 * </ul>
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            usage();
            return;
        }

        String cmd = args[0];
        switch (cmd) {
            case "-h", "--help", "help" -> { usage(); return; }
            default -> { /* fall through to commands below */ }
        }

        // All commands below this point need root to read /etc/hosts or mutate it.
        HostsFile.requireRoot();

        switch (cmd) {
            case "block" -> {
                requireArg(args, 1, "Provide a domain: sudo focus-guard block twitter.com");
                Blocker.block(args[1]);
            }
            case "unblock" -> {
                requireArg(args, 1, "Provide a domain: sudo focus-guard unblock twitter.com");
                new Gauntlet().unblock(args[1]);
            }
            case "list" -> Blocker.list();

            case "fast-add" -> {
                requireArg(args, 1, "Usage: sudo focus-guard fast-add <ip> <host>");
                requireArg(args, 2, "Usage: sudo focus-guard fast-add <ip> <host>");
                FastMode.fastAdd(args[1], args[2]);
            }
            case "fast-del" -> {
                requireArg(args, 1, "Usage: sudo focus-guard fast-del <host>");
                FastMode.fastDel(args[1]);
            }
            case "fast-clear" -> FastMode.fastClear();

            default -> {
                Ui.println(Ansi.RED + "Unknown command: " + cmd + Ansi.RESET);
                usage();
                System.exit(1);
            }
        }
    }

    private static void requireArg(String[] args, int idx, String message) {
        if (args.length <= idx) {
            Ui.println(message);
            System.exit(1);
        }
    }

    private static void usage() {
        Ui.println(Ansi.BOLD + "Usage:" + Ansi.RESET);
        Ui.println("  sudo focus-guard block      <domain>       — Block immediately (gauntlet to remove)");
        Ui.println("  sudo focus-guard unblock    <domain>       — Unblock (1-hour gauntlet)");
        Ui.println("  sudo focus-guard list                      — Show all managed entries");
        Ui.println();
        Ui.println("  sudo focus-guard fast-add   <ip> <host>    — Instant add (isolated from guard)");
        Ui.println("  sudo focus-guard fast-del   <host>         — Instant remove of a fast entry");
        Ui.println("  sudo focus-guard fast-clear                — Remove ALL fast entries");
        Ui.println();
        Ui.println(Ansi.YELLOW
                + "Fast mode never touches guarded entries, and the gauntlet never touches fast entries."
                + Ansi.RESET);
    }
}
