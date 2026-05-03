# ADR 0003 — Shell out to `ffmpeg` via `ProcessBuilder` for muxing and audio transcoding

- **Status:** Accepted
- **Date:** 2026-05-03
- **Deciders:** srk
- **Tags:** ffmpeg, subprocess, muxing, audio-transcode

## Context

Two of the tool's operations require container-level and codec-level manipulation of media files:

1. **Mux video + audio → MP4** (Flow A in `02-architecture.md` § 2.1). YouTube's ANDROID client typically returns video and audio as separate streams (video-only MP4 or WebM, audio-only M4A or WebM/Opus). To produce a single playable MP4 the user expects, we need to mux these two streams into one container. This is a stream-copy operation (no re-encoding) per AC-1.6: `-c copy -map 0:v:0 -map 1:a:0`.
2. **Audio transcode — stream → MP3** (AC-2.4). When the user passes `--audio-format mp3`, we transcode the selected audio stream (typically M4A/AAC) to MP3 at `NFR-DEFAULT-MP3-BITRATE = 192 kbps`. This is a re-encode: `-c:a libmp3lame -b:a 192k`.

Both operations are CPU- and codec-intensive. They have three known implementation paths in Java:

- **Shell out to a local `ffmpeg` binary** via `ProcessBuilder`. `ffmpeg` is the reference implementation of both operations — it's what yt-dlp, NewPipe, OBS, VLC, and essentially every media tool uses under the hood.
- **JNI bindings to libavcodec / libavformat** (JavaCV, JCodec, Humble Video, etc.). These wrap the same C libraries `ffmpeg` is built from, exposed as a Java API.
- **Pure-Java media libraries** (JCodec has pure-Java decoders for some codecs; JAVE for audio transcoding).

All three can technically do the work. The choice affects build size, runtime dependencies, licence complexity, and the size of the "things that can go wrong" surface.

AC-13.1..AC-13.5 already require a version probe at start-up (`ffmpeg -version`), a version floor (`NFR-MIN-FFMPEG-VERSION = 4.0`), stderr surfacing on failure (`NFR-FFMPEG-STDERR-LINES = 20`), a per-invocation timeout (`NFR-FFMPEG-INVOCATION-TIMEOUT = 600 s`), and — critically — a "skip the probe when not needed" path for transcript-only and audio-only-m4a operations (AC-13.5). This ADR captures the reasoning for picking shell-out as the implementation strategy that satisfies all of them.

## Decision

**We shell out to a local `ffmpeg` binary via `java.lang.ProcessBuilder` for every muxing and transcoding operation.** The binary is located by looking up `ffmpeg` on the process's `PATH` (or `--ffmpeg-location` when the user overrides). The `FfmpegMuxer` component in `02-architecture.md` § 1.2.3 owns this integration.

Concretely:

- **Version probe** at start-up (only when mux or transcode is needed per AC-13.5) runs `ffmpeg -version`, parses the first line's `ffmpeg version X.Y.Z` prefix via regex, compares to `NFR-MIN-FFMPEG-VERSION = 4.0`, fails with AC-5.2 exit `60` if missing or too old.
- **Mux invocation** builds the command line exactly as `ffmpeg -i <video.part> -i <audio.part> -c copy -map 0:v:0 -map 1:a:0 -y <out.mp4>` with `-loglevel error` at standard verbosity and `-loglevel info` under `--debug` per `NFR-FFMPEG-LOGLEVEL`.
- **Transcode invocation** builds `ffmpeg -i <audio.part> -c:a libmp3lame -b:a 192k -y <out.mp3>` with the same loglevel rules.
- **stderr capture** uses a bounded ring buffer of the last `NFR-FFMPEG-STDERR-LINES = 20` lines; on non-zero exit, those lines are included in the `FfmpegException` message per AC-13.4.
- **Timeout enforcement** uses `Process.waitFor(NFR-FFMPEG-INVOCATION-TIMEOUT, TimeUnit.SECONDS)`; on timeout, sends SIGTERM, waits 5 s, sends SIGKILL per the shutdown model in `02-architecture.md` § 5.
- **Shutdown hook** cooperates with the orchestrator's shutdown hook (`02-architecture.md` § 5.1) — on SIGINT to the JVM, the child `ffmpeg` is SIGTERMed before the JVM exits.

ffmpeg is not bundled with the fat jar. The user is responsible for having it on `PATH` (macOS: `brew install ffmpeg`; Linux: `apt/dnf/pacman install ffmpeg`). The tool fails gracefully if it is missing (AC-13.2).

## Alternatives considered

