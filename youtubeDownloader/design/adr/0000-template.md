# ADR NNNN — <Short title, imperative voice>

- **Status:** Proposed | Accepted | Superseded by [ADR-XXXX](./XXXX-*.md) | Deprecated
- **Date:** YYYY-MM-DD
- **Deciders:** <names / team aliases>
- **Tags:** <comma-separated, e.g. http, parsing, ffmpeg>

## Context

What is the issue motivating this decision? State the forces at play — technical, product, operational. Include anything a future reader would need to understand *why this was even a question*. Two or three paragraphs maximum. Do not describe the solution here.

## Decision

State the decision in one or two sentences, in active voice. Example: "We will use the ANDROID InnerTube client as the primary stream-metadata source, with a fallback to WEB only when required."

Then, in a few paragraphs, describe the decision precisely enough that a reader can tell whether a given piece of code is faithful to it.

## Alternatives considered

List at least two alternatives, including "do nothing" when relevant. For each, give one paragraph: what it was, why it was rejected.

### Alternative 1 — <name>

One paragraph on what it is and why it was rejected.

### Alternative 2 — <name>

One paragraph on what it is and why it was rejected.

## Consequences

State the consequences of the decision honestly — both positive and negative. A future reader should be able to see what was accepted as trade-off, not just what was gained.

**Positive:**
- ...

**Negative / accepted trade-offs:**
- ...

**Neutral (things that follow from this decision):**
- ...

## References

- Links to prior art, benchmarks, internal docs, external articles, related ADRs, user stories, ACs, NFRs.

---

## How to use this template

1. Copy this file to `NNNN-short-kebab-title.md` where `NNNN` is the next free number.
2. Fill in every section. Do not delete sections — if one doesn't apply, say so explicitly.
3. Submit in the same CR as the code or design change it governs. ADRs without accompanying changes are almost always premature.
4. Once merged, an ADR is immutable except for status transitions and typo fixes. To change a decision, write a new ADR that supersedes it and update this ADR's status.
