---
doc: reviews-index
last_reviewed: 2026-05-02
---

# Design Reviews

This folder captures **design review sessions** against the docs in `design/`. One file per review round. This is NOT for line-level CR comments — those still belong on the CR itself for fast inline feedback. Review files here capture the substantive discussion that would otherwise die when the CR merges.

## Spec-driven phase gates

Each phase (Requirements → Design → Formal → Tasks → Code) requires a `resolved` review file here before moving to the next phase. This is the approval artifact.

## Why this exists

- CR comments disappear from the design doc's orbit the moment the CR merges. Three months later, nobody knows why a decision reads the way it does.
- Agents can traverse Markdown in the repo; they cannot traverse CR comment threads.
- Deferred items need a visible home with a re-evaluation date, or they silently vanish.
- The review trail becomes part of the design's history — you can read "the design, and how the team pushed on it."

## Rules

1. **One file per review round**, not per comment. Named `YYYY-MM-DD-<short-slug>-r<N>.md`.
2. **`reviewed_commit` and `phase` are mandatory.** No review without a pinned commit SHA. Reviews of "the current state" rot instantly.
3. Comments are **numbered** (`C1`, `C2`, ...). Numbering is stable across the life of the file — authors reference `C3` when responding.
4. Every comment has exactly one status: `open`, `addressed`, or `deferred`. Nothing else.
5. `addressed` requires `addressed_in_commit`. No exceptions.
6. `deferred` requires a `deferred_reason` AND a `re-evaluate_by` date. A deferral without a re-evaluation date is a silent drop — which is the exact failure mode this folder exists to prevent.
7. Reviews are **immutable once resolved**, except for typo fixes. New issues on the same artifact = a new round (`-r2`, `-r3`, ...).
8. The CR that addresses review comments **updates the review file in the same CR**. Same rule as design drift.

## Where reviews happen (so we don't double-work)

| CR touches | Substantive design discussion goes in | CR comments used for |
|---|---|---|
| `design/` | A review file in this folder | Typo-level nits only |
| `src/` | CR comments (normal flow) | Everything |
| `src/` **and reveals a design issue** | Migrate the discussion into a review file before the CR merges | Everything |

## How to file a review

1. Copy [`TEMPLATE.md`](./TEMPLATE.md) to `YYYY-MM-DD-<slug>-r<N>.md`.
2. Fill the front-matter — especially `reviewed_commit` (get it from `git log` on the design CR or `git rev-parse <branch>`) and `phase`.
3. Add numbered comments. Be specific — point at files and sections, not "somewhere in the design."
4. Submit in a CR. The author responds on the CR or in a follow-up commit.

## How to address a review (author flow)

1. Edit the design docs to fix the comment.
2. In the same CR (or a follow-up CR), update the review file:
   - Flip each addressed comment's `status` to `addressed`.
   - Fill `addressed_in_commit` — use the SHA of the commit that addresses it.
   - For deferrals, flip to `deferred` and fill `deferred_reason` + `re-evaluate_by`.
   - Update the file-level `status`.
3. Commit.

## File-level status values

| Status | Meaning |
|---|---|
| `in-progress` | Review filed; author has not yet responded |
| `partial` | Some comments addressed / deferred, others still open |
| `resolved` | All comments either addressed or explicitly deferred |

A review is "done" when it reaches `resolved`. It is not "done" just because the CR merged.

## Open + deferred items (across all reviews)

Find open items:

```bash
grep -rn "^- \*\*Status:\*\* open" design/reviews/
```

Find deferrals past their re-evaluate date:

```bash
grep -rn "re-evaluate_by" design/reviews/
```

## Index

| File | Phase | Reviewed commit | Status | Reviewer |
|---|---|---|---|---|
| [2026-05-02-requirements-phase-1a-r1.md](./2026-05-02-requirements-phase-1a-r1.md) | requirements (1a) | `1481921` | resolved | srk |
| [2026-05-02-requirements-phase-1b-r1.md](./2026-05-02-requirements-phase-1b-r1.md) | requirements (1b) | `d300785` | resolved | srk |
| [2026-05-03-requirements-phase-1c-r1.md](./2026-05-03-requirements-phase-1c-r1.md) | requirements (1c) | `41eefc0` | resolved | srk |
| [2026-05-03-design-01-overview-r1.md](./2026-05-03-design-01-overview-r1.md) | design (01-overview) | `aceca50` | resolved | srk |
| [2026-05-03-design-02-architecture-r1.md](./2026-05-03-design-02-architecture-r1.md) | design (02-architecture) | `ec90ff8` | resolved | srk |
| [2026-05-03-adrs-0001-0004-r1.md](./2026-05-03-adrs-0001-0004-r1.md) | design (ADRs 0001–0004) | `1d10c7c` | resolved | srk |
| [2026-05-03-design-03-data-model-r1.md](./2026-05-03-design-03-data-model-r1.md) | design (03-data-model) | `5a418a1` | resolved | srk |
