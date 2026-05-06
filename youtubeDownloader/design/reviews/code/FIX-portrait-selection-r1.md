---
review_id: FIX-portrait-selection-r1
reviewed_commit: f21a063
reviewer: specDrivenReviewer
author: specDrivenImplementer + specDrivenTester
phase: code
task: FIX-portrait-selection
status: resolved
round: 1
---

# Review: FIX-portrait-selection — Portrait video format selection fix — round 1

**Scope:** `FormatSelector.java` (modified) + `FormatSelectorBehaviorTest.java` (modified) — fix for portrait-video max-height filter bug reported in L-6 of `design/reviews/2026-05-06-lessons-learned-post-M5-integration.md`.
**Inherited rulebook:** java-code-reviewer + _shared/review-rubric.md
**Added layer:** Spec-Adherence against AC-1.3, AC-1.4.
**Overlays applied:** (none).

---

## C1 — Clean, minimal fix with correct semantics

- **Location:** `FormatSelector.java:225–232` — `qualityHeight(Format)`
- **Severity:** Praise
- **Category:** Correctness
- **Spec citation:** AC-1.3
- **Reviewer comment:**
  `Math.min(width, height)` is the right abstraction for "quality tier" — it matches YouTube's own `qualityLabel` semantics (1080p = shorter dimension is 1080 regardless of orientation). The fix is surgical: one new helper, two call-site changes (filter + sort), no unrelated refactoring. The defensive `MAX_VALUE` fallback for absent dimensions is sound — video formats always have both, but the fallback ensures the selector doesn't NPE on malformed data and correctly excludes unknowns from capped filters.

## C2 — INFO log uses raw height, not qualityHeight

- **Location:** `FormatSelector.java:156` — `video.height().orElse(0)`
- **Severity:** Minor
- **Category:** Observability
- **Spec citation:** AC-10.* (structured logs on external-boundary calls)
- **Reviewer comment:**
  The INFO log at line 156 prints `video.height().orElse(0)` as the `{}p` value. For a portrait 1080x1920 video, this logs `"Format selected: video itag=137 1920p avc1.640028 4500000bps"` — misleading for operators who expect `1080p` to match YouTube's quality label. Not a correctness bug (selection is correct), but the log becomes confusing when diagnosing portrait-video issues.
- **Suggested fix:**
  Replace `video.height().orElse(0)` with `qualityHeight(video)` in the log format string, so the log reads `1080p` for both landscape and portrait 1080p-tier formats.

## C3 — Thorough test coverage for the fix

- **Location:** `FormatSelectorBehaviorTest.java` — `PortraitVideo` (7 tests) + `QualityHeight` (6 tests)
- **Severity:** Praise
- **Category:** Testing
- **Spec citation:** AC-1.3, AC-1.4
- **Reviewer comment:**
  13 new tests cover the exact bug scenario (portrait H.264 vs VP9), regression cases (landscape, square), edge cases (absent dimensions), and the helper's contract directly. The `portraitVideo(...)` factory method correctly sets width < height to model real YouTube Shorts data. The regression tests for landscape and square confirm no behavioral change for existing content. Well-structured nested classes with clear `@DisplayName` annotations.

## C4 — qualityHeight is package-private and directly tested

- **Location:** `FormatSelector.java:225` — `static int qualityHeight(Format f)`
- **Severity:** Praise
- **Category:** API-Design
- **Reviewer comment:**
  Package-private visibility is the right choice — it enables direct unit testing (the `QualityHeight` nested class) without polluting the public API. Consistent with the existing `codecRank` and `containerRank` helpers at the same visibility level. Clean Code: small, single-purpose, intention-revealing name.

## C5 — AC-1.3 wording says "height" but semantic intent is "quality tier"

- **Location:** design spec — AC-1.3 in `00-requirements.md` line 316
- **Severity:** Discussion
- **Category:** Discussion
- **Spec citation:** AC-1.3
- **Reviewer comment:**
  AC-1.3 verbatim: *"The system SHALL filter the available video formats to those whose `height ≤ max-height`"*. The literal reading of "height" means the pixel-count height field from InnerTube. For portrait content (YouTube Shorts, 1080×1920), the height field is 1920 — which exceeds the default max-height of 1080 and incorrectly excludes the format. The fix interprets "height" as "quality tier" (shorter dimension), which matches YouTube's own `qualityLabel` semantics and user expectations. This is the correct semantic reading, but the spec text is ambiguous. A future DCR should clarify AC-1.3 to say "shorter dimension" or "quality-tier height (min of width, height)" to prevent re-litigation.