### Alternative 1 — JNI-bound libav (JavaCV, JCodec-FFmpeg, Humble Video)

Pros:
- **No external binary dependency.** Everything ships in the fat jar.
- **Programmatic error handling.** Failures come back as typed exceptions, not "parse ffmpeg stderr lines" heuristics.
- **Faster small-invocation path.** No fork-exec overhead per operation.

Cons:
- **Massive fat-jar size.** JavaCV's FFmpeg preset is ~200 MB **per platform** because it bundles the native `libav*.so` / `.dylib` / `.dll` for each supported architecture. A truly cross-platform jar is 500 MB+. For a tool whose main job is one HTTP call plus two HTTP body reads plus one mux, this is absurd.
- **Native binaries mean architecture-specific artifacts.** We'd need `-macos-aarch64`, `-macos-x86_64`, `-linux-x86_64`, `-linux-aarch64` jars, or a loader that picks at runtime. Complicates `NFR-SUPPORTED-OS` support and the Maven build.
- **License complexity.** libav is LGPL, but some ffmpeg-preset bundles include x264 (GPL), AAC encoders (licence-complicated), and more. Every upstream version bump risks a licence-review friction we don't want for a learning project.
- **JNI failures are brutal.** A bug in the binding can crash the JVM outright (SIGSEGV), not throw an exception. For a CLI tool this is a worst-in-class failure mode.
- **Slower to develop against.** JavaCV's API is a thin wrapper over FFmpeg's C API, which is notoriously documentation-light. Shell-out against the ffmpeg CLI is what every Stack Overflow answer shows.

Rejected. JNI-bound libav is the right choice for tools that need fine-grained frame-level processing in a JVM server (media-processing pipelines, live transcoding services). It is dramatically over-budgeted for an MVP that runs two simple, well-documented ffmpeg commands per invocation.

### Alternative 2 — Pure-Java media libraries (JCodec, JAVE)

Pros:
- **No external binary.**
- **No native dependency.** Works on any JVM-supported platform.

Cons:
- **Codec coverage gaps.** JCodec supports H.264 decoding but not encoding of many codecs relevant to our inputs. JAVE (Java Audio Video Encoder) wraps a bundled ffmpeg — it's not pure-Java; it's an ffmpeg bundler with a Java API. If we used JAVE we'd essentially be using ffmpeg anyway, just with more indirection.
- **Performance is an order of magnitude worse than native ffmpeg.** 600 s timeout (`NFR-FFMPEG-INVOCATION-TIMEOUT`) would be regularly exceeded on slower machines doing a full 1080p transcode.
- **Pure-Java MP3 encoding is unreliable.** Fewer well-maintained options than one would hope.

Rejected. JCodec in particular is impressive engineering but not a production path for our specific codec needs. JAVE's use of a bundled ffmpeg just moves the "where does ffmpeg live" decision — the bundled ffmpeg is still an external binary, just hidden inside the jar.

### Alternative 3 — Ship a bundled `ffmpeg` binary with the fat jar

Pros:
- **Zero user setup.** User doesn't need to `brew install ffmpeg`.
- **Known ffmpeg version.** No version-drift surprises.

Cons:
- **Dramatically increases the fat jar.** An `ffmpeg-static` build is ~70–100 MB per OS/arch.
- **Cross-platform binary shipping is painful.** Now we need a jar-with-classifier per `{macos-aarch64, macos-x86_64, linux-x86_64, linux-aarch64}` at minimum. Violates the "small, simple fat jar" spirit.
- **Licence redistribution issues.** ffmpeg's licence depends on which options it was built with. Redistribution with GPL-covered codecs triggers source-code availability obligations; with LGPL-only it's less onerous but still requires care. Not worth the MVP complexity.
- **Security burden.** When ffmpeg releases a CVE fix, we need to rebuild and release. The user has `brew upgrade ffmpeg` as an alternative that we can't compete with.

Rejected. This is the right path for consumer-grade applications (think OBS, desktop recorders) where user-setup friction is the binding constraint. It is the wrong path for a CLI tool where `brew install ffmpeg` is expected friction and `NFR-SUPPORTED-OS = macOS + Linux only` already assumes users can install a package.

### Alternative 4 — Skip muxing entirely; emit separate `.mp4` and `.m4a` files

Pros:
- **Zero ffmpeg dependency.** The tool becomes pure-network + file-write.
- **Simpler flow.** Remove an entire component (`FfmpegMuxer`) and all its error-handling.

