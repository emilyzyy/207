# Autoschedule ownership and Checkstyle report

Recorded 2026-08-06 on `feature/emily-autoschedule`. Ownership is derived from
`git diff --diff-filter=A/M origin/main HEAD` and `git blame`, not from memory.
**Modifying a shared file does not make that file Emily-owned**, and the tables below are
arranged so that distinction stays visible.

## 1. Checkstyle configuration provenance — corrected

> **This section corrects an earlier conclusion.** Previous versions of this document said
> "no official course configuration exists" and labelled `config/checkstyle.xml`
> engineering judgment. **That was wrong.** An official CSC207 configuration does exist. It
> was found in this pass and has replaced the project-written file.

| Question | Answer | Source |
|---|---|---|
| Does CSC207 provide an official Checkstyle XML configuration? | **Yes — `mystyle.xml`** | Lecture `15-regex (1).pdf` p.5: *"you can see lots of similar examples in the **mystyle.xml** configuration file which we have used for Checkstyle this term!"* |
| Where is it distributed? | In course starter repositories, beside `pom.xml` | Found in `starter-hw5/`, `csc207-hw5/`, `hw3/hw4/hw5-git-activity/` |
| Does the course tell students to install an IntelliJ plugin? | Effectively yes — that is how the file is consumed | No course starter `pom.xml` or `build.gradle` wires Checkstyle at all (`grep -ci checkstyle` = 0) |
| Which configuration is the plugin meant to load? | `mystyle.xml` from the project root | Same |
| Is there an official command or grading setup? | **None found.** No Maven/Gradle wiring, no documented command | Searched all 30 course PDFs, Piazza findings and audit files |
| Is Checkstyle required, or only particular rules? | Following the course Checkstyle rules is required | Piazza @275 (2026-06-28, Pan Chen) |

### The file now in this repository

`config/mystyle.xml` is **byte-identical** to the copy in the course's own `starter-hw5`:

```
md5  8128987caad9cb8c58732d8f85be6f89   config/mystyle.xml
md5  8128987caad9cb8c58732d8f85be6f89   <course>/starter-hw5/mystyle.xml
```

The same checksum appears in `csc207-hw5` and `hw5-git-activity`. (`hw3` and `hw4` carry
slightly older revisions — `DesignForExtension` was dropped and `FinalLocalVariable`,
`UncommentedMain` and `UnusedLocalVariable` were added since — so the hw5 revision is the
current-term one.) It is copied unmodified, with no rules added, removed or retuned; the
provenance is recorded here rather than by editing the file, so the checksum stays
verifiable.

It is Checkstyle's own "Practice What You Preach" configuration — its metadata says *"In our
config we should use all Checks that Checkstyle has"* — carrying **225 modules** at
`severity=error`.

### It is a development aid, not a zero-warning gate

This matters for how the numbers below should be read, and it is established by the course's
own artefacts rather than asserted:

| Code | Violations under `mystyle.xml` |
|---|---|
| **CSC207 `starter-hw5` — the teaching team's own distributed code** | **62** |
| Emily's completed and submitted `csc207-hw5` | 67 |
| `hw3-git-activity` | 168 |

The course ships the configuration alongside code that does not satisfy it. Its top
violations in the course's own starter are `FinalLocalVariable` (64), `OperatorWrap` (44),
`CustomImportOrder` (14) and `ImportOrder` (9) — the same categories that dominate our
repository.

The configuration also enables **`CustomImportOrder` and `ImportOrder` simultaneously** with
incompatible settings (`ImportOrder` requires `separated=true` between groups;
`CustomImportOrder` as configured does not), so a single import block cannot satisfy both.
The course's own code trips both checks, which is the signature of that conflict.

**Conclusion:** `mystyle.xml` is the official CSC207 configuration and is now what this
project runs. It is used here as **evidence and guidance**, exactly as the course uses it —
not as a zero-warning build gate. `failOnViolation` stays `false`.

### How it is run

```bash
./mvnw checkstyle:check          # report -> target/checkstyle-result.xml
```