- **Suggested amendment kind:** ac-update (clarify AC-1.3 wording to explicitly state shorter-dimension semantics for orientation-agnostic filtering)
- **Impact on this review's verdict:** none — this is a spec gap, not a code defect. The code is correct.

## C6 — Sort comparator uses qualityHeight consistently

- **Location:** `FormatSelector.java:213` — `videoComparator()`
- **Severity:** Praise
- **Category:** Correctness
- **Spec citation:** AC-1.4(a) — "resolution (height)" tiebreak
- **Reviewer comment:**
  The comparator's first key is `Comparator.comparingInt(FormatSelector::qualityHeight)`, ensuring the sort order matches the filter semantics. A portrait 1080p format (qualityHeight=1080) now correctly ties with a landscape 1080p format (qualityHeight=1080), and the codec preference (AC-1.4(b)) breaks the tie in favor of H.264. This is exactly the behavior the bug report described as desired.

---

## Review summary

- Blockers: 0
- Majors: 0
- Minors: 1
- Nits: 0
- Praise: 4
- Discussion: 1
- **Verdict:** resolved

## Coverage verification

- Task ACs satisfied in diff: AC-1.3 (portrait-aware filter), AC-1.4 (codec preference now applies correctly across orientations)
- Task ACs not visibly satisfied: (none)
- Contract tests satisfied in diff: CT-APP-3, CT-APP-4 (existing, still pass)
- Contract tests not yet satisfied: (none applicable to this fix)
- Coverage on new code: `qualityHeight` is 100% covered (6 direct tests + 7 integration-level tests through `PortraitVideo`)

## Inherited-rubric sweep

| Category | Findings |
|---|---|
| Correctness | ✅ Fix is correct. `Math.min(w, h)` matches YouTube's quality-label semantics. |
| Testing | ✅ 13 new tests, regression coverage for landscape/square. |
| Observability | ⚠️ C2 — log line uses raw height, misleading for portrait. Minor. |
| API-Design | ✅ Package-private helper, consistent with existing pattern. |
| Readability | ✅ Clear Javadoc on `qualityHeight` explaining the orientation logic. |
| Security | N/A |
| Reliability | ✅ Defensive MAX_VALUE fallback for absent dimensions. |
| Performance | N/A (pure function, no allocation change) |

## Spec-adherence sweep

| Spec ID | Result |
|---|---|
| AC-1.3 | ✅ Filter now uses `qualityHeight(f) <= maxHeight` — portrait 1080p passes at default maxHeight=1080. |
| AC-1.4 | ✅ Codec preference (H.264 > VP9 > AV1) now correctly applies — portrait H.264 beats VP9 at same quality tier. |
| AC-1.4(a) | ✅ Resolution tiebreak uses `qualityHeight` — orientation-agnostic. |
| AC-1.4(b) | ✅ Codec rank unchanged (avc1=2, vp9=1, av01=0). |
| AC-1.4(c) | ✅ Bitrate tiebreak unchanged. |

## Answers to review questions

1. **`Math.min(width, height)` vs `qualityLabel` parsing:** `Math.min` is correct and sufficient. It produces the same value as YouTube's qualityLabel for all standard aspect ratios (16:9, 9:16, 4:3, 1:1). Parsing `qualityLabel` (e.g., "1080p60") would require string manipulation and is fragile (format varies, may be absent on some formats). The current approach is more robust.

2. **Absent-dimension fallback `Integer.MAX_VALUE`:** Correct choice. A format with unknown dimensions is excluded by any capped filter (maxHeight > 0), which is the safe default — you don't want to accidentally select an unknown-resolution format. The alternative (treat as 0, always included) would risk selecting a format that might be 4K or higher.

3. **AC-1.3 wording DCR:** Flagged as Discussion item C5. Not blocking this fix.

4. **Other raw height usages:** Only one remains — the INFO log at line 156 (C2 above). It's cosmetic, not a selection decision. No other selection logic uses raw height.

5. **Test coverage adequacy:** 13 new tests covering the exact bug, regressions, edge cases, and the helper directly — more than adequate for a 7-line logic change. The existing 20+ tests in the file continue to pass, confirming no regression.
