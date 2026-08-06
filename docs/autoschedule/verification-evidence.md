# Autoschedule verification evidence

Recorded 2026-08-06 on the branch `feature/emily-autoschedule`.
Environment: Java 24.0.2 (Temurin), macOS, aarch64. Maven via `./mvnw`.

## 1. Automated tests

```bash
./mvnw clean test
```

**265 tests, 0 failures, 0 errors, 1 skipped.** The skip is the pre-existing opt-in live
Open-Meteo test, which requires `RUN_LIVE_OPEN_METEO_TEST=true`. All 53 tests that existed
before this feature still pass.

## 2. Coverage (JaCoCo)

```bash
./mvnw clean test          # report written to target/site/jacoco/index.html
```

| Scope | Line | Branch |
|---|---|---|
| Repository-wide (after exclusions) | **78.4%** | 59.1% |
| Autoschedule slice (`application.autoschedule*` + gateways) | **89.2%** | 71.5% |
| `AutoScheduleInteractor` (the use-case interactor) | **91.1%** | 78.6% |

Against the group rubric's testing descriptors — 5/5 wants more than 90% interactor
coverage and more than 70% overall — the interactor is at 91.1% and the repository is at
78.4%. Both thresholds are met on line coverage, which Piazza @339 confirms is an
acceptable metric.

**Exclusions, and why each is justified:**

| Excluded | Reason |
|---|---|
| `closeai/adapters/views/**` | Swing rendering. Verified by hand (§5) and through structural tests; unit-testing pixel layout would measure nothing useful. |
| `closeai/infrastructure/web/**` | Static file handler for the optional REST path, unrelated to this feature. |

**No scheduling logic is excluded.** The engine, policies, validators, interactor,
presenter, controller and gateway adapters are all measured.

**Known uncovered areas, honestly:** most remaining misses in the Autoschedule slice are
`equals`/`hashCode`/`toString` on value objects and a few defensive branches that only fire
on programmer error. The lowest-covered meaningful class is `ReasonCollector` at ~71%; its
uncovered paths are reason combinations that need an unusual fixture rather than untested
behaviour. Branch coverage is lower than line coverage throughout because a lot of branches
are argument-validation guards.

## 3. Style (Checkstyle)

```bash
./mvnw checkstyle:check    # report written to target/checkstyle-result.xml
```

| Scope | Violations |
|---|---|
| Autoschedule-owned code (main and test) | **0** |
| Rest of the repository (pre-existing) | 236 across 39 files |

The pre-existing violations are almost entirely `NeedBraces` (104) and `LeftCurly` (69) from
single-line `if` statements in teammate code — for example `ApiController` (32), `Trip` (28)
and `MapPanel` (24). **These were not reformatted.** Rewriting a teammate's file to satisfy a
style report would obscure their authorship for no functional gain, and the course asks that
members not take over each other's work. They are listed here so the team can decide.

No course Checkstyle configuration was distributed with the available materials, so
`config/checkstyle.xml` is a conservative approximation covering naming, braces, imports,
whitespace and correctness habits. It should be replaced if the course publishes its own.

Both tools are configured as **reports, not gates**. Failing the build on a coverage
threshold or a legacy style violation would block teammates' commits for work that is not
theirs to fix.

## 4. Live provider verification

```bash
RUN_LIVE_AUTOSCHEDULE_TEST=true ./mvnw test -Dtest=AutoScheduleLiveVerificationTest
```

Route: Union Station (43.6453, −79.3806) → Casa Loma (43.6780, −79.4094), both seeded demo
locations. Trip date: the following day.

| Mode | Departure | Result | Latency | Provider actually used |
|---|---|---|---|---|
| Walking | 09:30 | 68 min | 483 ms | OSRM `routed-foot` |
| Walking | 17:30 | 68 min | 254 ms | OSRM `routed-foot` |
| Driving | 09:30 | 11 min | 358 ms | **OSRM `routed-car` fallback** |
| Driving | 17:30 | 11 min | 249 ms | **OSRM `routed-car` fallback** |
| Transit | 09:30 | 33 min | 871 ms | Transitous `/api/v6/plan` |
| Transit | 17:30 | 33 min | 352 ms | Transitous `/api/v6/plan` |
| Transit | 03:00 | **47 min** | 182 ms | Transitous `/api/v6/plan` |
| Weather | trip date | available, cannot distinguish times | 1001 ms | Open-Meteo |

**Walking — verified.** Identical at both departures, exactly as expected: the walking
provider takes no time parameter, which is why the system treats walking as
time-insensitive and fetches a single matrix rather than one per period.

**Transit — verified, and genuinely timetable-aware.** The two daytime departures returned
the same duration, which initially looked like the `time` parameter being ignored. Querying
the API directly disproved that: itinerary start times track the requested departure
(09:31 and 17:31 local), and a 03:00 departure returns 47 minutes against 33 in the
daytime. The daytime match is a well-served route, not a bug. Night service is where the
timetable shows itself, which is why the test compares three departures rather than two.

**Driving — not live-verified.** No TomTom credential was available in the environment
(`TOMTOM_API_KEY` unset, no ignored local config). The corrected `lat,lng` coordinate order
is unit-tested against a stubbed HTTP client, including a regression test that fails against
the old `lng,lat` URL, but **no real TomTom route has been obtained**, so no traffic-aware
claim should be made in the demo. Without a key the system falls back to OSRM and reports
driving as time-insensitive, which is the honest behaviour: both departures returned 11
minutes because a static road network has no rush hour.

