package com.imagegen;

/** Usage text. {@link #agentContract()} is the single-call orientation dump for an agent. */
public final class Help {

    private Help() {
    }

    public static String usage() {
        return """
            imagegen - generate and edit images with Google's Gemini image models.

            USAGE
              imagegen generate --prompt "<text>" [options]
              imagegen edit     --prompt "<text>" --image <path> [--image <path> ...] [options]
              imagegen models
              imagegen config [--init | --show]
              imagegen help [--agent]

            WHAT IT DOES
              generate  Makes a new image from a text prompt. Output lands in the current
                        directory (or --out-dir) as out_<seq>_<prompt-slug>.<ext>.
              edit      Sends one or more input images plus a prompt. Output lands beside
                        the first input (or --out-dir) as out_<seq>_<inputName>.<ext>.

            KEY OPTIONS
              -p, --prompt <text>        The instruction. Required for generate and edit.
                  --prompt-file <path>   Read the prompt from a file instead (avoids shell quoting).
              -i, --image <path>         Input image; repeat for multiple. Required for edit.
              -o, --out-dir <dir>        Where to write. Created if missing.
              -m, --model <id>           Default: %s
              -r, --resolution <size>    512px | 1K | 2K | 4K. Default: %s. Uppercase K required.
              -a, --aspect-ratio <w:h>   1:1 3:2 2:3 3:4 4:3 4:5 5:4 9:16 16:9 21:9. Default: model's choice.
                  --mime <type>          image/jpeg (default) or image/png. Models differ on what
                                         they accept; if unset, a rejection is auto-corrected once
                                         using the type the API says it supports.
                  --thinking <level>     minimal (default) or high. Higher = slower, better on hard prompts.
              -n, --count <int>          Make N images (N separate API calls). Default 1, max %d.
                  --continue-from <path> Refine a previous output; inherits its settings and
                                         chains via previous_interaction_id.

            OUTPUT AND SAFETY
                  --dry-run              Validate everything and show the paths that would be
                                         written. Makes no API call, spends nothing.
                  --format json|text     Default json. stdout is always the result document;
                                         all logs go to stderr.
                  --emit-base64          Also include base64 image bytes in the result JSON.
                                         Off by default - a 4K image is tens of megabytes.
                  --quiet                Suppress progress logging on stderr. Errors still print.
                  --debug-dump-response <path>  Save the raw API response for troubleshooting.

            CONFIG AND AUTH
                  --config <path>        Config file. Default: ~/.config/imagegen/config.json
                  --api-key <key>        Override the key for this call.
                  --timeout <seconds>    Default %d.
                  --retries <n>          Automatic backoff on 429/5xx. Default %d.

              API key precedence: --api-key > $IMAGEGEN_API_KEY > $GEMINI_API_KEY
                                  > $GOOGLE_API_KEY > config file.

            EXAMPLES
              imagegen generate -p "a red bicycle leaning on a blue wall" -r 2K -a 16:9
              imagegen edit -p "make the sky stormy" -i photo.jpg
              imagegen edit -p "now add rain" --continue-from out_001_photo.png
              imagegen generate -p "a logo for a coffee shop" -o ./assets --dry-run

            EXIT CODES
              0 ok   2 usage   3 config/auth   4 retryable API   5 permanent API   6 I/O

            Run 'imagegen help --agent' for the machine-readable contract.
            """.formatted(Config.DEFAULT_MODEL, Config.DEFAULT_RESOLUTION, Config.MAX_COUNT,
                Config.DEFAULT_TIMEOUT_SECONDS, Config.DEFAULT_RETRIES);
    }

