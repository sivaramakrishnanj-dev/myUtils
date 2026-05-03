# ADR 0004 — Use Jackson for InnerTube JSON parsing

- **Status:** Accepted
- **Date:** 2026-05-03
- **Deciders:** srk
- **Tags:** json, parsing, jackson, schema-stability

## Context

The `PlayerResponseExtractor` component (`02-architecture.md` § 1.2.1) is responsible for turning the raw JSON bytes returned by InnerTube into an immutable `PlayerResponse` domain object. It is the only place in the tool that parses JSON — we do not emit JSON ourselves in MVP (the `--print-json` feature is Future Work per `01-overview.md`).

The InnerTube response has three characteristics that shape the choice of JSON library:

1. **Deeply nested, with a small read path.** The response contains hundreds of fields across dozens of nesting levels — `streamingData`, `captions.playerCaptionsTracklistRenderer.captionTracks[]`, `videoDetails`, `playabilityStatus`, `microformat`, `annotations`, `storyboards`, and many more. We read perhaps 10–15 of these. The library must let us ignore unknown fields silently, or every YouTube response-shape tweak breaks us.
2. **Untrusted, in the reverse-engineering sense.** Fields appear, disappear, change types (occasionally a string becomes a number or vice-versa). Our parser must tolerate the small, predictable drift that this implies — while surfacing the kind of *breaking* shape changes that should trip a test and force a new response-fixture capture (Phase 3 formal specs with `x-captured-on` dates).
3. **Parsed once per run, never emitted back.** We don't round-trip. There is no write-side. This is pure deserialisation, which lets us prefer libraries optimised for that path over round-trip ones.

The three mature Java JSON options are:

- **Jackson** (`com.fasterxml.jackson.core:jackson-databind`)
- **Gson** (`com.google.code.gson:gson`)
- **JSON-P + JSON-B** (Jakarta EE spec) or a streaming equivalent

The decision is local to one component but affects fat-jar size, test ergonomics, and the Phase 3 formal-contract story (because Jackson has a mature JSON Schema generator; Gson does not).

## Decision

**We use Jackson (`jackson-databind` + `jackson-annotations` + `jackson-core`) for all JSON parsing, configured for permissive-by-default unknown-field handling and strict-by-choice required-field handling.**

Concretely:

- **Single `ObjectMapper` instance** owned by `PlayerResponseExtractor`, configured at construction time with:
  - `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false` — unknown fields are ignored. YouTube can add new fields without breaking us.
  - `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES = true` — catch genuinely broken responses early. A primitive field that is `null` is a shape-break, not drift.
  - `DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY = true` — some InnerTube array fields occasionally appear as single objects. Permissive handling matches yt-dlp's real-world observations.
  - No `JavaTimeModule`, no `AfterburnerModule`, no reflection optimisations — defaults only, minimum dependency graph.
- **Domain types as Java `record` classes** annotated with `@JsonIgnoreProperties(ignoreUnknown = true)` at the type level (redundant with the feature flag but explicit), `@JsonProperty("...")` for any field whose Java name diverges from the wire name (e.g., `videoDetails` → `VideoDetails videoDetails`).
- **Partial parsing tolerance.** We describe the InnerTube response shape as what we need, not as the full response. `06-formal/innertube-player-response.schema.json` (Phase 3) documents only the fields we consume, with `additionalProperties: true` at every nesting level.
- **Typed errors on required-field failure.** When Jackson cannot populate a required domain-type field (e.g., `videoDetails.videoId` is missing), `PlayerResponseExtractor` catches the `JsonMappingException` / `JsonProcessingException` and rethrows it as our `InnerTubeParseException`, which `ErrorMapper` maps to AC-5.2 exit code `11`.
- **No streaming parser (`JsonParser`) used directly.** The response is small enough (50–200 KB) that databind's in-memory tree build is fine. A streaming pass would save a fraction of a millisecond and cost significant code complexity.

Jackson adds ~2 MB to the fat jar (`jackson-databind` ~1.5 MB, `jackson-core` ~500 KB, `jackson-annotations` ~75 KB).

## Alternatives considered

### Alternative 1 — Gson

Pros:
- **Smaller fat-jar footprint** (~280 KB) than Jackson.
- **Very stable, very well known** in the Java / Android ecosystem.
- **Simpler API** for the one-shot deserialise case: `new Gson().fromJson(text, Type.class)`.
- Permissive by default (unknown fields ignored without configuration).

Cons:
- **No first-class `@JsonAlias`-equivalent.** If a wire field has been renamed across YouTube response versions (e.g., `videoDetails.title` → `videoDetails.videoTitle` in some future change), handling both in one domain type requires a custom `JsonDeserializer<T>` — more boilerplate than Jackson's one-line `@JsonAlias({"title","videoTitle"})`.
- **Weaker record-class support.** Gson support for Java `record` classes shipped later than Jackson's and required manual adapter registration for a while; recent versions (≥ 2.10) handle it, but edge cases around records with `@JsonProperty`-style renames remain awkward.
- **No mature JSON Schema generator** in the same ecosystem. Phase 3 `06-formal/*.schema.json` files can still be written by hand, but Jackson ships `jackson-module-jsonSchema` (or the community `victools/jsonschema-generator` that Jackson integrates with) which would generate those files from our domain types for free if we wanted.
- **Slower than Jackson** by 20–50% on typical payloads. Irrelevant for our tiny one-shot parse per run, but a fact.

