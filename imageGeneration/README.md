# imagegen

A single-jar CLI that generates and edits images with Google's Gemini image models
(Nano Banana). Built so a Claude agent can drive it: stdout is machine-readable,
exit codes classify failures, and every error names the next action.

Claude can't render images. This fills that gap — the agent shells out, gets back a
file path, and can then read, show, or keep editing the result.

## Build

```bash
mvn clean package
```

Produces a self-contained `target/imagegen-cli.jar` (~2.3 MB, Jackson shaded in).
Requires JDK 17+ (built and tested against Corretto 21).

Put it on your PATH:

```bash
ln -s "$PWD/imagegen" ~/bin/imagegen
```

The wrapper resolves symlinks, so it finds the jar wherever the repo lives. Override
with `IMAGEGEN_JAR=/path/to/imagegen-cli.jar` if you move the jar on its own.

## Configure

```bash
imagegen config --init          # writes ~/.config/imagegen/config.json, mode 600
```

Then paste your [Gemini API key](https://aistudio.google.com/apikey) into `apiKey`.

```json
{
  "apiKey": "AIza...",
  "model": "gemini-3.1-flash-image",
  "resolution": "1K",
  "mimeType": "image/png",
  "aspectRatio": null,
  "thinkingLevel": null,
  "outDir": null,
  "timeoutSeconds": 180,
  "retries": 2
}
```

Key precedence, highest first:

| Source | |
|---|---|
| `--api-key <key>` | per-call override |
| `$IMAGEGEN_API_KEY` | |
| `$GEMINI_API_KEY` | |
| `$GOOGLE_API_KEY` | |
| `apiKey` in the config file | |

Config file location: `--config <path>`, else `$IMAGEGEN_CONFIG`, else
`~/.config/imagegen/config.json`.

Check what's actually in effect (the key is redacted to its last 4 characters):

```bash
imagegen config --show
```

## Use

Generate from a prompt — lands in the current directory:

```bash
imagegen generate -p "a lighthouse at dusk, long exposure" -r 2K -a 16:9
# → ./out_001_a-lighthouse-at-dusk-long-exposure.png
```

Edit an existing image — lands beside the input:

```bash
imagegen edit -p "make the sky stormy" -i ~/pics/photo.jpg
# → ~/pics/out_001_photo.png
```

Refine what you just made, without re-uploading it:

```bash
imagegen edit -p "now add rain" --continue-from ~/pics/out_001_photo.png
# → ~/pics/out_002_photo.png
```

Check before you spend:

```bash
imagegen generate -p "a coffee shop logo" -o ./assets --dry-run
```

Multiple reference images in one call:

```bash
imagegen edit -p "put the person from the second image into the first scene" \
  -i scene.png -i person.jpg
```

Long prompts without shell-quoting pain:

```bash
imagegen generate --prompt-file ./prompt.txt -r 4K --thinking high
```

## Output naming

`out_<seq>_<base>.<ext>`

- **edit** — `<base>` is the first input's filename without extension; output goes in
  that file's directory.
- **generate** — `<base>` is a kebab-cased slug of the prompt, capped at 40
  characters, cut at a word boundary; output goes in the current directory.
- `<seq>` is the highest existing `out_<n>_*` in the target directory plus one,
  zero-padded to 3. Numbering is derived by scanning, so there's no counter file to
  drift, and moving files between folders stays safe.
- `<ext>` follows `--mime`, **not** the input. Editing `photo.jpg` with the default
  PNG output gives `out_001_photo.png`.
- An existing `out_<n>_` prefix is stripped from the base, so editing an output gives
  `out_002_photo.png` rather than `out_002_out_001_photo.png`.

Each image gets a sidecar `out_<seq>_<base>.json` holding the prompt, model,
settings and `interactionId`. That's what `--continue-from` reads.

Override the directory anywhere with `-o/--out-dir` (created if missing).

## Options

| Flag | Default | Notes |
|---|---|---|
| `-p, --prompt <text>` | — | Required for generate/edit |
| `--prompt-file <path>` | — | Read the prompt from a file |
| `-i, --image <path>` | — | Repeatable; required for edit |
| `-o, --out-dir <dir>` | see above | Created if missing |
| `-m, --model <id>` | `gemini-3.1-flash-image` | `imagegen models` lists them |
| `-r, --resolution` | `1K` | `512px` `1K` `2K` `4K` — uppercase K required |
| `-a, --aspect-ratio` | model's choice | `1:1 3:2 2:3 3:4 4:3 4:5 5:4 9:16 16:9 21:9` |
| `--mime` | `image/png` | or `image/jpeg` |
| `--thinking` | `minimal` | or `high` — slower, better on hard prompts |
| `-n, --count <int>` | 1 | N separate API calls; capped at 8 |
| `--continue-from <path>` | — | Chain from a previous output |
| `--dry-run` | off | Validate and show paths; no API call |
| `--format json\|text` | `json` | |
| `--emit-base64` | off | Also put base64 in the result JSON |
| `--quiet` | off | Silence stderr progress |
| `--debug-dump-response <path>` | — | Save the raw API response |
| `--config <path>` | `~/.config/imagegen/config.json` | |
| `--api-key <key>` | — | |
| `--timeout <seconds>` | 180 | |
| `--retries <n>` | 2 | Exponential backoff on 429/5xx |

## Agent contract

`imagegen help --agent` prints the full machine-readable contract in one call. The
essentials:

- **stdout** is a single JSON document for `generate`, `edit` and `config` — success
  or failure. **stderr** is human logging only; never parse it.
- Images go to disk and the result gives **absolute paths**. base64 is *not* returned
  unless you pass `--emit-base64` — a 4K PNG is tens of megabytes and would swamp an
  agent's context.
- Every error carries `status`, `code`, `message`, `hint`. The hint names the fix.

Exit codes:

| Code | Meaning | What to do |
|---|---|---|
| 0 | OK | — |
| 2 | Usage | Fix the flags |
| 3 | Config / auth | Missing or rejected API key |
| 4 | API, retryable | 429, 5xx or timeout — retry |
| 5 | API, permanent | Bad request or safety block — change the prompt |
| 6 | I/O | Local read/write problem |

Success payload:

```json
{
  "status": "ok",
  "command": "edit",
  "model": "gemini-3.1-flash-image",
  "resolution": "1K",
  "mimeType": "image/png",
  "prompt": "make the sky stormy",
  "sourceImages": ["/Users/me/pics/photo.jpg"],
  "outputs": [
    {
      "path": "/Users/me/pics/out_001_photo.png",
      "bytes": 1840233, "seq": 1,
      "sidecar": "/Users/me/pics/out_001_photo.json",
      "mimeType": "image/png", "width": 1024, "height": 1024
    }
  ],
  "interactionId": "int_abc123",
  "usage": { "input_tokens": 812, "output_tokens": 1290 },
  "thoughtImages": 2,
  "latencyMs": 7412,
  "dryRun": false
}
```

## Claude Code integration

A skill lives at `.claude/skills/imagegen/SKILL.md`. It's picked up automatically
when Claude runs in this repo. To make it available everywhere:

```bash
mkdir -p ~/.claude/skills
ln -s "$PWD/.claude/skills/imagegen" ~/.claude/skills/imagegen
```

Then just ask: *"generate a hero image for the landing page"*, or *"take
screenshot.png and remove the sidebar"*.

## Models

```bash
imagegen models
```

| Model | Notes |
|---|---|
| `gemini-3.1-flash-image` | **Default.** Generalist. 512px/1K/2K/4K. Refs: 10 object + 4 character. |
| `gemini-3.1-flash-lite-image` | Fastest and cheapest. 1K only. Refs: 14 object. Weaker at sequential edits. |
| `gemini-3-pro-image` | Highest quality, best world knowledge. Refs: 6 object + 5 character + 3 style. |
| `gemini-2.5-flash-image` | Legacy; prefer flash-lite. |

Reference images cap at 14 total. All outputs carry Google's SynthID watermark.

## How it talks to the API

Plain REST over `java.net.http.HttpClient` against
`POST https://generativelanguage.googleapis.com/v1beta/interactions`, auth via the
`x-goog-api-key` header. No Google SDK — the published Java samples for this API are
text-prompt-only and don't document how to attach input images or set
`response_format`, which is precisely what this tool needs. Raw REST is fully
documented and keeps the request under our control.

One caveat worth knowing: the docs describe the **response** only through SDK
accessors and never print a raw body. `ResponseParser` therefore treats field names
as likely rather than certain — it prefers the documented
`steps[] → content[] → data` shape, accepts known aliases, and falls back to a
whole-tree scan (warning on stderr when it has to). Interim "thought" images are
counted and reported but never saved as outputs. If a response ever parses oddly,
capture it with `--debug-dump-response` and the parser can be tightened against
reality.

`-n N` issues N separate API calls — the API has no batch image-count parameter.

## Tests

```bash
mvn test
```

52 tests covering sequence numbering, filename slugs and prefix stripping, config
precedence and validation, argument parsing, request-payload shape, and the tolerant
response parser (including malformed and unfamiliar response shapes). The API itself
is not mocked; use `--dry-run` for an end-to-end check that spends nothing.
