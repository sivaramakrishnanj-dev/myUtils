---
review_id: YYYY-MM-DD-<slug>-r<N>
reviewed_commit: <40-char or short SHA of the commit being reviewed>
reviewer: <alias>
author: <alias>
phase: requirements | design | formal | tasks | code
status: in-progress   # in-progress | partial | resolved
resolved_commit:      # set when status = resolved
resolved_at:          # YYYY-MM-DD when status = resolved
---

# Review: <what was reviewed> — round <N>

**Scope:** <what this review covers — e.g., "all of 00-requirements.md at commit abc1234" or "ADR-0003 only">

---

## C1 — <short one-line title>

- **Location:** `<path/to/file.md>` — `<section anchor or heading>`
- **Severity:** blocker | major | minor | nit
- **Reviewer comment:**
  <The actual comment. Be specific. Point at the exact thing. Explain why it matters, not just what's wrong.>
- **Status:** open | addressed | deferred
- **Addressed in commit:** `<SHA>`   # required if status=addressed
- **Author response:**
  <Author's reply. What changed, or why they disagree.>
- **Deferred reason:**                # required if status=deferred
  <Why this is being deferred rather than addressed now.>
- **Re-evaluate by:** YYYY-MM-DD      # required if status=deferred

## C2 — <short one-line title>

- **Location:** ...
- **Severity:** ...
- **Reviewer comment:**
  ...
- **Status:** open
- **Addressed in commit:**
- **Author response:**

<!-- Add more comments as C3, C4, ... Numbering is stable across the life of the file. -->

---

## Review notes

<Optional. Free-form observations from the reviewer that don't map to a specific numbered comment — e.g., overall impression, patterns noticed across multiple files, praise.>
