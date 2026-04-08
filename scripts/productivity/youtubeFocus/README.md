# YouTube Focus Mode

A Tampermonkey userscript that strips YouTube down to a search tool. No recommendations, no Shorts, no end-screen traps — just the video you came for.

## Why This Exists

YouTube's recommendation engine is designed with one goal: keep you watching as long as possible. Every thumbnail on the homepage, every "Up Next" autoplay, every end-screen card is an algorithmically optimized invitation to stay longer than you intended. What starts as "I'll watch one tutorial" turns into 45 minutes of unrelated videos.

This script removes the engine's surface area entirely. You open YouTube, search for exactly what you need, watch it, and leave. The algorithm never gets a foothold.

## What It Hides

| Element | Where |
|---|---|
| Recommendations feed | Homepage |
| Filter/category chips | Homepage |
| Shorts shelves | Everywhere |
| Sidebar recommendations | Watch page |
| Autoplay / Up Next UI | Watch page |
| End screen cards & video wall | Watch page |
| Info cards overlay | Video player |

## What It Leaves Alone

Search results are fully visible — that is intentional use. Your personal pages (playlists, library, history, subscriptions, channels) are also untouched.

## Installation

1. Install the [Tampermonkey](https://www.tampermonkey.net/) browser extension.
2. Open the Tampermonkey dashboard → **Create a new script**.
3. Replace the default content with the contents of `youtube-focus-mode.js`.
4. Save. The script activates immediately on any YouTube tab.

## Usage

The script is **on by default**. A small floating button appears in the bottom-right corner of every YouTube page:

- **🎯 Focus: ON** — distractions are hidden
- **😴 Focus: OFF** — YouTube is back to normal

Click the button to toggle. Your preference is saved across sessions via `localStorage`, so it persists through page refreshes and browser restarts.

## How It Protects You

The recommendation algorithm exploits a simple psychological loop: you see a thumbnail, curiosity fires, you click, and the cycle repeats. The only reliable way to break the loop is to never see the thumbnail in the first place.

This script cuts the loop at the source. The homepage shows a prompt to use the search bar instead of a feed. The watch page shows only the video you chose, with no sidebar pulling your attention sideways. End screens are blank, so when a video finishes, there is no visual cue to keep going.

The toggle button exists for the rare case where you genuinely need to browse — but the friction of turning it off is a small, intentional reminder to ask yourself whether you actually need to.