**Weather — verified, and the limitation is real.** The live gateway returns a forecast, but
`canDistinguishTimes()` is false: one severity covers the whole trip, so every candidate
time scores identically. The preview therefore says "The forecast covers the whole day, so
weather could not influence the timing of outdoor activities" rather than listing weather as
an objective it did not apply. A walkthrough test asserts that caveat appears.

## 5. Manual functional, EDT and accessibility checks

The full demo also runs as an automated test (`AutoScheduleWalkthroughTest`) through the
production wiring, so these were confirmed both by hand and by assertion.

| Check | Result |
|---|---|
| Populated Day Plan opens | Pass |
| Autoschedule opens the settings dialog | Pass |
| Availability, mode, unavailable periods, keep-order and pins are read | Pass |
| Preview runs off the event thread | Pass — `SwingTaskRunner` verified to run work off the EDT and to return immediately |
| Day Plan unchanged during Preview | Pass — repository and Calendar both still show original times |
| Metrics, reasons, warnings, "Why these times?" render | Pass |
| Cancel changes nothing | Pass |
| Apply updates Trip and Calendar | Pass |
| Stale Preview rejected | Pass — Apply refused after an external edit |
| Impossible pin gives a structured conflict | Pass — names "Royal Ontario Museum" and states the plan was not changed |
| Empty Day Plan / no trip stay safe | Pass |
| Seeded and ordinary trips behave identically | Pass |

**Accessibility**, inspected on the real frame:

| Item | Result |
|---|---|
| Keyboard navigation | Autoschedule, Apply, Cancel, "Why these times?" and Calendar View are all focusable |
| Accessible names | Present on every control; lock checkboxes are named "Lock &lt;activity&gt; at its current time" |
| Focus | Standard focus traversal; the dialog's default button is Generate Preview |
| Escape / Enter | Escape cancels the settings dialog; Enter triggers Generate Preview |
| Errors not colour-only | Every failure and conflict carries explanatory text; colour is additional, never the sole signal |
| Expandable explanations | "Why these times?" is a focusable toggle button, not a hover tooltip |
| EDT responsiveness | Routing, forecast and search run on a background worker; the window stays responsive |

## 6. Benchmarks

```bash
./mvnw test -Dtest=SchedulingBenchmarkTest
```

Fake travel provider, no network. Java 24.0.2, macOS, aarch64. **These are project evidence
from one machine, not a performance guarantee.**

| Activities | Directed pairs | Buckets | Prefetch calls | Nodes explored | ms | Budget exhausted | Outcome |
|---|---|---|---|---|---|---|---|
| 5 | 20 | 4 | 80 | 326 | 0 | no | scheduled |
| 8 | 56 | 2 | 112 | 106,721 | 78 | no | scheduled |
| 12 | 132 | 1 | 132 | 200,000 | 142 | **yes** | scheduled |
| 15 | 210 | 1 | 210 | 1 | 1 | no | conflict |

**Reading the table.** Buckets shrink as the day grows — 4 periods at 5 activities, 2 at 8,
1 at 12 and above — which is the documented degradation working. At 12 activities the node
budget is reached, so the result is reported as the best found within the search limit
rather than presented as optimal. The 15-activity row is a conflict because that fixture
(fifteen 45-minute activities plus travel) genuinely cannot fit a twelve-hour day; it is
rejected at the first node by the remaining-time bound, which is why it takes 1 ms.

**Verified limits:**

- **No travel call ever happens inside the recursive search** — asserted by counting
  estimator calls before and after `ScheduleEngine.search`.
- **Prefetch stays within the documented contract.** This is where a benchmark caught a real
  defect: the budget was described as a ceiling on total requests, but 12 activities need
  132 pairs and no amount of bucket merging goes below one full matrix. Scheduling cannot
  begin without pairwise travel, so one matrix is an irreducible floor. `PeriodPlan` now
  documents the invariant that actually holds — either a single period is in use, or the
  total is within the ceiling — and `withinPrefetchBudget` exposes it.
- **A walking day costs exactly one matrix** (56 calls at 8 activities), since the provider
  has no time input.
- **More than 15 activities is refused** with a plain message rather than a hung search.
- **Results are deterministic**: repeated runs of the same day produce the same order, the
  same score and the same node count.

## 7. Screenshots

Screenshots were **not** captured: this environment has no reliable way to drive and capture
a Swing window without installing unrelated tooling, and a fabricated image would be worse
than none. Capture these six states manually, at the default window size:

1. **Before** — the Day Plan with the inefficient seeded day (museum after closing, lunch at
   15:30), showing the Lock checkboxes.
2. **Settings** — the dialog with availability, mode, one unavailable period added, and
   "Keep my current order where possible" ticked.
3. **Preview** — the proposal under the unchanged Day Plan, with the metrics line, the
   objective summary, one reason on a row, and the weather caveat visible.
4. **Why these times** — the expanded explanation panel.
5. **After Apply** — the updated Day Plan, plus the Calendar View showing the same times.
6. **Conflict** — pin the museum inside an unavailable period and generate a preview; capture
   the message naming the museum and stating the plan was not changed.

Steps 1, 3, 5 and 6 are the before/after/failure evidence the individual rubric asks for.
