# CAVE — final status for this branch

Recorded 2026-08-06 on `feature/emily-autoschedule`, after integrating `origin/main`
(`11d4ddc`, Raashid's TomTom fix) and `origin/alex-activity-discovery` (`7e3b460`,
`224481b`, Alex's discovery and add/edit/remove plan work).

> ## The one-line answer
>
> **CAVE was not run, so this project is not claimed to pass CAVE.** What *is* verified is
> an import-based Clean Architecture audit of the integrated tree, which passes every rule
> it checks. **Exact CAVE package-name compliance is an open team-level submission risk.**

## 1. The requirement, as stated

A Piazza announcement states:

> "For CAVE to check that your code follows Clean Architecture, you must package your code
> as follows. Each use_case or Application Business Rules must have its own folder.
>
> ```
> src/main/java/
>   app/
>   database/
>   entity/
>   interface_adapter/
>   views/
>   use_case/
>     use_case_1/
>     use_case_2/
>     ...
> ```

Linked page: *CAVE Instructions for Checking Your Code*.

## 2. The earlier guidance it appears to conflict with

All recorded in the local course audit from full-thread readings on 2026-08-04.

| Source | Recorded content |
|---|---|
| Piazza **@348** | "Any package organization is permitted if it is **consistent, documented, and justifiable**." |
| Piazza **@354** (CAVE package names), **@355** (CAVE false positives) — ledger **S-066** | "CAVE is **not used for automatic project grading**; its suggestions are reference material. Tool findings, **including package-name assumptions**, require manual dependency/import verification." Pan agreed the reported concerns were valid but did not turn them into a universal rule. |
| Quercus *CAVE Instructions for checking your code* — ledger **S-074** | Documents `cave verify` and `cave start` as **optional** checking/learning commands. |
| Audit conclusion, `piazza-findings.md` §11 | "Repository dependency direction must be audited from imports and construction, **not accepted or rejected solely from a folder name or CAVE output**." |

Three readings are possible: the announcement supersedes @348/@354/@355; or it describes the
layout CAVE needs *in order to analyse* a project without changing grading; or it targets new
projects rather than a retrofit in the final week. **Reading 2 is most consistent with every
recorded source, but that is inference and is not treated here as settled.**

## 3. Why CAVE could not be run

| Attempt | Outcome |
|---|---|
| `which cave` | not on PATH |
| filesystem search for `*cave*` | one unrelated PDF |
| `pip download cave` | **a different tool entirely** — `cave 1.4.1`, "an analyzing tool for configuration optimizers", `github.com/automl/CAVE`. Recorded so nobody runs it and believes their architecture was checked. |
| PyPI `cave-tool`, `csc207-cave`, `cave-verify` | do not exist |
| Web search | nothing; the tool is course-internal |
| Quercus instructions page | authentication required |

No baseline output exists. See [`cave-baseline.md`](cave-baseline.md).

## 4. What *is* verified: import-based Clean Architecture audit

This is the check CAVE exists to perform, done from imports and construction — the method
the audit itself calls authoritative. Run against the **integrated** tree, 176 production
files.

**Inward-dependency violations: 0.** No inner layer imports an outer one.

| Rule | Result |
|---|---|
| Controllers depend on Input Boundaries, not concrete Interactors | **PASS** |
| Interactors implement Input Boundaries | **PASS** |
| Presenters implement Output Boundaries | **PASS** |
| Interactors depend only on application-owned gateway interfaces | **PASS** |
| Concrete data access and API adapters point inward | **PASS** |
| Views import no Interactor and no infrastructure | **PASS** |
| Entities import no `application`, `adapters` or `infrastructure` | **PASS** |
| Package declarations match source paths (176/176) | **PASS** |
| No package cycle between layers | **PASS** |

Per use case:

- **Autoschedule** — `AutoScheduleInteractor` implements `AutoScheduleInputBoundary`;
  `AutoSchedulePresenter` implements `AutoScheduleOutputBoundary`; the Interactor imports
  only `TripRepository`, `TravelTimeEstimator` and `WeatherContextGateway`, all declared
  inside the use-case package.
- **Add-to-plan / discovery (Alex)** — `ManualPlanController` and `BookmarkController` call
  application use cases and never touch a repository directly;
  `ManualPlanPresenter` writes only to view models.
- **The seam between them** — enforced by a test, not prose:
  `AddToPlanAutoscheduleIntegrationTest.autoscheduleHasNoDependencyOnAddToPlanClasses`
  reads every file in the Autoschedule package and fails if any names
  `ManualPlanController`, `AddActivityToPlanUseCase` or the discovery classes. The two
  features meet only at `Trip` via `TripRepository`.

**This does not establish CAVE compliance.** CAVE may additionally require exact folder
names, which this tree does not use.

## 5. Current package structure

All **176 production and 57 test files** live under the `closeai` root package:

```
closeai/
  AppBuilder, Main, DemoSeeding
  application/{autoschedule{,engine,policy}, usecases, ports, scheduling}, AppContainer
  adapters/{controllers, presenters, viewmodels, views, gateways}
  domain/{entities, valueobjects}
  infrastructure/{routing, weather, places, persistence, mock, web}
```

The mandated tree has **no root package**, which is what turns compliance into a
whole-repository change rather than a local one.

## 6. Complete migration map (not executed)

| Current package | CAVE target | Files | Emily | Teammates |
|---|---|---|---|---|
| `closeai/application/autoschedule` (+`engine`,`policy`) | `use_case/autoschedule` | 39 | **39** | 0 |
| `closeai/application/usecases` | `use_case/<one folder per use case>` | 28 | 0 | 28 |
| `closeai/application/scheduling` | `use_case/<owner>` | 2 | 0 | 2 |
| `closeai/application/ports` | `use_case/<owner>` or shared | 7 | 0 | 7 |
| `closeai/application` (`AppContainer`) | ambiguous | 2 | 0 | 2 |
| `closeai/adapters/controllers` | `interface_adapter/<feature>` | 12 | 7 | 5 |
| `closeai/adapters/presenters` | `interface_adapter/<feature>` | 6 | 2 | 4 |
| `closeai/adapters/viewmodels` | `interface_adapter/<feature>` | 18 | 3 | 15 |
| `closeai/adapters/gateways` | `interface_adapter/<feature>` | 2 | 2 | 0 |
| `closeai/adapters/views` | `views` | 17 | 1 | 16 |
| `closeai/domain/entities` | `entity` | 5 | 0 | 5 |
| `closeai/domain/valueobjects` | `entity` | 6 | 0 | 6 |
| `closeai/infrastructure/persistence` | `database` | 3 | 0 | 3 |
| `closeai/infrastructure/{routing,weather,places,mock}` | `database` or `data_access` | 10 | 0 | 10 |
| `closeai/infrastructure/web` | `app` or drivers | 1 | 0 | 1 |
| `closeai` (`AppBuilder`,`Main`,`DemoSeeding`) | `app` | 3 | 0 | 3 |
| **Total mapped** | | **161** | **54** | **107** |

Plus all **57 test files**, whose packages mirror production.

### Shared and teammate-owned files that must move together

Everything outside `closeai/application/autoschedule/**`. Named by owner:

- **Raashid** — `AppBuilder`, `Main`, `AppContainer`, `OsrmDistanceService`,
  `InMemoryTripRepository`, `InMemoryItineraryDataAccessObject`, `ApiController`,
  `JsonPresenter`, `JsonRequest`, `StaticFileHandler`, the `TripSetup*` and
  `EditItinerary*` classes.
- **Shiyuan** — `DayPlanPanel`, `DayPlanState`, `CalendarPanel`, `CalendarViewModel`,
  `MapPanel`, `GalleryPanel`, `StaticTileLoader`, `HeaderPanel`, `OverviewPanel`,
  `NewItineraryDialog`, the `ShareTrip*` classes, `OpenMeteoWeatherService` and its DTOs.
- **Alex** — `ActivityDiscoveryController`, `BookmarkController`, `ManualPlanController`,
  `ActivityDiscoveryPresenter`, `ManualPlanPresenter`, `SearchPanel`, `BookmarksPanel`,
  `SearchState`, `NominatimPlacesService`.
- **Shared / unattributed** — `Trip`, `Activity`, `ScheduledEvent`, `User`,
  `WeatherWarning`, all of `domain/valueobjects`, all of `application/ports`, and the
  legacy `application/usecases` set.

**Two thirds of the affected files are not Emily's.** Under the brief's own classification
this is **Case 3 — repository-wide team migration**, requiring team approval.

### Why a partial move would be worse

Moving only `application/autoschedule` produces a tree containing both
`use_case/autoschedule/` and `closeai/**`. That matches neither the current layout nor the
mandated one, satisfies no reading of the requirement, and turns every subsequent teammate
merge into a conflict. **No packages were moved.**

## 7. Questions that still need an instructor answer

1. Does the announcement **supersede** @348 and @354/@355, or describe the layout CAVE needs
   in order to run?
2. Is a root package such as `closeai` permitted, or must packages sit directly under
   `src/main/java/`?
3. `views` or `view`?
4. `database`, `data_access`, or both?
5. How does CAVE identify one use case — folder, `*InputBoundary`, `*Interactor`, or
   convention?
6. Where do **gateway interfaces owned by a use case** belong: in that use case's folder, or
   shared?
7. Where do **gateway adapter implementations** belong: `interface_adapter/` or `database/`?
8. Do the legacy `application/usecases` classes each need their own folder, including ones
   no longer wired into the UI?
9. How does CAVE report violations, and which are the known false positives from @355?
10. Is a repository-wide rename expected in the final week of a group project, and does it
    affect grading if not done?

## 8. Submission risk

**Exact CAVE package-name compliance is an open team-level risk.** It is not Emily's alone
to close: 107 of 161 mapped files belong to other members, and Alex and Raashid both pushed
today.

Mitigating it: the substance CAVE checks — dependency direction — is verified and passing,
and the current organisation is consistent and documented, which is what @348 asks for. If
the announcement is binding on package *names*, the tree does not comply and one coordinated
migration is required.

## 9. The correct workflow for closing this

A repository-wide rename must not happen on a personal feature branch.

1. Finish each feature on its own branch.
2. Create a **team integration branch** from the latest `main`.
3. Merge every completed team branch into it.
4. Stabilise production wiring; get the full suite green.
5. **Run CAVE against the entire integrated `src` tree** — not against one feature.
6. If CAVE requires exact package names, perform **one coordinated repository-wide
   migration** on that integration branch, with the team present.
7. Rerun CAVE, tests, Checkstyle and coverage.
8. Only then merge the verified integration branch into `main`.

Doing the migration on `feature/emily-autoschedule` instead would rewrite 107 teammate files
inside one person's branch, guarantee conflicts for everyone still pushing, and still leave
CAVE unrun against the complete project.

## 10. Status

- Packages: **unchanged**. No migration committed.
- Import-based CA audit: **passes all nine checked rules, 0 inward violations**.
- CAVE itself: **not run**; no compliance claimed in either direction.
- Action: put §7 to Sajad or Pan, then follow §9 as a team.
