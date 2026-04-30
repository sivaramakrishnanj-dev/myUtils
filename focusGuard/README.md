# focus-guard

Block/unblock distracting domains with deliberate friction, plus a separate
fast-mode for arbitrary `/etc/hosts` entries used for testing.

Written in Java so the gauntlet logic is opaque in the distributed JAR (not
readable like a shell script). A thin shell wrapper handles `sudo` and invokes
the JAR.

---

## What it does

### Guard mode (the original focus-guard)

- **`block <domain>`** — instant: appends `127.0.0.1 <domain>` and
  `127.0.0.1 www.<domain>` to `/etc/hosts`, flushes DNS, and re-locks the
  file with `chflags schg`.
- **`unblock <domain>`** — the gauntlet:
  1. 5 forced-answer reflection questions.
  2. Memory challenge: pick a number in `[50B, 250B]` divisible by 7, input hidden.
  3. Round 1 (15-min timer, +5 min per restart): `N / 7` — 2 attempts.
  4. Round 2 (15-min timer): `digit_sum(R1) × 13` — 1 attempt.
  5. Round 3 (15-min timer): `(R2 × R2) mod 97` — 1 attempt.
  6. Round 4 (15-min timer, 60-sec input limit): `(R3 × 7 + R3²) mod 53` — 1 attempt.
  7. 3 more reflection questions + type the exact sentence
     *"I am making a conscious choice, not a compulsive one."*

  Any wrong answer or timeout restarts the whole gauntlet and bumps the Round 1
  timer by 5 minutes.

### Fast mode (new — for testing / temporary routing)

- **`fast-add <ip> <host>`** — instant add, e.g. `fast-add 127.0.0.1 api.local`.
- **`fast-del <host>`** — instant remove of the fast entry.
- **`fast-clear`** — wipe all fast entries.

Fast entries are tagged `# FOCUS-FAST:<host>` in `/etc/hosts`. They are isolated
from guard entries:

- Fast-mode commands only ever modify `FOCUS-FAST` lines.
- The gauntlet only ever modifies `FOCUS-GUARD` lines.

You cannot accidentally blow away your guarded domains with `fast-clear`.

### List

- **`list`** — shows guarded domains and fast entries in two grouped sections.

---

## Install

### 1. Build the JAR

```bash
cd /Users/sivarj/my/workspace/utilWorkspace/myUtils/focusGuard
mvn clean package
```

The executable JAR is produced at `target/focus-guard.jar`.

### 2. Copy the JAR to your scripts lib

```bash
mkdir -p ~/my/scripts/lib
cp target/focus-guard.jar ~/my/scripts/lib/focus-guard.jar
```

### 3. Install the shell wrapper

The wrapper `focus-guard` at `~/my/scripts/focus-guard` handles the `sudo`
requirement and launches the JAR. Make it executable:

```bash
chmod +x ~/my/scripts/focus-guard
```

Ensure `~/my/scripts` is on your `PATH` (add this to `~/.zshrc` if not already):

```bash
export PATH="$HOME/my/scripts:$PATH"
```

### 4. (Optional) Remove the source

If you want the logic to be harder to inspect, you can delete or move this
Maven project after building. The shipped JAR is all you need to run the tool.

> Note: JAR bytecode is decompilable with tools like `javap`, CFR, or Procyon.
> Removing the source gives you "casually private" — not "cryptographically
> secret". For true obfuscation, add ProGuard or compile to a native image
> with GraalVM.

---

## Usage

All commands require `sudo` because they modify `/etc/hosts`:

```bash
# Guarded (focus) mode
sudo focus-guard block   twitter.com
sudo focus-guard unblock twitter.com     # 1-hour gauntlet
sudo focus-guard list

# Fast mode (testing / temporary mapping)
sudo focus-guard fast-add 127.0.0.1 api.local
sudo focus-guard fast-add 192.168.1.50 myserver.lan
sudo focus-guard fast-del api.local
sudo focus-guard fast-clear
```

---

## Design notes

### Why Java instead of bash

- **Opacity.** The bash source reveals the exact math formulas for the
  gauntlet, which lets a determined user compute answers offline. Compiled
  Java bytecode is a higher bar (decompilable, but not trivially readable).
- **Input hidden with Console.readPassword().** Cleaner than `read -s`.
- **Timed input** for Round 4 is handled on a daemon thread so the 60-second
  cutoff is precise.
- **Single self-contained JAR** ships with no dependencies — just
  `java -jar focus-guard.jar`.

### Marker scheme

Two distinct markers keep the two modes strictly isolated:

| Mode   | Marker tag in `/etc/hosts` | Who can remove      |
|--------|---------------------------|---------------------|
| Guard  | `# FOCUS-GUARD:<domain>`  | only the gauntlet   |
| Fast   | `# FOCUS-FAST:<host>`     | only fast-del/clear |

The isolation is enforced in three places:

1. `Blocker.removeGuardedEntries(...)` filters by the GUARD marker only.
2. `FastMode` explicitly skips any line containing `FOCUS-GUARD:` before
   removing anything.
3. `fast-clear` walks the file line-by-line and preserves every `FOCUS-GUARD`
   line even as it strips `FOCUS-FAST` lines.

### `/etc/hosts` locking

macOS supports the `schg` system-immutable flag. We apply it via `chflags schg`
after every write so the file cannot be hand-edited even by root without first
running `chflags noschg`. All mutations follow the pattern:

```java
HostsFile.unlock();
try {
    // mutate
    HostsFile.flushDns();
} finally {
    HostsFile.lock();
}
```

### DNS flush

```bash
dscacheutil -flushcache
killall -HUP mDNSResponder
```

Both are fired after any `/etc/hosts` change so effects are immediate.

---

## Project layout

```
focusGuard/
├── pom.xml
├── README.md
└── src/main/java/com/srk/focusguard/
    ├── Main.java          # CLI dispatcher
    ├── Ansi.java          # ANSI color constants
    ├── Ui.java            # Prompts, hidden input, timers
    ├── Markers.java       # GUARD and FAST tag constants
    ├── HostsFile.java     # read/write/lock/flush DNS
    ├── Blocker.java       # block + list + remove-guarded
    ├── Gauntlet.java      # the hour-long unblock ritual
    └── FastMode.java      # fast-add / fast-del / fast-clear
```

---

## Development

Build:

```bash
mvn clean package
```

Run directly (requires sudo for real use):

```bash
sudo java -jar target/focus-guard.jar list
```

The final JAR is `target/focus-guard.jar` — `finalName` is set in `pom.xml`
so the filename is stable.

---

## License

Personal utility — use at your own risk. The hour-long gauntlet is by design;
don't enable this on a machine you cannot afford to be inconvenienced on.
