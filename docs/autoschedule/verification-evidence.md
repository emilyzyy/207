# Autoschedule verification evidence

Recorded 2026-08-06 on the branch `feature/emily-autoschedule`.
Environment: Java 24.0.2 (Temurin), macOS, aarch64. Maven via `./mvnw`.

## 1. Automated tests

```bash
./mvnw clean test
```

**297 tests, 0 failures, 0 errors, 6 skipped** (re-run 2026-08-06 after the weather-preference
work; the previous figure was 270). The six skips are all opt-in live tests that require
network access and an explicit environment variable: the pre-existing
`OpenMeteoWeatherServiceLiveTest` (1, needs `RUN_LIVE_OPEN_METEO_TEST=true`) and
`AutoScheduleLiveVerificationTest` (5, needs `RUN_LIVE_AUTOSCHEDULE_TEST=true`, results in
§4). Nothing in the ordinary suite touches the network. All 53 tests that existed before
this feature still pass.

## 2. Coverage (JaCoCo)

```bash
./mvnw clean test          # report written to target/site/jacoco/index.html
```

Figures below were reproduced from `target/site/jacoco/jacoco.csv` on the run above, not
carried over from the previous batch.

| Scope | Line | Branch |
|---|---|---|
| Repository-wide (after exclusions) | **79.1%** | 60.2% |
| Autoschedule slice (`application.autoschedule*` + gateways) | **90.4%** | 73.5% |
| `AutoScheduleInteractor` (the use-case interactor) | **92.3%** | 80.8% |

New in this batch: `WeatherOption` 100% line, `WeatherContextGateway` 100% line,
`SchedulingPreferences` 100% line, `AutoScheduleController` 94.6% line.

Against the group rubric's testing descriptors — 5/5 wants more than 90% interactor
coverage and more than 70% overall — the interactor is at 92.3% and the repository is at
79.1%. Both thresholds are met on line coverage, which Piazza @339 confirms is an
acceptable metric. The Autoschedule slice as a whole also reaches 90.4%.

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

| Scope | Files | Violations |
|---|---|---|
| Emily-owned production and test code | 99 | **0** |
| Shared files modified by Emily | 7 | **0** |
| Raashid's routing file carrying Emily's one-line fix | 1 | 3 — all blamed to his own commits |
| Unrelated teammate-owned files | 38 | 233 — not touched |
| **Repository total** | | **236 warnings, 0 errors** |

The tool reports "0 Checkstyle violations" because the configuration's severity is
`warning` and `violationSeverity` is `error`; the 236 warnings are in
`target/checkstyle-result.xml`. Full ownership breakdown, with the `git blame` evidence for
each of the three routing violations, is in **`docs/autoschedule/ownership.md`**.

The pre-existing violations are almost entirely `NeedBraces` (104) and `LeftCurly` (69) from
single-line `if` statements in teammate code — for example `ApiController` (32), `Trip` (28)
and `MapPanel` (24). **These were not reformatted.** Rewriting a teammate's file to satisfy a
style report would obscure their authorship for no functional gain, and the course asks that
members not take over each other's work. They are listed here so the team can decide.

**Configuration provenance.** Piazza @275 requires the project to follow the course
Checkstyle rules — a verified course requirement. No configuration file was distributed
with the available materials, and none appears in the 30 course PDFs, so
`config/checkstyle.xml` is a **project-defined approximation and engineering judgment, not
a verified course standard**. It covers naming, braces, imports, whitespace and correctness
habits. It should be replaced if the course publishes its own. No IDE Checkstyle extension
was installed; the Maven plugin produced every number here.

Both tools are configured as **reports, not gates**. Failing the build on a coverage
threshold or a legacy style violation would block teammates' commits for work that is not
theirs to fix.

## 4. Live provider verification

```bash
RUN_LIVE_AUTOSCHEDULE_TEST=true ./mvnw test -Dtest=AutoScheduleLiveVerificationTest
```

Route: Union Station (43.6453, −79.3806) → Casa Loma (43.6780, −79.4094), both seeded demo
locations. Trip date: the following day.

Re-run 2026-08-06 during Batch 5. Latencies are from that run.

| Mode | Departure | Result | Latency | Provider actually used |
|---|---|---|---|---|
| Walking | baseline | 68 min | 511 ms | OSRM `routed-foot` |
| Walking | 09:30 | 68 min | 346 ms | OSRM `routed-foot` |
| Walking | 17:30 | 68 min | 253 ms | OSRM `routed-foot` |
| Driving | 09:30 | 11 min | 355 ms | **OSRM `routed-car` fallback** |
| Driving | 17:30 | 11 min | 250 ms | **OSRM `routed-car` fallback** |
| Transit | 09:30 | 33 min | 1367 ms | Transitous `/api/v6/plan` |
| Transit | 17:30 | 33 min | 254 ms | Transitous `/api/v6/plan` |
| Transit | 03:00 | **47 min** | 202 ms | Transitous `/api/v6/plan` |
| Weather | trip date | `available=true`, `canDistinguishTimes=false` | 1020 ms | Open-Meteo |

**Walking — verified.** Identical at both departures, exactly as expected: the walking
provider takes no time parameter, which is why the system treats walking as
time-insensitive and fetches a single matrix rather than one per period.

**Transit — verified, and genuinely timetable-aware.** The two daytime departures returned
the same duration, which initially looked like the `time` parameter being ignored. Querying
the API directly disproved that: itinerary start times track the requested departure
(09:31 and 17:31 local), and a 03:00 departure returns 47 minutes against 33 in the
daytime. The daytime match is a well-served route, not a bug. Night service is where the
timetable shows itself, which is why the test compares three departures rather than two.

