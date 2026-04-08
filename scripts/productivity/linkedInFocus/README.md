# LinkedIn Focus Mode

A Tampermonkey userscript that transforms the LinkedIn feed into a distraction-free, text-only reading experience with a dark terminal aesthetic. Active only on `/feed/` — the rest of LinkedIn renders normally.

## Why This Exists

LinkedIn is a professional platform with genuine value: industry insights, technical articles, career lessons, and knowledge shared by people you chose to follow. But the feed is also engineered to keep you scrolling — recommended posts from people you never followed, reaction counts designed to trigger social comparison, images and videos that hijack attention before you've even read the text.

The result: you open LinkedIn to learn something, and 20 minutes later you've consumed a stream of content you didn't choose, from people you don't know, and retained almost none of it.

This script strips the feed back to its signal. You read text. You decide what deserves more attention. Media and comments are one click away — but only when you choose.

## What It Does

### On `/feed/` (Focus Mode active)

| Element | Behaviour |
|---|---|
| Posts from people you don't follow | Hidden — replaced with `[ promoted / recommended post — hidden ]` |
| Promoted / Sponsored posts | Hidden |
| Images and videos in posts | Hidden by default — `[ + show media ]` button to reveal |
| Comments section | Hidden by default — `[ + show comments ]` button to reveal |
| Like / reaction counts | Hidden |
| Sidebar recommendations | Hidden |
| Theme | Dark terminal — black background, dark green text |

### On all other LinkedIn pages

No changes. The script is completely inactive outside `/feed/`.

## Installation

1. Install the [Tampermonkey](https://www.tampermonkey.net/) browser extension.
2. Open the Tampermonkey dashboard → **Create a new script**.
3. Replace the default content with the contents of `linkedin-focus-mode.js`.
4. Save. Navigate to `linkedin.com/feed` — focus mode activates immediately.

## Usage

A floating button appears in the bottom-right corner of the feed:

- **🟢 Focus: ON** — distractions hidden, terminal theme active
- **⚪ Focus: OFF** — LinkedIn renders normally

Click to toggle. Your preference is saved across sessions. Turning focus off reloads the page to cleanly restore the original LinkedIn layout.

### Revealing Media or Comments

When a post has media or comments, small buttons appear inline:

```
[ + show media ]
[ + show comments ]
```

Click either to expand that section for that specific post. Everything else stays hidden. This keeps you in control — you expand only what genuinely earns your attention after reading the text.

## How It Protects Your Focus

LinkedIn's feed mixes content you subscribed to with content the algorithm thinks will keep you engaged. These are not the same thing. Recommended posts from strangers, viral reactions, and trending videos are optimized for time-on-site, not for your learning.

By hiding non-followed posts entirely, the feed becomes a curated stream of people you deliberately chose to follow. By hiding media by default, you read the idea first — and only pull up the image or video if the text earns it. By hiding comments and reaction counts, you remove the social validation loop that turns a 2-minute read into a 15-minute scroll through replies.

The terminal theme is intentional. It signals to your brain that this is a reading and thinking environment, not an entertainment feed.
