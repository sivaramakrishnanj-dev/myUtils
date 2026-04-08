# focus-guard

A command-line productivity tool that blocks distracting websites and makes unblocking them deliberately hard — protecting you from your own impulses.

## Why This Exists

Every time you open Instagram, Netflix, or Prime Video during a work session, you don't just lose the minutes you spend there. You lose the 15–20 minutes of deep focus it takes to get back into the zone. Over a day, these micro-distractions silently consume hours that could have gone toward your actual goals.

The problem isn't willpower — it's friction. Distracting sites are one click away, and your brain is wired to seek that dopamine hit the moment work gets hard or boring. `focus-guard` fixes this by making the cost of unblocking a domain high enough that your rational mind has time to override the craving.

Blocking is instant. Unblocking takes **a minimum of 1 hour**.

## How It Works

### Blocking a Domain

```bash
sudo focus-guard block netflix.com
sudo focus-guard block instagram.com
sudo focus-guard block primevideo.com
```

The domain is blocked immediately. Both `domain.com` and `www.domain.com` are redirected to localhost. The hosts file is then locked so it cannot be casually edited.

### Listing Blocked Domains

```bash
sudo focus-guard list
```

### Unblocking a Domain

```bash
sudo focus-guard unblock netflix.com
```

This starts the gauntlet. Here's what happens:

**Step 1 — Reflection (before the timer starts)**
You must answer 5 questions about why you want to unblock the domain. Empty answers are not accepted. This forces your prefrontal cortex — the rational part of your brain — to engage before the craving wins.

**Step 2 — Memory Challenge**
You are asked to pick and memorize a large number. You will need it throughout the next hour. It is never displayed again.

**Step 3 — Four 15-Minute Rounds**
The timer runs four times, back to back. After each 15-minute wait, you must answer a mental challenge based on the number you memorized. The challenges get progressively harder with each round.

- After Round 1: You get **2 attempts** to answer correctly.
- After Rounds 2, 3, and 4: You get **1 attempt** each. Round 4 also has a **60-second time limit**.

**Step 4 — Final Questions**
After all four rounds, you answer 3 final reflection questions and type a specific confirmation sentence to complete the unblock.

### The Penalty System

If you answer any challenge incorrectly, the **entire process restarts from the beginning** — questions, memory challenge, and all four timers. Every restart also adds **5 minutes** to the first timer, so the more you fail, the longer the minimum wait grows.

This is intentional. The goal is not to punish you — it's to give your craving enough time to pass so you can make a clear-headed decision.

## How It Protects You From Cravings

Dopamine cravings are short-lived. Research consistently shows that the urge to check social media or watch a video typically fades within 10–20 minutes if you don't act on it. `focus-guard` exploits this:

- The **initial questions** interrupt the automatic, impulsive response and make you articulate a reason.
- The **1-hour minimum wait** outlasts the craving in almost every case. By the time the timer ends, the urge is usually gone.
- The **mental challenges** keep your mind engaged and make the process feel like work — which further reduces the appeal of the reward.
- The **restart penalty** means that a moment of carelessness costs you another full hour, making repeated attempts increasingly costly.

Most of the time, you will close the terminal before the hour is up and go back to work. That is the intended outcome.

## Installation

```bash
# Copy to a directory in your PATH
sudo cp focus-guard /usr/local/bin/focus-guard
sudo chmod +x /usr/local/bin/focus-guard
```

## Requirements

- macOS (uses `dscacheutil`, `mDNSResponder`, and `chflags`)
- Must be run with `sudo`