Rejected on two grounds: the `@JsonAlias`-equivalent boilerplate is a non-trivial maintenance burden for a reverse-engineered wire format that drifts, and the Phase 3 Schema-generation story is meaningfully easier with Jackson. The 1.7 MB fat-jar saving is real but small relative to the ~5 MB baseline Jackson lands us on.

### Alternative 2 — JSON-P (`jakarta.json.*`) + JSON-B (`jakarta.json.bind.*`)

Pros:
- **Standardised** — Jakarta EE specs, less vendor lock-in in principle.
- **Streaming parser available** (`JsonParser`) for fine-grained control.

Cons:
- **Complex dependency story.** Need a JSON-P provider (`eclipse-ee4j/parsson`), a JSON-B provider (`eclipse-ee4j/yasson`), and a Jakarta API artifact. That's ~1 MB spread across three artifacts with coordinated versions — more assembly than Jackson's single-family dependency.
- **Less community momentum.** Jackson and Gson together cover essentially all Java JSON use cases; JSON-P/B is a distant third in actual usage. Finding community answers to subtle deserialisation problems is harder.
- **No compelling advantage for this use case.** We don't emit JSON; we don't need the spec's round-trip guarantees; we parse once per run.

Rejected. JSON-P/B is the right choice for Jakarta EE stacks that need spec compliance. For a small CLI tool, it trades away the ergonomic wins of either Jackson or Gson for nothing we need.

### Alternative 3 — Manual parsing (hand-rolled recursion over a generic `Map<String, Object>`)

Pros:
- **Zero JSON library dependency beyond whatever the HTTP client brings transitively.** (OkHttp doesn't include a JSON parser.)
- Full control.

Cons:
- **Every field read is a string lookup, cast, and null-check.** Type safety is lost. The tool's `PlayerResponseExtractor` becomes a 200-line method of `Object value = map.get("videoDetails"); if (!(value instanceof Map)) throw ...;` patterns. The exact thing domain types exist to prevent.
- **Testing against fixtures is harder** because the "unstructured map" intermediate form is harder to set up and assert against than typed records.
- **Maintenance nightmare** if the response shape changes — a missing typo in a string key won't fail at compile time.

Rejected without further discussion. This option exists in the list so it's on record that we looked at it.

## Consequences

**Positive:**

- **`record` classes work beautifully.** `PlayerResponse`, `Format`, `CaptionTrack`, `VideoDetails`, `PlayabilityStatus` are all immutable records with Jackson constructor-param discovery — zero setters, no mutable state, matches the library's immutability discipline (AC-11.1).
- **Unknown-field tolerance is the default.** YouTube adding fields doesn't break us. Only shape changes (field missing, type changed) do, which is exactly the signal a contract test should catch.
- **One library for Phase 3 too.** When `06-formal/innertube-player-response.schema.json` is written, Jackson can optionally generate skeletons from the domain types. Even if hand-authored, the `jackson-module-jsonSchema` integration makes round-trip validation tests trivial in Phase 5.
- **Fast enough.** `ObjectMapper` parse of a 200 KB response is 5–10 ms. Negligible next to the network round-trip and the ffmpeg mux.
- **Well-known idioms.** Any Java dev reading `PlayerResponseExtractor` in Phase 5 will recognise `@JsonProperty`, `@JsonAlias`, and `@JsonIgnoreProperties` immediately.

**Negative / accepted trade-offs:**

- **~2 MB fat-jar cost.** Largest single dependency after OkHttp. The record-class ergonomics, `@JsonAlias` for drift tolerance, and schema-gen integration are worth it.
- **`jackson-databind` has a history of CVEs** around polymorphic type handling — none of them affect us because we do not use polymorphic deserialisation (no `@JsonTypeInfo`, no `DefaultTyping`). Mitigated by keeping the dep version current via normal Maven `<dependencyManagement>` hygiene.
- **A future `--print-json` feature** (out of scope today, in Future Work) will want the same Jackson for serialisation. That's a positive when we get there; just noting the decision locks in Jackson for write-path too.
- **Custom deserialisers possible but not anticipated.** If YouTube introduces a field whose representation Jackson can't handle with annotations alone (has happened in other reverse-engineered APIs), we write a `@JsonDeserialize(using = MyDeserializer.class)` class. Small one-off cost when / if it happens.

**Neutral:**

- Jackson does not leak onto the public API of `yt-core`. Callers see only domain records; they never import a Jackson type. The library's embeddability (US-9) is preserved.
- Logging of parse failures goes through SLF4J like every other component — Jackson does not add its own logging dependency.

## References

- `00-requirements.md` § User stories — US-5 (fail fast), US-10 (structured logs), US-11 (offline-testable parsers)
- `00-requirements.md` § Acceptance criteria — AC-5.2 exit code 11 (InnerTube parse error), AC-9.1..AC-9.4 (library public API surface, typed exceptions), AC-11.1 (pure functions for parsers), AC-11.2 (unit tests against fixtures)
- `02-architecture.md` § 1.2.1 — `PlayerResponseExtractor` component references this ADR
- `06-formal/README.md` — conventions for response-shape schemas, including `additionalProperties: true` and the `x-captured-on` note referenced in this ADR's "Partial parsing tolerance" clause
- [FasterXML/jackson](https://github.com/FasterXML/jackson)
- [`@JsonIgnoreProperties(ignoreUnknown = true)` reference](https://github.com/FasterXML/jackson-annotations/wiki/Jackson-Annotations#deserialization)
- ADR 0001 (ANDROID InnerTube client) — determines the exact response shape this component parses
- ADR 0002 (OkHttp) — provides the `ResponseBody` that Jackson reads from
