package com.srk.focusguard;

/**
 * Tag markers used inside /etc/hosts so we can identify entries
 * owned by this tool and separate guarded (gauntlet) entries from
 * fast (instant) entries.
 */
final class Markers {
    /** Tag for gauntlet-protected domains. Removal requires the hour-long gauntlet. */
    static final String GUARD = "# FOCUS-GUARD";

    /** Tag for fast entries. Removal is instant; gauntlet never touches these. */
    static final String FAST = "# FOCUS-FAST";

    private Markers() {}
}