`pom.xml` pins `com.puppycrawl.tools:checkstyle:10.21.4` under the plugin, because the
plugin's bundled Checkstyle predates `SuppressWithNearbyTextFilter` (10.10) and
`NoCodeInFile` (10.9) and cannot load the course file. Pinning the tool is what lets the
course configuration be used **exactly as distributed** rather than edited to suit our build.

No IDE extension is installed or committed. The IntelliJ Checkstyle plugin is a reasonable
developer convenience — and is how the course itself expects the file to be used — but
Maven plus the official XML is the reproducible project-level check.

`config/checkstyle.xml`, the project-written approximation used before this pass, has been
**removed**; Git history retains it.

## 2. Results by ownership

Under the official configuration, whole repository, **after integrating `origin/main`
(`11d4ddc`) and Alex's branches (`7e3b460`, `224481b`)**. Attribution is by the original
author of each file, from `git log --diff-filter=A`.

| Author | Files | Violations |
|---|---|---|
| Emily — production | 84 | 1360 |
| Emily — tests | 37 | 1468 |
| Raashid | 11 | 809 |
| Alex | 8 | 122 |
| Other teammates (Shiyuan, Bianca, shared legacy) | 83 | 1584 |
| **Total** | | **5343** |

The previous figure was 5024. The rise is **merged teammate code arriving**, not new
defects: Raashid's expanded `OsrmDistanceService`, Alex's nine new classes, and Emily's new
integration test.

**This integration introduced no new unambiguous violations.** Filtering the files touched
in this pass against the categories already ruled out leaves exactly three findings — a
non-ASCII `·` in `DayPlanPanel`, a `\u2026` escape and an overload ordering in `AppBuilder`
— and all three were verified present before the pass began. Nothing was fixed, because
there was nothing new to fix, and manufacturing changes to show activity would have been
worse than reporting the truth.

Repository-wide, by check:

| Check | Count |
|---|---|
| FinalLocalVariable | 1712+ |
| CustomImportOrder / ImportOrder (mutually unsatisfiable pair) | 963 / 245 |
| MagicNumber | 445 |
| JavadocMethod / MissingJavadocMethod | 301 / 137 |
| AvoidInlineConditionals | 206 |
| ReturnCount | 152 |

## 3. What was fixed, and what was deliberately not

**Fixed in Emily-owned code — 2 violations, 2 files:**

| File | Check | Change |
|---|---|---|
| `AutoScheduleLiveVerificationTest` | `IllegalIdentifierName` | Private helper named `record` → `recordResult`. `record` is a restricted identifier in modern Java; renaming is a genuine improvement at zero risk. |
| `AutoScheduleSettingsValidator` | `MultipleStringLiterals` | Repeated `"Unavailable period "` literal extracted to a `private static final` constant. |

Total: **5026 → 5024**.

The categories the brief nominated as the safe subset — `NewlineAtEndOfFile`,
`RedundantImport`, `AvoidStarImport`, duplicate imports — were **already at zero** in
Emily-owned code, because the previous project configuration enforced them and the code was
kept clean against it. There was no backlog of that kind to clear.

**Deliberately not fixed, with reasons:**

| Check | Count (Emily) | Why not |
|---|---|---|
| `FinalLocalVariable` | 953 | Adding `final` to 953 locals across 64 files is exactly the mass mechanical churn the brief rules out. The course's own starter code has 64 of these. |
| `CustomImportOrder` + `ImportOrder` | 472 | **Mutually unsatisfiable as configured.** Reordering to satisfy one guarantees violating the other. |
| `JavadocMethod` / `MissingJavadocMethod` | 271 | Mass Javadoc generation, explicitly excluded. |
| `AvoidInlineConditionals` | 86 | Removing 86 ternaries is a behaviour-preserving rewrite with no readability gain. |
| `ReturnCount` | 84 | Rewriting methods solely to reduce returns; guard clauses are clearer than the alternative. |
| `RightCurly` | 17 | Wants `}` alone on its line *before* `catch`, and `public enum Kind { ACTIVITY, TRAVEL }` expanded over four lines. Contradicts standard Java formatting and the rest of the repository. |
| `NeedBraces` | 10 | Wants `(a, b) -> Integer.compare(a, b)` to become `(a, b) -> { return …; }`. Verbosity with no benefit. |
| `ParameterName` / `LambdaParameterName` | 21 | Rejects two-letter names, i.e. `to` on the **`TravelTimeEstimator` interface**. `from`/`to` are the clearest possible names for a travel estimator; renaming interface parameters to satisfy a report would make the code worse. |
| `InnerTypeLast` | 39 | Moving nested types produces large diffs for no functional gain. |
| `MagicNumber`, `NPath`/`Cyclomatic`, `VisibilityModifier`, others | ~90 | Either intentional (tuned policy constants, documented in situ) or requiring redesign. |

