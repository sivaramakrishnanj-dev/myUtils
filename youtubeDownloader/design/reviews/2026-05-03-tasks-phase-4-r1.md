---
review_id: 2026-05-03-tasks-phase-4-r1
reviewed_commit: ec9e74b
reviewer: srk
author: srk
phase: tasks
status: resolved
resolved_commit: ec9e74b
resolved_at: 2026-05-03
---

# Review: Tasks — Phase 4 — round 1

**Scope:** `design/07-tasks.md` at commit `ec9e74b`. The ordered implementation plan for Phase 5 code. Covers 6 milestones (M0–M5), ~45 tasks, 78 contract test references, 6 verification gates, 8 risks, and a task → user-story mapping table.

---

## Review notes

No blocking, major, minor, or nit comments. Draft approved as-is.

**What was verified:**

- **Every user story has ≥ 1 task.** Section 7 is the mapping — US-1..US-13 all covered. No orphan stories, no orphan tasks.
- **Every verification gate is testable.** G0–G5 gate criteria are boolean-checkable (e.g., "Fat jar under 5 MB" vs "Fat jar is small"). Phase 5 developer can self-verify before moving to the next milestone.
- **Tasks are sized realistically.** The S/M/L scale is consistent across milestones. Large tasks (`T-1.5`, `T-2.3`, `T-2.5`, `T-2.8`) are correctly identified — they are the genuine complexity centres (InnerTube client, stream downloader, progress reporter, output writer).
- **Dependencies are explicit within milestones.** Each task row lists prerequisite task IDs. Cross-milestone dependencies are implicit from the G0–G5 gates; no circular deps.
- **Milestone parallelism is called out.** M4 (captions + thumbnails) can start after M1 without waiting for M2/M3 — valuable for a multi-developer run; still works for a single developer as a natural break.
- **Contract-test integration is woven in, not stapled on.** Section 3's per-milestone test-count table ties each implementation milestone to the `06-formal/contract-tests.md` test IDs that become green during that milestone. Avoids the "write all tests at the end" anti-pattern.
- **Risk register (§ 5) is honest.** R-1 (ANDROID client deprecation) and R-2 (cipher becomes mandatory) are the two existential risks, both correctly flagged as high-impact. R-3 (real-fixture shape drift) is the realistic medium-risk most Phase 5 developers will actually hit. R-5 (JaCoCo 80% pathology) mirrors OQ-C in `01-overview.md` and keeps the escape hatch (drop to 75% via NFR round) visible.
- **OOS-for-Phase-5 list (§ 6)** is appropriately restrictive. Homebrew tap, Maven Central, Docker, shell completion are all post-MVP. This prevents scope creep from "while we're at it" additions during implementation.
- **Cross-reference integrity.** 51 AC refs, 18 NFR refs, 11 INV refs, 13 US refs, 4 ADR refs, 1 OOS ref all cross-checked against their source docs during draft preparation. Every reference is live.
- **Estimate total (~5 person-weeks)** is informational — not a commitment, just a calibration anchor. Phase 5 will produce actual timings.

**Why this review has no numbered comments:**

Silence in a review process must be explicit. This file exists to record that `07-tasks.md` was reviewed, considered complete, and approved as-drafted — not that review was skipped.

**Phase gate:** **Phase 4 — Tasks is closed.** With Phase 1 + Phase 2 + Phase 3 + Phase 4 all resolved, the design baseline is complete. Phase 5 begins at T-0.1.
