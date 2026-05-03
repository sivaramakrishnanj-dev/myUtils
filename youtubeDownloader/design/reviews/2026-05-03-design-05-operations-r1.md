---
review_id: 2026-05-03-design-05-operations-r1
reviewed_commit: ebcc8b9
reviewer: srk
author: srk
phase: design
status: resolved
resolved_commit: ebcc8b9
resolved_at: 2026-05-03
---

# Review: Design — 05-operations.md — round 1

**Scope:** `design/05-operations.md` at commit `ebcc8b9`. Fifth and final Phase 2 Design artifact. Covers build, run, metrics/observability, testing, common failures and remediation, packaging, updating, known limits, and Phase 3/4/5 preview.

---

## Review notes

No blocking, major, minor, or nit comments. Draft approved as-is.

**What was verified:**

- **Prereqs and build commands (§ 1)** — exact shell commands for macOS/Debian/RHEL install, exact Maven commands for build, test, integration, coverage. Module layout is clear: parent aggregator, `yt-core` library, `yt-cli` CLI, per `02-architecture.md` § 7. Pre-release verification checklist is concrete.
- **Run examples (§ 2)** — covers every flag combination that matters for first-time users. The shell wrapper in § 2.3 shows an exit-code-driven retry-and-fallback pattern that exercises the AC-5.2 contract in a real shape.
- **Observability posture (§ 3)** — explicit about what MVP has (SLF4J at five levels, per-component logger names for operator-side level tuning) and what it doesn't (Prometheus, CloudWatch, OpenTelemetry, persistent history, remote crash reporting). This is the right honest framing for a learning-project CLI.
- **Testing discipline (§ 4)** — unit tests are offline per AC-11.3/AC-11.4 against checked-in fixtures with `x-captured-on` metadata (aligned with `06-formal/README.md` convention). Integration tests are opt-in via `-P integration` and not subject to the 80% coverage gate. The coverage gate is scoped to `yt-core` only, which is the right call — gating CLI glue on coverage encourages trivial tests.
- **Failure matrix (§ 5)** — every AC-5.2 exit code plus 130/143 signal codes mapped to typical cause and user remediation. Decision tree in § 5.1 is a practical diagnostic path, not a bureaucratic one. Exit `22` (cipher) steers users to yt-dlp as the honest remediation per OOS-2.
- **Packaging (§ 6)** — fat-jar size target ≤ 10 MB backed by a back-of-envelope tally of ~4 MB from the ADR-chosen dependencies. Distribution is source-only for MVP; Homebrew tap / Maven artifact / GitHub Release binaries are all explicit Future Work. Semver documented, with the carve-out that `NFR-ANDROID-*` triplet updates are allowed in PATCH releases (they're YouTube-driven, not semver-governed).
- **Upgrade flow (§ 7)** — git pull + `mvn clean package` for the tool itself; `brew upgrade ffmpeg` for the external dep. The "when YouTube breaks the tool" diagnostic flowchart in § 7.3 captures the OQ-A early-warning procedure directly. Known limits (§ 7.4) are blunt: cipher out, playlists out, auth out, live out, Windows out.
- **Phase 3/4/5 preview (§ 8)** — correctly forecasts the next three phases' artifacts (JSON Schemas and contract tests for 3, task breakdown for 4, code milestones for 5).
- **Cross-reference integrity** — 13 AC refs, 7 NFR refs, 5 OOS refs all cross-checked against `00-requirements.md`.

**Why this review has no numbered comments:**

Silence made explicit.

**Phase gate:** `05-operations.md` is **resolved**. With `01-overview.md` (`aceca50`), `02-architecture.md` (`ec90ff8`), ADRs 0001–0004 (`f44e681`/`bae8a87`/`278f51f`/`1d10c7c`), `03-data-model.md` (`5a418a1`), `04-apis.md` (`4088d0f`), and `05-operations.md` (`ebcc8b9`) all resolved or Accepted, **Phase 2 — Design is closed.** The combined Phase 1 + Phase 2 baseline is now the ground truth for Phase 3 formal contracts, Phase 4 tasks, and Phase 5 code. Further changes to any Phase 2 artifact require a new review round on the relevant file and an explicit phase-transition entry.

Proceeding to Phase 3 — Formal contracts.
