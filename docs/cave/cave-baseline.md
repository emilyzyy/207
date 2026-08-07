# CAVE baseline

Recorded 2026-08-06 on `feature/emily-autoschedule`, after merging `origin/main` (`11d4ddc`)
and `origin/alex-activity-discovery` (`7e3b460`).

> **CAVE was not run.** No baseline output exists, because the checker could not be
> obtained. Everything below is investigation, not a check result. Nothing in this document
> should be read as a compliance claim.

## 1. What was attempted, and what it produced

| Attempt | Result |
|---|---|
| `which cave` | not on PATH |
| `find` across the home directory for `*cave*` | one unrelated PDF |
| `pip download cave` | **downloads a different tool** — `cave 1.4.1`, "an analyzing tool for configuration optimizers", `github.com/automl/CAVE`, authored at Uni Freiburg. **Not the CSC207 tool.** Recording this so nobody later runs it and believes they have checked their architecture. |
| PyPI lookups for `cave-tool`, `csc207-cave`, `cave-verify` | no such packages |
| Web search for the CSC207 CAVE tool | nothing; it is course-internal |
| The Quercus page *CAVE Instructions for Checking Your Code* | requires authentication; not reachable from here |

**Consequence:** the brief's instruction "Run CAVE against the current repository before
refactoring, if locally possible" resolves to *not locally possible*, and the instruction
"Do not claim CAVE compliance without an actual successful check" therefore forbids any
compliance claim in either direction — including a claim that the current layout fails.

## 2. What the course sources actually say

These are prior full-thread readings recorded in the local course audit
(`/Users/emily/Documents/207-course-audit-2026-08-04/`), with ledger IDs.

| Source | Recorded content |
|---|---|
| Quercus, *CAVE Instructions for checking your code* (**S-074**, read in full 2026-08-04) | Documents `cave verify` and `cave start` as **optional checking/learning commands**. |
| Piazza **@354** (CAVE package names) and **@355** (CAVE false positives) (**S-066**, full threads read) | "CAVE is **not used for automatic project grading**; its suggestions are reference material. Tool findings, **including package-name assumptions**, require manual dependency/import verification." Pan agreed the reported tool concerns were valid but **did not** convert the student's repository-specific claims into a universal rule. |
| Piazza **@348** | "**Any package organization is permitted if it is consistent, documented, and justifiable.**" |
| Audit conclusion (`piazza-findings.md` §11) | "Repository dependency direction must therefore be audited from imports and construction, **not accepted or rejected solely from a folder name or CAVE output**." |
| Week 10 Quercus announcement (**S-073**) | Presents CAVE as a check/demo after the project midpoint. |

## 3. The conflict

The announcement quoted in the current task states packaging **must** be:

```
src/main/java/
  app/  database/  entity/  interface_adapter/  views/
  use_case/
    use_case_1/  use_case_2/  ...
```

That is a mandate. The audit-recorded sources say the opposite twice — that any justifiable
organisation is permitted (@348) and that CAVE's package-name assumptions are reference
material needing manual verification (@354/@355).

Both cannot be simultaneously authoritative. Possible readings:

1. The new announcement **supersedes** @348/@354/@355 and packaging is now mandatory.
2. The announcement describes the layout **CAVE expects in order to analyse a project**,
   without changing what the project is graded on — consistent with CAVE being a learning
   aid.
3. It applies to newly created projects rather than retrofitting an existing team repository
   in its final week.

**This cannot be resolved from here.** The announcement text is available only through the
task prompt; the Quercus page and Piazza are behind authentication. Reading 2 is the one
most consistent with every recorded source, but that is inference, not evidence.

## 4. Open questions the instructions would have to answer

Recorded because a refactor cannot be done correctly without them, and none is answerable
from the material available here:

- Is a root package such as `closeai` permitted? The mandated tree shows packages directly
  under `src/main/java/`, implying **no root package** — which is what makes this a
  whole-repository change.
- `views` or `view`? The announcement says `views`; much CSC207 material uses `view`.
- `database`, `data_access`, or both? The announcement lists `database`; the repository's
  `ItineraryDataAccessInterface` naming suggests `data_access` is also recognised.
- How does CAVE identify an individual use case — by folder, by an `*InputBoundary`, by an
  `*Interactor` class, or by convention?