    /** Compact, complete orientation for an automated caller. */
    public static String agentContract() {
        return """
            # imagegen - agent contract

            Purpose: give an agent the ability to create and edit raster images via Google's
            Gemini image models. Fills the gap where the agent itself cannot render images.

            ## Invocation
              imagegen <generate|edit|models|config|help> [flags]

            ## Contract
            - For generate, edit and config, stdout is ALWAYS a single JSON document
              (success or failure). Parse it. 'help' and 'models' print documentation text.
            - stderr is human logging only. Never parse it.
            - Exit code carries the class of outcome; branch on it:
                0 OK              result JSON has status:"ok"
                2 USAGE           bad flags - fix the command
                3 CONFIG          missing/invalid API key or config - see hint
                4 API_RETRYABLE   429/5xx/timeout - retry later
                5 API_PERMANENT   bad request or safety block - change prompt/inputs
                6 IO              local read/write problem
            - Every error JSON has: status, code, message, hint. The hint names the next action.
            - Images are written to disk; the result gives ABSOLUTE paths. Read those paths.
              base64 is NOT returned unless --emit-base64 is passed (payloads are huge).

            ## Success result shape
            {
              "status": "ok",
              "command": "generate" | "edit",
              "model": "<model id>",
              "resolution": "1K",
              "aspectRatio": "16:9",          // omitted if unset
              "mimeType": "image/jpeg",       // the type actually accepted
              "mimeTypeRequested": "image/png",   // both only present if auto-corrected
              "mimeTypeAutoCorrected": true,
              "prompt": "<prompt used>",
              "sourceImages": ["/abs/in.jpg"], // edit only
              "outputs": [
                {"path": "/abs/out_001_in.png", "bytes": 1840233, "seq": 1,
                 "sidecar": "/abs/out_001_in.json", "base64": "<only with --emit-base64>"}
              ],
              "interactionId": "<id>",         // pass to --continue-from chaining
              "text": "<any text the model returned>",
              "usage": { ... },                // token counts, passed through verbatim
              "thoughtImages": 2,              // interim images, not saved, not billed
              "latencyMs": 7412,
              "dryRun": false
            }

            ## Error result shape
            {"status":"error","code":"API_RATE_LIMITED","message":"...","hint":"...","exitCode":4}

            ## Output naming
            - edit:     out_<seq>_<inputFileNameWithoutExt>.<outExt>, beside the first input.
            - generate: out_<seq>_<prompt-slug>.<outExt>, in the current directory.
            - <seq> is the highest existing out_<n>_* in that directory, plus one, zero-padded to 3.
            - The extension follows the output type, not the input: editing photo.png with
              the default JPEG output gives out_001_photo.jpg.
            - Each image gets a sidecar out_<seq>_<base>.json holding the prompt, model,
              settings and interactionId.

            ## Iterative refinement
            Chain edits without re-uploading: run edit once with --image, then for each
            follow-up pass --continue-from <the previous output path>. Settings and the
            interaction id are inherited from that output's sidecar; any flag overrides them.

            ## Before spending
            Pass --dry-run to validate flags, config and key presence and to see the exact
            output paths. No API call is made.

            ## Recipes
              # new image, widescreen, 2K
              imagegen generate -p "a lighthouse at dusk" -r 2K -a 16:9
              # edit in place next to the source
              imagegen edit -p "remove the parked car" -i /abs/street.png
              # refine the result of the previous call
              imagegen edit -p "make it warmer" --continue-from /abs/out_001_street.png
              # long prompt without shell quoting pain
              imagegen generate --prompt-file ./prompt.txt -r 4K
              # harder prompt, more deliberation
              imagegen generate -p "an exploded isometric diagram of a bicycle hub" --thinking high

            ## Notes
            - Default model %s; default resolution %s; default output image/jpeg.
            - Output MIME support varies by model (gemini-3.1-flash-image accepts only
              image/jpeg). Leave --mime unset and a rejection is corrected automatically;
              set it explicitly and a rejection is reported instead.
            - -n N issues N separate API calls (the API has no batch image-count parameter).
            - 512px is only supported on the flash image models; flash-lite supports 1K only.
            - All generated images carry Google's SynthID watermark.
            - Run 'imagegen models' for per-model capability limits.
            """.formatted(Config.DEFAULT_MODEL, Config.DEFAULT_RESOLUTION);
    }

    public static String models() {
        return """
            Model ids for --model (defaults to %s).

              gemini-3.1-flash-image        DEFAULT. Generalist. Resolutions 512px/1K/2K/4K.
                                            Reference images: up to 10 object + 4 character, no style.
                                            Supports thinking_level minimal|high.

              gemini-3.1-flash-lite-image   Fastest and cheapest. 1K only.
                                            Reference images: up to 14 object, no character/style.
                                            Weaker at sequential edits and multi-reference work.

              gemini-3-pro-image            Highest quality, best world knowledge and brand
                                            consistency. Reference images: 6 object + 5 character
                                            + 3 style. Use for the hardest visual tasks.

              gemini-2.5-flash-image        Legacy. Prefer gemini-3.1-flash-lite-image.

            Reference-image totals cap at 14 across all kinds.
            Every output carries a SynthID watermark.
            """.formatted(Config.DEFAULT_MODEL);
    }
}