Cons:
- **Fails US-1.** The primary user story is "playable local MP4" — separate files require the user to remux themselves, defeating the whole point.
- **Audio transcode goes away too.** US-2 becomes m4a-only; users who want MP3 are out of luck. This is an even bigger user-value hit.

Rejected. We considered it because it would let the MVP avoid ffmpeg entirely (AC-13.5's carve-out for transcript-only runs hints at how nice that is), but the primary user value proposition for US-1 requires muxing. Transcript-only runs already don't invoke ffmpeg (AC-13.5); video + audio runs fundamentally do.

## Consequences

**Positive:**

- **Tiny fat jar.** No libav in the dependency graph. The tool's jar stays at single-digit MB, which is a meaningful differentiator for a single-invocation CLI tool.
- **Reliable codec coverage.** Whatever ffmpeg can mux, we can mux. Whatever ffmpeg can transcode, we can transcode. We inherit ffmpeg's enormous and battle-tested codec matrix at zero maintenance cost to us.
- **AC-13.5 is naturally satisfied.** The skip-probe-when-not-needed path is trivial — we only probe when `FfmpegMuxer` is about to be used.
- **Troubleshooting is easier than it sounds.** When something goes wrong, the last 20 lines of ffmpeg's stderr are more useful to a user than any abstraction we could build on top. Users who know ffmpeg (many do) can debug from the error message directly.
- **Upgrades are the user's problem.** When a user upgrades ffmpeg via `brew upgrade`, the tool benefits immediately. No coordinated release needed from us.
- **Offline-testable by injection.** `FfmpegMuxer` holds a `Path ffmpegBinary` field that tests can override to point at a stub script, enabling offline testing of the command-line construction and stderr-parsing paths per AC-11.1..AC-11.3.

**Negative / accepted trade-offs:**

- **User must install ffmpeg separately.** `NFR-MIN-FFMPEG-VERSION = 4.0` is documented in the README and in the AC-13.2 error message. The user experience of the very first run on a fresh machine includes one `brew install ffmpeg` step; we judge this acceptable.
- **Fork-exec overhead.** Each ffmpeg invocation spawns a process — ~10–50 ms latency overhead on macOS / Linux. Negligible relative to the actual mux / transcode work.
- **stderr parsing is not structured.** "ffmpeg failed because X" is inferred from human-readable log lines. We surface the last 20 lines verbatim rather than trying to classify the failure further, trusting the user to read them (AC-13.4). This is honest, not fancy.
- **No progress inside mux.** ffmpeg can emit progress to stderr (`-progress pipe:2`); for MVP we don't parse it. Progress reporting during mux is a "simulated" fixed phase (`ProgressReporter` shows "muxing…" as a discrete step, not bytes-per-second). Good enough for MVP; can be improved later.
- **Platform variations in `PATH` resolution.** `ProcessBuilder` uses the JVM's `PATH`, which on macOS under certain launch conditions (e.g., double-click from Finder) can be minimal. MVP targets CLI-shell invocation where `PATH` is the user's shell `PATH`, so this is not an issue in practice. The `--ffmpeg-location` flag is the escape hatch if it ever is.

**Neutral:**

- We rely on ffmpeg's command-line contract staying stable. It has, for well over a decade, for the flags we use. Unlikely to break.
- The bundled vs shell-out decision is fully reversible if we ever reverse it. `FfmpegMuxer` is a one-component boundary.

## References

- `00-requirements.md` § User stories — US-1 (download muxed video), US-2 (audio-only with mp3 option), US-13 (tolerate ffmpeg missing or too old)
- `00-requirements.md` § Acceptance criteria — AC-1.6 (mux via ffmpeg), AC-2.4 (mp3 transcode), AC-13.1..AC-13.5 (ffmpeg availability, version check, stderr surfacing, skip-when-not-needed)
- `00-requirements.md` § Non-functional requirements — NFR-MIN-FFMPEG-VERSION, NFR-FFMPEG-STDERR-LINES, NFR-FFMPEG-INVOCATION-TIMEOUT, NFR-FFMPEG-LOGLEVEL, NFR-DEFAULT-MP3-BITRATE
- `02-architecture.md` § 1.2.3 — `FfmpegMuxer` component references this ADR
- `02-architecture.md` § 5 — shutdown cooperation with the ffmpeg child process
- [ffmpeg documentation — `-c copy`](https://ffmpeg.org/ffmpeg.html#Stream-copy) for the mux command form
- [ffmpeg documentation — libmp3lame](https://trac.ffmpeg.org/wiki/Encode/MP3) for the MP3 transcode command form
- ADR 0001 (ANDROID InnerTube client) — the reason we receive separate video and audio streams that need muxing