- Where do **gateway interfaces owned by a use case** belong: inside that use case's folder,
  or in a shared location?
- Where do **gateway adapter implementations** belong: `interface_adapter/` or `database/`?
- How does CAVE report dependency violations, and what counts as a false positive (@355
  indicates there are known ones)?

## 5. Scope, measured

The repository has **171 production and 56 test Java files, every one of them under the
`closeai` root package.** The mandated layout has no root package, so satisfying it
literally means changing the package declaration of **every file in the repository** and
every import that references them.

| Current package | Plausible CAVE target | Files | Emily | Teammates |
|---|---|---|---|---|
| `closeai/application/autoschedule` | `use_case/autoschedule` | 39 | **39** | 0 |
| `closeai/application/usecases` | `use_case/<one folder each>` | 28 | 0 | 28 |
| `closeai/application/scheduling` | `use_case/<owner>` | 2 | 0 | 2 |
| `closeai/application/ports` | `use_case/<owner>` or shared | 7 | 0 | 7 |
| `closeai/adapters/controllers` | `interface_adapter/<feature>` | 12 | 7 | 5 |
| `closeai/adapters/presenters` | `interface_adapter/<feature>` | 6 | 2 | 4 |
| `closeai/adapters/viewmodels` | `interface_adapter/<feature>` | 18 | 3 | 15 |
| `closeai/adapters/gateways` | `interface_adapter/<feature>` | 2 | 2 | 0 |
| `closeai/adapters/views` | `views` | 17 | 1 | 16 |
| `closeai/domain/entities` | `entity` | 5 | 0 | 5 |
| `closeai/domain/valueobjects` | `entity` | 6 | 0 | 6 |
| `closeai/infrastructure/persistence` | `database` | 3 | 0 | 3 |
| `closeai/infrastructure/{routing,weather,places,mock}` | `database` / `data_access` | 10 | 0 | 10 |
| `closeai/infrastructure/web` | `app` or drivers | 1 | 0 | 1 |
| `closeai/application` (`AppContainer`) | ambiguous | 2 | 0 | 2 |
| `closeai` (`AppBuilder`, `Main`, `DemoSeeding`) | `app` | 3 | 0 | 3 |
| **Total** | | **161** | **54** | **107** |

*(161 counts files in mapped packages; the remaining 10 are in nested engine/policy folders
that move with `use_case/autoschedule`.)*

**Two thirds of the affected files belong to teammates.** This is squarely **Case 3 —
repository-wide team migration** under the brief's own classification, which says: *"Do not
unilaterally move every teammate's package… stop for Emily/team approval before performing
the repository-wide move."*

### What Emily could move independently

`closeai/application/autoschedule/**` (39 files, including `engine/` and `policy/`) is
entirely Emily-owned and self-contained. Moving it to `use_case/autoschedule/` would touch
only her files plus the import lines in files that reference them.

### What cannot move independently

`entity`, `database`, `views`, `app`, `application/usecases` and most of
`interface_adapter` are teammate-owned or shared. Alex's brand-new discovery work and
Raashid's routing fix both landed today; moving their packages under them, in the final
week, without agreement, is the kind of change that loses other people's work in a conflict.

Also note: a **partial** migration is likely worse than either extreme. If CAVE expects
top-level `use_case/`, `entity/`, `app/`, then moving only Autoschedule produces a tree with
both `use_case/autoschedule/` and `closeai/**`, which satisfies neither layout and makes
every subsequent teammate merge conflict.

## 6. What is actually verifiable today

CAVE's stated purpose — checking that dependencies point inward — **is** verifiable here,
from imports and construction, which is exactly what the audit says is authoritative
(`piazza-findings.md` §11). That evidence already exists and is independent of folder names:

- `docs/autoschedule/diagrams/` — the full use-case class diagram, every node cross-checked
  against source.
- `docs/autoschedule/architecture.md` — dependency direction per layer.

A direct import-based dependency check is recorded in `cave-final.md`.

## 7. Status

**Blocked pending Emily's decision.** No packages were moved in this pass. The question put
to her is whether to (a) leave the layout and document the justification under @348,
(b) move only `application/autoschedule` → `use_case/autoschedule`, or (c) plan a
team-wide migration to be executed with the team's agreement.
