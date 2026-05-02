---
review_id: 2026-05-02-requirements-phase-1b-r1
reviewed_commit: d300785
reviewer: srk
author: srk
phase: requirements
status: resolved
resolved_commit: d300785
resolved_at: 2026-05-02
---

# Review: Requirements — Phase 1b (EARS acceptance criteria) — round 1

**Scope:** `design/00-requirements.md` Phase 1b section at commit `d300785` (acceptance criteria for US-1..US-13, resolved open questions table for OQ-1..OQ-6, central exit-code contract, 9 symbolic NFR references). Phase 1a (user stories) was resolved separately at `1481921`. Phase 1c NFRs are out of scope for this review.

---

## Review notes

No blocking, major, minor, or nit comments. Draft approved as-is.

**What was verified:**

- **Coverage** — every user story US-1..US-13 has at least one AC; AC counts per story (7, 5, 6, 4, 5, 4, 4, 4, 5, 5, 4, 4, 5 = 62 total) are proportional to story complexity and nobody is under-specified. US-1 (video) and US-3 (paths/names) carry the most ACs because they have the most observable surface area; US-4 (progress), US-7 (manual→ASR), US-8 (language), US-11 (offline tests), US-12 (InnerTube) sit at 4 ACs each, which is enough to be testable without over-specifying.
- **EARS discipline** — every AC is tagged with exactly one of `[ubiquitous]`, `[event-driven]`, `[state-driven]`, `[unwanted]`, `[optional]`. No untagged criteria and no mixed-type criteria.
- **Behaviour vs thresholds** — numeric thresholds do not appear inline; they are referenced through symbolic `NFR-*` names (9 distinct identifiers introduced in this phase). This keeps Phase 1b behavioural and leaves Phase 1c as the single place where numbers are pinned.
- **Open-question resolution** — OQ-1 through OQ-6 each have an explicit resolution in the "Resolved open questions" table with a cross-reference to the AC(s) where the resolution takes effect. OQ-7 (min ffmpeg version) is explicitly deferred to Phase 1c with the NFR name `NFR-MIN-FFMPEG-VERSION` reserved.
- **Central contracts** — the exit-code mapping is defined once in AC-5.2 with 11 documented categories and is referenced by every failure-related AC (AC-1.7, AC-3.6, AC-5.3, AC-6.4, AC-7.4, AC-8.3, AC-12.4, AC-13.2, AC-13.3, AC-13.4). This is the kind of single-source-of-truth that will keep `06-formal/cli-exit-codes.md` and the code aligned in Phase 3 and Phase 5.
- **Three pre-flagged design tensions** (exit-code scheme AC-5.2, network-fails-unit-tests AC-11.3, injectable progress listener AC-9.3) were acknowledged during draft preparation and accepted as-drafted. They remain legitimate review targets for later rounds if implementation experience surfaces a reason to revisit — in which case a new review round (`-r2`) on this file would be filed per the reviews/README.md rules.
- **Traceability** — every AC references its user story by ID in the heading, and every behavioural default has a traceable link back to an OQ-N resolution.

**Why this review has no numbered comments:**

Silence in a review process must be explicit. This file exists to record that the Phase 1b draft was reviewed, considered complete, and approved as-drafted — not that review was skipped. Future contributors reading the requirements doc can trace its approval to commit `d300785` via this review file.

**Phase gate:** Phase 1b is **resolved**. Proceeding to Phase 1c — numeric NFRs, which will pin all 9 symbolic `NFR-*` references introduced by 1b plus any additional operational / quality thresholds the tool needs.
