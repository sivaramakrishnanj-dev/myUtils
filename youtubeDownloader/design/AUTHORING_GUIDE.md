# Design Authoring Guide

This is the 1-page reference for writing and maintaining design docs in this package. Read it once before you add or edit anything in `design/`.

## The non-negotiables

1. **`design/` lives at the package root** — same repo as the code, reviewed in the same CR.
2. **All design is Markdown + Mermaid + JSON Schema.** No PNGs, no drawio, no Lucidchart exports. Diagrams must be text so they diff, review, and feed into LLM agents natively. Schemas must be executable (JSON Schema, not prose).
3. **A CR that changes behaviour updates the design in the same CR.** If you have nothing to update, say so in the CR description ("No design impact — <why>"). Silence counts as drift.
4. **Decisions go into ADRs.** If you have a conversation longer than ten minutes about *why* something is built a certain way, that conversation belongs in an ADR.
5. **Every file has a front-matter header** with `last_reviewed` date. Stale dates are a CR-time smell.
6. **Spec-driven phase discipline.** Each phase (Requirements → Design → Formal → Tasks → Code) is approved before the next starts. See `reviews/` for approval artifacts.
7. **Schemas are the source of truth for data contracts.** Prose in `04-apis.md` describes schemas; schemas in `06-formal/` are canonical. When they disagree, the schema wins and the prose is fixed.

## Folder structure

```
design/
  README.md                   # index + navigation + context diagram
  AUTHORING_GUIDE.md          # this file
  00-requirements.md          # user stories, EARS acceptance criteria, NFRs
  01-overview.md              # purpose, scope, non-goals, actors, system context
  02-architecture.md          # components, sequence, algorithm flows, failure handling
  03-data-model.md            # entities, ER diagram, lifecycle state machines
  04-apis.md                  # contracts, request/response, emitted events
  05-operations.md            # build, run, package, troubleshoot, update
  06-formal/                  # formal, machine-checkable specs
    README.md
    *.schema.json             # JSON Schema for every message / record shape
    state-machine.md          # state diagram + invariants + transition rules
    contract-tests.md         # index of contract tests derived from schemas
  07-tasks.md                 # implementation task breakdown
  adr/
    0000-template.md          # copy this for new ADRs
    NNNN-short-kebab-title.md
  reviews/
    README.md
    TEMPLATE.md
    YYYY-MM-DD-<slug>-r<N>.md
```

Numbered prefixes enforce reading order. Do not invent new top-level docs without a conversation.

## Mermaid diagram vocabulary

| Use it for | Type | Notes |
|---|---|---|
| System context, component views | `flowchart` | Most portable. |
| Request flows, async interactions, sequences | `sequenceDiagram` | |
| Data model, entity relationships | `erDiagram` | |
| Lifecycles, state machines | `stateDiagram-v2` | |
| Class/type relationships (sparingly — code is usually truer) | `classDiagram` | |

> **Avoid `C4Context`, `C4Container`, etc.** They need Mermaid v10+. Use `flowchart` with styling classes for system-context views.

## Requirements discipline (Phase 1)

- User stories use the classic shape: **"As a `<persona>`, I want `<capability>`, so that `<outcome>`."**
- Acceptance criteria use **EARS** (Easy Approach to Requirements Syntax):
  - *Ubiquitous:* `The system SHALL <do X>.`
  - *Event-driven:* `WHEN <trigger> THEN the system SHALL <response>.`
  - *State-driven:* `WHILE <state> the system SHALL <behaviour>.`
  - *Unwanted:* `IF <condition> THEN the system SHALL <mitigation>.`
  - *Optional:* `WHERE <feature is present> the system SHALL <behaviour>.`
- NFRs are **numeric and testable** whenever possible. "Fast" is not a requirement. "p99 end-to-end download latency ≤ `X` for a 10-minute 1080p video on a 100 Mbps link" is.
- AC IDs are stable: `AC-<story>.<N>`. NFRs have stable IDs `NFR-<NAME>`. Tasks and tests reference them by ID.

## Formal spec discipline (Phase 3)

- Every data shape that crosses a process boundary has a JSON Schema in `06-formal/`. That includes:
  - InnerTube player request and response bodies
  - YouTube caption track response shape
  - This tool's output-metadata JSON file
  - This tool's CLI exit-code contract
- Every JSON Schema has at least one positive and one negative example in `contract-tests.md`.
- The download-lifecycle state machine is defined as a diagram plus a bullet list of invariants that must hold in every state.

## ADR discipline

- **When to write one:** any decision whose reversal would be expensive, whose rationale isn't self-evident from code, or that has live alternatives worth recording.
- **Template:** `adr/0000-template.md`. Copy it. Fill every section. Don't delete sections — say "N/A" explicitly.
- **Numbering:** strictly sequential, zero-padded to 4 digits. No gaps, no reuse.
- **Immutability:** once merged, an ADR is frozen except for status transitions and typo fixes. To change a decision, write a new ADR that supersedes it.

## CR checklist

- [ ] `design/` updated, or CR description explicitly notes "No design impact — <why>"
- [ ] New architectural decision captured as an ADR
- [ ] Mermaid diagrams render (check in the CR UI — broken diagrams block)
- [ ] Any new or changed wire-message / record shape has a corresponding JSON Schema update in `06-formal/`
- [ ] `last_reviewed` date bumped on any file materially changed

## Red flags reviewers should call out

- A CR touches `src/` and claims no design impact but adds a new external dependency, a new CLI flag, a new InnerTube client variant, or a new output-file format. These always have design impact.
- A diagram says one thing, the code does another. The diagram wins — either the code is wrong or the diagram is stale. Either way, fix it in this CR.
- A schema in `06-formal/` disagrees with the prose in `04-apis.md`. The schema wins; fix the prose.
- An ADR has no "Alternatives considered" with at least two genuine options. A decision with no alternatives is not a decision, it's a retcon.
- `last_reviewed` date older than six months on a doc in an actively changing package.
- A requirements AC depends on a specific tool, library, or component name. ACs describe behaviour; component names belong in `02-architecture.md`.

## Working with AI agents

Design docs in this format are agent-friendly by construction:

- Mermaid is text, so agents read and modify diagrams directly without image recognition.
- JSON Schemas are executable — agents can validate messages and generate contract tests directly.
- Front-matter headers let agents key off `package`, `owners`, `status` without parsing prose.
- ADRs give agents the *why*, not just the *what* — they make code changes that respect original intent instead of just pattern-matching on existing code.

When you ask an agent to modify the package, point it at `design/README.md` first. Everything else navigates from there.

## Keeping the doc alive

- Quarterly: owners do a "design walk" — open every file, verify `last_reviewed` is fresh, correct any drift.
- After any incident (e.g., YouTube API change breaks the tool): add a paragraph to `05-operations.md#known-limits` or write an ADR if the incident revealed a decision worth recording.
- On every CR: the author is the first line of defence against drift. Reviewers are the second. The doc is the third — if the doc is easy to update, it will get updated.

## Project-specific notes

This project reverse-engineers a third-party API (YouTube's InnerTube). Two rules follow:

1. **Every wire-format assumption is captured as a JSON Schema with a pinned date.** When YouTube changes a response shape, the contract test fails first, the schema is updated, and the code follows — not the other way around.
2. **Every request we send must be traceable to an observed browser / Android app request.** When in doubt, add a note to the relevant ADR citing where the request shape came from (field capture date, app version if from Android).