**Nothing teammate-owned was reformatted.**

## 4. Ownership detail

### A–B. Emily-owned (99 files)

Everything under `src/main/java/closeai/application/autoschedule/` (including `engine/` and
`policy/`), plus `adapters/controllers/AutoSchedule*`, `TaskRunner`, `SwingTaskRunner`,
`adapters/presenters/AutoSchedulePresenter`, `adapters/viewmodels/AutoScheduleStatus`,
`PreviewRowView`, `PreviewMetricsView`, `adapters/views/AutoScheduleSettingsDialog`,
`adapters/gateways/DistanceServiceTravelTimeEstimator`, `WeatherServiceContextGateway`, and
the matching test classes. Non-source: `config/mystyle.xml` (course file) and everything
under `docs/autoschedule/`.

### C. Shared files modified by Emily (5 files with violations, 7 modified)

These belong to teammates. Emily changed them for a stated reason and nothing wider.

| File | Original author | Emily's change | +/− |
|---|---|---|---|
| `README.md` | Shiyuan | Additive Day Plan section; one heading disambiguated | +139/−2 |
| `pom.xml` | Raashid | JaCoCo, Checkstyle, course config, pinned tool version | +70/−3 |
| `AppBuilder.java` | Raashid | Wires the one Autoschedule path | +47/−20 |
| `DayPlanState.java` | Shiyuan | Additive state only | +119/−1 |
| `DayPlanPanel.java` | Shiyuan | Rewritten around Autoschedule (agreed feature owner) | +297/−41 |
| `SwingApplicationIntegrationTest.java` | Raashid | Button rename | +11/−8 |
| `SwingPanelStructureTest.java` | Raashid | Button rename; test double gained `weatherOptionFor` | +25/−19 |

### D. Raashid-owned routing file — 95 violations, none Emily's

`src/main/java/closeai/infrastructure/routing/OsrmDistanceService.java`. Emily's entire
change to this file is **one line plus one comment**: TomTom documents
`latitude,longitude` and was receiving `longitude,latitude`, so every driving request failed
and silently fell back to OSRM. Contract, fallback and key handling untouched.

`git blame` places every one of the 95 violations in Raashid's own commits. Per the batch
rule his code was not rewritten beyond that fix.

> **Resolved.** `11d4ddc` is now merged. Raashid's implementation is the production source:
> his failure logging and `.env` key fallback are kept whole, and the only thing carried over
> from this branch is the comment recording why the order is `latitude,longitude`. His file
> now carries 809 violations under the official config, all his own. Emily's nine routing
> regression tests are retained and pass unchanged against his implementation — `origin/main`
> has no routing tests at all.

### E. Unrelated teammate-owned files — 2547 violations, 121 files

**Deliberately not reformatted.** Rewriting a teammate's file to satisfy a style report
would obscure their authorship for no functional gain, and the course asks that members not
take over each other's assigned code.

## 5. Standing items for the team

- **Adopt `config/mystyle.xml` team-wide?** It is now wired for the whole repository. The
  5024 warnings are almost entirely pre-existing and in nobody's way, since the build is not
  gated. Worth a team decision before anyone treats the number as a target.
- **Raashid's `11d4ddc`** is merged; `.env` and `.env.*` are now in `.gitignore`, which his
  key fallback made necessary. No `.env` has ever existed in any commit on any branch.
- **Alex's work is merged** — `7e3b460` (discovery, bookmarking) and `224481b` (manual
  add/edit/remove). His authorship is preserved; his files are his.
- **CAVE packaging** is an open team-level risk, deliberately not acted on here. See
  [`../cave/cave-final.md`](../cave/cave-final.md).