**Driving — still not live-verified, and the distinctions matter.** Batch 5 attempted this
again. `TOMTOM_API_KEY` was not present in the verification process, nor in any ancestor
process (checked without printing any value), so the live-driving portion was stopped and
the rest of the batch continued. Stated precisely:

| Claim | Status |
|---|---|
| A live TomTom request was made and verified | **No.** No key reached the process. |
| The corrected `lat,lng` order reaches the live service | **Not proven live.** Proven against a stubbed `HttpClient`, including a regression test that fails against the old `lng,lat` URL. |
| `departAt` is sent | **Not proven live.** Asserted on the stubbed URL. |
| A valid duration was returned by TomTom | **No.** The 11-minute figures came from the OSRM fallback. |
| A traffic-time difference between two departures was observed | **Not observed.** Both departures returned 11 minutes because a static road network has no rush hour. |
| Which provider actually answered | **Unprovable through the shared return type.** `DistanceService` returns a bare `int`; the "OSRM fallback" attribution above is inferred from the absent key, not reported by the call. |

**No traffic-aware claim should be made in the demo.** To verify it, relaunch with the key
present in the environment — see §8.

**Weather — verified, and it is what the new preference gate is built on.** The live gateway
returns a forecast, but `canDistinguishTimes()` is false: one severity covers the whole
trip, so every candidate time scores identically. Since Batch 5 that is not merely a caveat
but the mechanism — the "Consider weather" checkbox is disabled and unticked, and the dialog
shows "Hourly weather is not available for this trip date." If the traveller somehow asks
for weather anyway, the Interactor still finds the forecast coarse, contributes zero, warns
that "the forecast covers the whole day rather than each hour", and omits weather from the
applied-objectives list. Asserted through the production wiring by
`AutoScheduleWalkthroughTest`, and the enabled path by
`anHourlyGatewayWouldOfferThePreferenceWithNoOtherChange`.

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

**Captured.** Seven states are committed under `docs/autoschedule/screenshots/`. The
previous batch could not capture them; this one could, using Java's own
`Component.printAll(Graphics)` to paint the real production components offscreen into a
`BufferedImage`. No extra software was installed and no screen-recording permission was
needed. The components are built through `AppBuilder.buildOffline()` and driven by the real
Controller, so every state was reached by the production use case rather than staged.

| # | File | State |
|---|---|---|
| 1 | `01-before-day-plan.png` | The inefficient seeded day, with Lock checkboxes |
| 2 | `02-settings-weather-withheld.png` | Settings with "Consider weather" disabled, unticked, and the reason shown — the state that occurs today |
| 2b | `02b-settings-weather-available.png` | The same dialog with an hourly forecast: enabled and ticked by default |
| 3 | `03-preview.png` | The proposal under the unchanged Day Plan, with metrics, reasons and the lock honoured |
| 4 | `04-why-these-times.png` | The expanded explanation panel |
| 5 | `05-after-apply.png` | The updated Day Plan |
| 6 | `06-conflict.png` | The named conflict, with the plan untouched |

**Two honest limitations.** These show the Day Plan panel and the settings dialog, not the
surrounding application window — no title bar, tab strip or native chrome — and offscreen
font rendering can differ very slightly from an on-screen window. For a slide they are
accurate; if a whole-window shot is wanted, capture 1, 3, 5 and 6 by hand. State-by-state
manual steps are in **`docs/autoschedule/screenshot-checklist.md`**.

Not captured automatically: the **Calendar View** after Apply, which needs the full frame.
Capture that one by hand alongside shot 5.

**Correction to an earlier claim.** This document previously said "Steps 1, 3, 5 and 6 of
that list are the before/after/failure evidence the individual rubric asks for." Checked
against the course audit, only **1 and 5** are supported: the Individual Presentation
Rubric's Required Elements are *before and after Views, Interactor code, and the full
use-case class diagram* (S-017). It names neither an intermediate preview state nor a
failure screenshot. Shots 3 and 6 remain worth having — 3 supports Use-case Explanation and
6 supports the group rubric's accessibility descriptor — but attributing them to the
individual rubric was **unverified**. See the checklist document for the full mapping.

## 8. Verifying live TomTom driving

The live-driving check is the one item Batch 5 could not complete, because no
`TOMTOM_API_KEY` was present in the environment. To finish it, launch the tooling with the
key already exported, then run the live test.

```bash
# Reads the key without echoing it and without leaving it in shell history.
read -rs TOMTOM_API_KEY && export TOMTOM_API_KEY
# then, in that same shell:
RUN_LIVE_AUTOSCHEDULE_TEST=true ./mvnw test -Dtest=AutoScheduleLiveVerificationTest
```

The test prints `[live] TomTom key present: true` when the key reaches the process, and the
two driving rows should then differ between a 09:30 and a 17:30 departure if traffic data is
genuinely being applied. **If they do not differ, say so** — a matching pair is evidence
against the traffic-aware claim, not for it.

Even with a key, note the standing limitation: `DistanceService` returns a bare `int`, so
the run still cannot prove *which* provider answered. Route provenance stays UNKNOWN until
that shared return type carries a quality signal, which is Raashid's to decide.

**Never** paste the key into a command line, a source file, the README, a log, or a
screenshot, and never commit a file containing it.
