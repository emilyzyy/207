# Autoschedule ownership and Checkstyle report

Recorded 2026-08-06 on `feature/emily-autoschedule`, base `origin/main` = `b6ab177`.
Ownership below is derived from `git diff --diff-filter=A/M origin/main HEAD` and
`git blame`, not from memory. **Modifying a shared file does not make that file
Emily-owned**, and the table is arranged so that distinction stays visible.

## 1. Checkstyle configuration provenance

| Question | Answer |
|---|---|
| Command | `./mvnw checkstyle:check` |
| Configuration | `config/checkstyle.xml` |
| Official course config? | **No — project-defined. Engineering judgment, not a verified course standard.** |
| Plugin | `maven-checkstyle-plugin` 3.6.0, declared in `pom.xml`; Maven resolves the tool itself |
| Enforcement | Report only (`failOnViolation=false`, `violationSeverity=error`); the config's own severity is `warning` |

**Why it is labelled engineering judgment.** Piazza @275 (2026-06-28, Pan Chen) states the
group project must follow the course Checkstyle rules used in labs and homework. That is a
verified course requirement. What could not be verified is a distributed configuration
file: the local course audit records "No Checkstyle config/plugin/documented command found"
(`project-requirements.md` §95, §118) and "The current repository does not configure a
Checkstyle Maven plugin or checked report" (`course-source-verification.md` §58). The 30
course PDFs in `course-files/` contain no `checkstyle.xml`. So the requirement is real and
the file satisfying it is ours.

`config/checkstyle.xml` therefore enforces the checks the lectures and code samples lean on
— naming, braces, imports, whitespace, and a short list of correctness habits — and omits
stylistic checks (Javadoc on every member, magic numbers, final parameters) that would
flag large amounts of existing teammate code without improving it. **If the course
publishes its own configuration, replace this file with it.** No conflict with an official
standard has been found, because no official standard was found to conflict with.

No IntelliJ or VS Code Checkstyle extension was installed. The IDE plugin is optional
editor integration; the Maven plugin is the project quality tool, and it is what produced
every number below.

## 2. Ownership classification

| Class | Files | Checkstyle violations |
|---|---|---|
| A. Emily-owned production | 64 | **0** |
| B. Emily-owned tests | 31 | **0** |
| C. Emily-owned non-source (config, docs) | 4 | **0** |
| D. Shared files modified by Emily | 7 | **0** |
| E. Raashid-owned routing file with Emily's narrow fix | 1 | 3 — *all pre-existing, none Emily's* |
| F. Unrelated teammate-owned files | 38 | 233 — **not touched** |
| **Repository total** | | **236 warnings, 0 errors** |

### A–C. Emily-owned (99 files, 0 violations)

Everything under `src/main/java/closeai/application/autoschedule/` (including `engine/` and
`policy/`), plus `adapters/controllers/AutoSchedule*`, `TaskRunner`, `SwingTaskRunner`,
`adapters/presenters/AutoSchedulePresenter`, `adapters/viewmodels/AutoScheduleStatus`,
`PreviewRowView`, `PreviewMetricsView`, `adapters/views/AutoScheduleSettingsDialog`,
`adapters/gateways/DistanceServiceTravelTimeEstimator`, `WeatherServiceContextGateway`, and
the 31 matching test classes. Non-source: `config/checkstyle.xml` and the three
`docs/autoschedule/` documents.

This batch added `WeatherOption`, `WeatherPreferenceTest` and
`AutoScheduleWeatherCheckBoxTest` to that set. All three are clean.

### D. Shared files modified by Emily (7 files, 0 violations)

These belong to teammates. Emily changed them for a stated reason and nothing wider.

| File | Original author | Emily's change | +/− |
|---|---|---|---|
| `README.md` | Shiyuan | Additive Day Plan section; one heading disambiguated | +133/−1 |
| `pom.xml` | Raashid | Added JaCoCo and Checkstyle plugins | +52/−0 |
| `AppBuilder.java` | Raashid | Wires the one Autoschedule path | +47/−20 |
| `DayPlanState.java` | Shiyuan | Additive state only | +119/−1 |
| `DayPlanPanel.java` | Shiyuan | Rewritten around Autoschedule (agreed feature owner) | +291/−41 |
| `SwingApplicationIntegrationTest.java` | Raashid | Button rename | +11/−8 |
| `SwingPanelStructureTest.java` | Raashid | Button rename; test double gained `weatherOptionFor` | +25/−19 |

None carries a Checkstyle violation, before or after this batch.

### E. Raashid-owned routing file with Emily's narrow fix

`src/main/java/closeai/infrastructure/routing/OsrmDistanceService.java` — **3 violations,
none introduced by Emily.** `git blame` places all three in Raashid's own commits:

| Line | Check | Introduced by | Commit |
|---|---|---|---|
| 40 | LineLength (116) | Raashid | `b6ab177` "time-sensitive DistanceService" |
| 61 | NeedBraces | Raashid | `b6ab177` "time-sensitive DistanceService" |
| 200 | LineLength (112) | Raashid | `7c494c7` "Replace MockDistanceService with OsrmDistanceService" |

Emily's entire change to this file is **one line plus one comment** — TomTom documents
`latitude,longitude` and was receiving `longitude,latitude`, so every driving request
failed and silently fell back to OSRM. Contract, fallback and key handling are untouched.
Per the batch rule, Raashid's code was **not** rewritten beyond that fix, and these three
violations are left for him.

### F. Unrelated teammate-owned files (38 files, 233 violations)

**Deliberately not reformatted.** Rewriting a teammate's file to satisfy a style report
would obscure their authorship for no functional gain, and the course asks that members not
take over each other's assigned code (Project Requirements). Listed so the team can decide.

Largest: `ApiController` (32), `Trip` (28), `MapPanel` (24), `OpenMeteoWeatherService` (15),
`NominatimPlacesService` (13), `AutoScheduleTripUseCase` (12), `ScheduledEvent` (10),
`Activity` (10).

Repository-wide breakdown by check:

| Check | Count |
|---|---|
| NeedBraces | 104 |
| LeftCurly | 69 |
| LineLength | 28 |
| OneStatementPerLine | 20 |
| MultipleVariableDeclarations | 6 |
| UnusedImports | 4 |
| RightCurly | 2 |
| WhitespaceAround | 2 |
| AvoidStarImport | 1 |

Almost all of it is single-line `if` statements in teammate code — a formatting habit, not
a defect.

## 3. Results, before and after this batch

| Scope | Before Batch 5 | After Batch 5 |
|---|---|---|
| Emily-owned production | 0 | **0** |
| Emily-owned tests | 0 | **0** |
| Shared files modified by Emily | 0 | **0** |
| Raashid's routing file | 3 (all his) | 3 (all his) |
| Unrelated teammate files | 233 | 233 |
| **Total** | 236 | **236** |

**Files changed by Checkstyle in this batch: none.** No violation existed in Emily-owned
code to fix, before or after the weather work, so no fix was required and none was invented.
The count is unchanged because nothing teammate-owned was reformatted, which was the point.

## 4. Standing item for the team

Whether to adopt `config/checkstyle.xml` or replace it with a course-published
configuration, and who clears the 233 pre-existing violations, are team decisions. Neither
blocks this feature: both tools are reports rather than build gates, so nobody's commit is
failed by them.
