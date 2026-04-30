package com.srk.focusguard;

/** ANSI color codes for terminal output. */
final class Ansi {
    static final String RED    = "\u001B[0;31m";
    static final String GREEN  = "\u001B[0;32m";
    static final String YELLOW = "\u001B[1;33m";
    static final String CYAN   = "\u001B[0;36m";
    static final String BOLD   = "\u001B[1m";
    static final String RESET  = "\u001B[0m";

    private Ansi() {}
}
