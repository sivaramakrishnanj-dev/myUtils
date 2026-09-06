---
name: imagegen
description: Generate or edit raster images with Google's Gemini image models via the local imagegen CLI. Use whenever the user asks you to create, draw, render, generate, or produce an image, logo, icon, illustration, diagram, mockup or photo; or to edit, retouch, modify, restyle, upscale, change, remove something from, or add something to an existing image file. Also use for iterative refinement of an image you previously generated. Triggers on "generate an image", "make me a picture/logo/icon", "draw", "render", "edit this image", "remove the X from this photo", "change the background", "make it widescreen", "now add Y" following a prior generation.
---

# imagegen

Claude cannot render images. This skill shells out to a local Java CLI that calls
Google's Gemini image models and writes files to disk, then hands you absolute paths.

## Before anything else

Confirm the tool is set up. Run once per session if unsure:

```bash
imagegen config --show
```

`apiKeyPresent: true` means you're good. If it's `false`, stop and tell the user to
run `imagegen config --init` and paste their key from
https://aistudio.google.com/apikey — do not attempt to work around a missing key.

If the `imagegen` command isn't found, the jar needs building:
`cd <repo>/imageGeneration && mvn -q clean package`, then use
`java -jar <repo>/imageGeneration/target/imagegen-cli.jar` in place of `imagegen`.

## The two commands

Create a new image from a description:

```bash
imagegen generate -p "<description>" [-o <dir>] [-r 1K|2K|4K] [-a 16:9]
```

Change an existing image:

```bash
imagegen edit -p "<what to change>" -i <path> [-i <path2>] [-o <dir>]
```

Refine something you just produced, without re-uploading it:

```bash
imagegen edit -p "<further change>" --continue-from <previous output path>
```

## How to read the result

stdout is a single JSON document. stderr is progress logging — ignore it.

Take `outputs[].path` (absolute) and use it: report it to the user, Read it to look at
what you made, or feed it back in with `--continue-from`.

Branch on the exit code, not the message text:

| Exit | Meaning | Your move |
|---|---|---|
| 0 | Success | Report the path(s) |
| 2 | Bad flags | Fix the command and retry |
| 3 | Missing/rejected API key | Stop; tell the user, quote the `hint` |
| 4 | Rate limit / 5xx / timeout | Retry once after a pause, then report |
| 5 | Bad request or safety block | Reword the prompt once; if it fails again, tell the user what the model said |
| 6 | Local I/O problem | Check the path exists and is writable |

Every error JSON has a `hint` field naming the fix. Read it before improvising.

## Rules

1. **Never pass `--emit-base64`** unless the user explicitly asks for base64. A 4K
   image is tens of megabytes of context for nothing — the file on disk is the
   deliverable.
2. **Use `--dry-run` first** when the user's intent is ambiguous, when you're about to
   generate several images, or when you want to show them where files will land. It
   spends nothing.
3. **Don't invent flags.** Run `imagegen help --agent` for the complete contract if
   you need something not listed here.
4. **Write prompts properly.** These models respond to descriptive scene-setting, not
   keyword soup. Say "a weathered brass compass on a nautical chart, warm afternoon
   light from the left, shallow depth of field" — not "compass, brass, chart, nice".
   For edits, name exactly what changes and state that the rest stays put: "replace
   the cloudy sky with a clear sunset; leave the buildings and foreground unchanged".
5. **Use `--prompt-file`** for prompts with quotes, newlines or shell metacharacters
   rather than fighting escaping.
6. **Let the defaults work.** `gemini-3.1-flash-image` at `1K` is right for most
   requests. Reach for more only with reason:
   - `-r 2K`/`-r 4K` — the user wants print or large-display output.
   - `-m gemini-3-pro-image` — hard compositions, brand consistency, style references.
   - `--thinking high` — dense or technical images (diagrams, infographics, text-heavy
     layouts) where the first attempt came out wrong.
   - `-a 16:9`, `-a 9:16`, `-a 1:1` — the user named a shape or a use (banner, phone
     wallpaper, avatar).
7. **Resolution strings are case-sensitive.** `1K`, not `1k`.
8. **Iterate with `--continue-from`, not by re-sending the file.** It chains through
   the API's own interaction history, so the model keeps full context of the image it
   already made.
9. **Tell the user about the watermark** if they ask about provenance or plan to
   publish: every output carries Google's invisible SynthID watermark.

## Where files land

`out_<seq>_<base>.<ext>` — `<seq>` auto-increments per directory.

- `edit` writes beside the input: `~/pics/photo.jpg` → `~/pics/out_001_photo.png`
- `generate` writes to the current directory using a slug of the prompt
- `-o <dir>` overrides both; the directory is created if missing
- The extension follows the output MIME type, not the input
- A sidecar `out_<seq>_<base>.json` records the prompt, settings and interaction id

If the user wants the image somewhere specific, pass `-o` rather than generating and
moving it.

## Worked sequences

New asset, then refine:

```bash
imagegen generate -p "a minimal line-art icon of a paper plane, single weight stroke, \
centred, generous margin" -a 1:1 -o ./assets
# → ./assets/out_001_a-minimal-line-art-icon-of-a-paper.png
imagegen edit -p "make the stroke noticeably thicker" \
  --continue-from ./assets/out_001_a-minimal-line-art-icon-of-a-paper.png
# → ./assets/out_002_a-minimal-line-art-icon-of-a-paper.png
```

Retouch a user's photo in place:

```bash
imagegen edit -p "remove the parked silver car on the right; extend the pavement and \
kerb naturally behind it; leave everything else untouched" -i /Users/me/pics/street.jpg
```

Combine references:

```bash
imagegen edit -p "place the chair from the second image into the empty corner of the \
room in the first image, matching the room's lighting" -i room.png -i chair.jpg
```

Hard technical image:

```bash
imagegen generate -p "exploded isometric diagram of a bicycle rear hub, parts \
separated along the axle, thin callout lines with labels, white background" \
  -r 2K -m gemini-3-pro-image --thinking high
```

## Reporting back

Give the user the path and what you made, not the JSON. If you generated several,
list them. If the model returned text alongside the image (the `text` field), pass
that along — it often explains a choice it made or a limit it hit.
