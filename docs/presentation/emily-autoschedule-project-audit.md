# Emily's Autoschedule project audit — learn it, present it, defend it

Audited 2026-08-08 at commit `7f3293a` on `feature/autoschedule-ui-polish` (upstream in
sync, `git pull --ff-only` → already up to date; 471 tests pass on this commit). The
branch is **12 commits ahead of `origin/main` and 2 behind** — the 2 are Dennis's new
"George" trip assistant (PR #18), which does not touch Autoschedule. Nothing was merged
during this audit.

---

## 1. The project in plain language

**CloseAI is a one-day trip planner.** You tell it a city and a date; it finds real
places (from OpenStreetMap), shows them on a map with weather, and lets you build a Day
Plan — a list of timed activities. Autoschedule then rearranges that day for you:
less walking, no missed opening hours, lunch at lunchtime, outdoor stuff in daylight
and decent weather. Finally you can view it as a calendar and share it.

**The user journey** (also the demo story): create a trip → discover and add
activities → Autoschedule the day (pinning anything that must not move) → check
weather → share.

### Who owns what (verified from git authorship on current `main`)

| Area | Owner | Their star classes |
|---|---|---|
| Places, routing, maps, trip setup | **Raashid** (`bobbuildersbytes`) | `NominatimPlacesService`, `OsrmDistanceService`, `MapPanel`, calendar date picker |
| Weather + hourly forecast popup + George assistant | **Dennis (Shiyuan)** | `OpenMeteoWeatherService`, `HourlyWeatherPanel`, `TripAssistant*` |
| Activity discovery, bookmarks, add/edit plan | **Alex** (`alex-zzz1`) | `ActivityDiscoveryController`, `BookmarkController`, `ManualPlanController` |
| Persistence / accounts / sharing | **Bianca** | Supabase persistence classes; `bianca-friends` branch in progress |
| **Autoschedule — everything below** | **Emily** | `AutoScheduleInteractor`, the engine, the policies, the polished Day Plan UI |

### How the features connect

Everything meets at two places: the **entities** (`Trip`, `ScheduledEvent`,
`Activity`) and the **`DayPlanViewModel`**. Alex's add-to-plan writes events into a
`Trip`; Autoschedule reads that same `Trip` and proposes a better arrangement;
Dennis's forecast popup and the Day Plan render from the same `DayPlanViewModel`, which
is why they stay in sync. External services: **Nominatim + Overpass** (places, opening
hours), **OSRM / TomTom / Transitous** (walking / driving / transit times),
**Open-Meteo** (hourly weather), **Supabase** (persistence). Every one of them sits
behind an interface owned by an inner layer — no use case ever imports HTTP or Swing.

### Package organization = the Clean Architecture layers

```
closeai.domain.*            Entities & value objects   (Trip, Activity, OpeningHours)
closeai.application.*       Use cases + the interfaces they own
closeai.adapters.*          Controllers, Presenters, ViewModels, Swing views
closeai.infrastructure.*    The real world: HTTP services, persistence, mocks
closeai.AppBuilder          Composition root — the only place that news everything up
```

---

## 2. Autoschedule, from the user's side

**Problem:** people pick *what* they want to do but are bad at deciding *when* —
they backtrack across the city, put lunch at 3:30pm, and end up in a park after dark
in the rain.

**User story:** *As a traveller who has already chosen my Day Plan activities, I want
CloseAI to reorder and retime exactly those activities — respecting opening hours, my
availability, and anything I've pinned — so I get a feasible, lower-travel day without
losing any of my choices.*

**What the user sees and does:**

- **Before:** the Day Plan lists their activities in the order they added them, each
  with a 12-hour time, a padlock, Edit/Remove, and an hourly weather line.
- **Locks:** clicking the padlock pins an activity to its exact time (e.g. a timed
  museum ticket). Locked rows tint blue with a closed padlock.
- **Settings dialog:** available from/until (prefilled from the trip), transport mode,
  optional "times I'm not available" (nothing—including travel—is scheduled in them),
  "keep my current order where possible", and "consider weather" (on by default now
  that hourly forecasts exist; disabled *with a stated reason* when the forecast can't
  tell hours apart).
- **Preview:** the proposal appears *below* the unchanged plan — nothing has been
  saved. It shows before/after travel and waiting minutes, `Locked`/`Moved` badges,
  indented travel rows, a "Why these times?" expandable panel of per-row reasons, and
  a **Schedule Improvements** stack of cards ("63 min of waiting removed", "Meal moved
  to a better time"…). Every card is computed by comparing the old and new placement —
  nothing is claimed that didn't actually improve.
- **Apply / Cancel:** Apply saves it (refused if the plan changed since the preview —
  a fingerprint check); Cancel discards it. Preview never writes anything.
- **Failure:** an impossible day (e.g. a pin inside an unavailable period, or a venue
  closed that date) produces a *named* conflict — "Royal Ontario Museum is locked to a
  time you marked as unavailable. Your Day Plan was not changed." — never a partial or
  corrupted plan.

## The internal flow, class by class

One click travels this path (all files under `src/main/java/closeai/`):

| Step | Class (file) | Layer | Responsibility |
|---|---|---|---|
| 1 | `DayPlanPanel`, `AutoScheduleSettingsDialog` (adapters/views) | Frameworks | Draw the plan, collect settings. Zero scheduling logic — buttons delegate. |
| 2 | `AutoScheduleController` (adapters/controllers) | Interface Adapters | Translates dialog values into `AutoScheduleInputData`; calls the Input Boundary. Runs work off the UI thread via `TaskRunner`. |
| 3 | `AutoScheduleInputBoundary` + `AutoScheduleInputData` (application/autoschedule) | Application | The use case's public contract. The Controller knows this interface, never the concrete Interactor. |
| 4 | **`AutoScheduleInteractor`** | Application | The use case. Loads the `Trip`, validates, builds tasks, prefetches travel, runs the engine, and reports through the Output Boundary. |
| 5 | `ScheduleEngine` + `ActivityPlacer` + policies (application/autoschedule/engine, /policy) | Application | Pure scheduling logic — no I/O, no Swing, no HTTP. Testable exhaustively. |
| 6 | `TripRepository`, `TravelTimeEstimator`, `WeatherContextGateway` | Application-owned **interfaces** | What the use case needs from the outside world, stated abstractly. |
| 7 | `AutoScheduleOutputBoundary` + three OutputData classes | Application | How results leave: preview, applied, or conflict — as data, not exceptions. |
| 8 | `AutoSchedulePresenter` (adapters/presenters) | Interface Adapters | Turns OutputData into display-ready `DayPlanState` (12-hour strings, reason sentences, improvement cards). |
| 9 | `DayPlanViewModel` → `DayPlanState` (adapters/viewmodels) | Interface Adapters | Observable state. Fires a property change… |
| 10 | …and `DayPlanPanel` redraws. | Frameworks | The circle closes. |

**Why the layering matters (say this in one sentence):** *runtime control flows
outward — Interactor calls repository, calls presenter — but every source dependency
points inward, because the outer classes implement interfaces the inner layer owns.*
That inversion is what lets the whole use case run in tests with fakes and no window.

---

## 3. How a schedule actually gets produced

Step by step, all current code:

1. **Tasks.** `buildTasks` turns each Day Plan activity into a `ScheduleTask`: id,
   duration (from the event, so manual edits survive), lock window if pinned, and its
   **opening windows resolved for the trip's own date** — the weekday is decided here,
   so the engine never needs a calendar.
2. **Validation first.** `ProblemValidator` rejects impossible inputs before any
   search: a lock outside availability, outside its venue's opening hours (including
   spanning a lunch closure), inside an unavailable period, or two locks overlapping.
   Each rejection is a typed `ScheduleConflict` naming the activity.
3. **Travel prefetch.** `TravelMatrixPrefetcher` fetches every pairwise travel time
   through `TravelTimeEstimator` *before* the search, per departure period (morning /
   afternoon / evening, since driving times vary). The recursion never touches the
   network.
4. **Search.** `ScheduleEngine` explores orderings of the movable tasks
   (branch-and-bound over permutations with pruning; `GreedyPlanner` seeds an initial
   solution; `SearchBudget` caps nodes). For each ordering, `ActivityPlacer` places
   activities one by one at the earliest lawful time.
5. **Hard constraints** (a placement violating any is simply not a schedule):
   - activity fits **entirely inside one opening interval** — a venue open 9–12 and
     14–18 offers two shifts, and a visit cannot straddle the closure;
   - unknown hours are *not* treated as closed — the activity falls back to its coarse
     single window (most OSM places publish no hours; refusing them would make the
     feature useless);
   - a venue on record as **closed that date** is unschedulable, reported by name;
   - locked activities stay at their exact time; travel to them must still work;
   - nothing overlaps; nothing (travel included) sits in an unavailable period —
     the traveller waits, then departs;
   - everything inside the availability window; durations preserved exactly.
   - Travel *is* allowed outside venue hours — walking to a museum before it opens is
     how you get there.
6. **Soft preferences** — the `SoftPolicy` list scores each placement in penalty
   minutes: `WeatherSuitabilityPolicy` (outdoor activities in bad forecast hours),
   `MealWindowPolicy` (FOOD outside lunch/dinner windows), `DaylightPolicy` (outdoor
   after dark). All penalties are **capped**, so a soft gain can never justify a huge
   detour, and a small capped charge applies for breaking the user's order when they
   asked to keep it.
7. **Score & tie-break.** `ScheduleScore` = travel + avoidable idle + capped penalties
   + order charge, with a deterministic string tie-break — same inputs, same schedule,
   on every machine.
8. **Exact-time refinement.** The winning plan is rebuilt with departure-time-accurate
   travel estimates (`SchedulePlanRebuilder`); `PlanValidator` re-checks every hard
   rule on the final artifact.
9. **Explanation.** `ReasonCollector` attaches per-row reason codes (opens later,
   closing soon, avoids your unavailable period…); `ScheduleImprovementFinder`
   re-runs *the same policy objects* against each activity's original time and only
   claims an improvement when the penalty actually dropped; `ScheduleMetrics` computes
   honest before-travel by charging the original order its implied journeys.
10. **Preview vs Apply.** Preview presents all of this without writing.
    Apply re-loads the trip, checks the `ScheduleFingerprint` (stale preview →
    refused), and saves through `Trip.replaceSchedule`, whose own invariants
    (sorted, non-overlapping, in-window) are the last line of defence.

**Division of labour in one line each:** the *Interactor* coordinates and owns the
policy of the use case; the *engine* decides placements and is a pure function; the
*adapters* supply travel/weather/persistence facts; the *UI* only displays state.

---

## 4. Team connections and the DIP example

The Interactor needs two things it must not know the details of:

| Port (Emily owns) | Production adapter | Behind it | Test fake |
|---|---|---|---|
| `TravelTimeEstimator` | `DistanceServiceTravelTimeEstimator` | Raashid's `DistanceService` → OSRM / TomTom / Transitous | `FakeTravelTimeEstimator` |
| `WeatherContextGateway` | `WeatherServiceContextGateway` | Dennis's `OpenMeteoWeatherService` | `FakeWeatherContextGateway` |

**The payoff story (memorize this):** when Dennis's hourly forecast landed, the
"Consider weather" preference went from permanently disabled to enabled-by-default
with a **one-adapter change — zero edits to the engine, Interactor, Controller, or
dialog**. The gateway's `WeatherContext` finally answered `canDistinguishTimes() =
true`, and everything above it already knew what to do. That is dependency inversion
paying rent, provable from git history.

Opening hours are the same shape at the data level: Raashid's `NominatimPlacesService`
reads the OSM `opening_hours` tag (keeping his coarse whole-week window);
`OpeningHoursParser` (infrastructure — provider syntax stays out of the use case)
normalises it into per-weekday `OpeningHours`; the Interactor resolves the trip's
weekday; the engine just sees plain `TimeWindow`s.

---

## 5. The Strategy pattern, honestly

The lecture's definition (14-DesignPatterns p.31): *high-level logic is the same
except for which algorithm solves part of the task — decouple the class from the
algorithms it may use.*

- **Interface:** `SoftPolicy` — `penaltyMinutes(placement, context)` (+ a reason).
- **Three real, interchangeable algorithms:** `WeatherSuitabilityPolicy`,
  `MealWindowPolicy`, `DaylightPolicy`.
- **Assembly:** `AppBuilder` registers the list; `SchedulingPreferences.builtIn(...)`
  carries the active set into the engine.
- **Evaluation:** the engine sums penalties over whatever list it was given. It has no
  idea weather exists.

**Contrast to say aloud:** the alternative was an `if (isOutdoor && badWeather)…
else if (isFood && notLunchtime)…` block inside the engine — every new objective would
mean editing and re-verifying the search itself. With Strategy, a new objective is one
new class and one registration line; the engine is closed against that change.

**Why it's genuinely Strategy, not "an interface exists":** three production
implementations doing real work, selected and composed at runtime (weather joins the
list only when usable), with the same high-level algorithm around them. Bonus link the
lecture itself makes (p.35): this is OCP and DIP in action — say that sentence and
you've stitched the pattern slide to the SOLID slide.

---

## 6. Testing and limitations

**Measured on this commit:** 471 tests, 0 failures, 9 skipped (all opt-in live-network
tests). Coverage (JaCoCo, measured this week): repository **74.8% line / 58.3%
branch**; **`AutoScheduleInteractor` 93.5% line**; autoschedule slice ~91.7% line;
`OpeningHours` 100/100. Against the rubric's testing thresholds: interactor >90% ✓,
overall >70% ✓ — the 5/5 band, with the caveat that the *overall* margin moves
whenever anyone merges untested code.

The tests worth naming:

- **`BruteForceCrossCheckTest`** — enumerates every possible order on ~100 randomized
  days and requires the pruned search to match. This is how you *know* the pruning
  never discards the best answer (and it caught a real lower-bound bug when written).
- **`RealOpeningHoursTest`** (16) — hard-constraint behaviour: exact boundaries, a
  visit one minute too long refused (not truncated), the lunch-closure straddle ban,
  closed-day named conflicts, overnight splits, unknown≠closed.
- **`OpeningHoursProductionWiringTest`** (6) — end-to-end from a stubbed Overpass HTTP
  response through Raashid's adapter into a scheduled preview; includes the case where
  the coarse flattened window says 23:00 but Wednesday truly closes at 17:00 — the
  schedule believes the weekday.
- **`LockValidationTest`**, **`UnavailableWindowTest`** — pins and blocked periods.
- **`AutoscheduleDemoImprovementsTest`** — the exact six improvement cards the seeded
  demo produces, computed by the real Interactor.
- **UI:** `AutoschedulePolishedUiTest`, `HourlyWeatherAndAutoscheduleTest` (Dennis's
  popup and the Day Plan sharing one ViewModel), plus integration tests through
  `AppBuilder`.

**Limitations to state, not hide:** one day at a time, one transport mode per run;
travel confidence reported as unknown (the shared `DistanceService` can't distinguish
a routed result from its distance fallback); transit times use the JVM time zone;
TomTom driving never live-verified (no key has ever been present; OSRM fallback is
what runs); opening-hours parser handles the common tag shapes only — month ranges,
`sunrise-sunset`, holiday calendars degrade to "unknown = permissive"; the seeded demo
fixtures publish no hours at all, so the demo can't show a closed-day conflict without
a live place.

---

## 7. Emily's individual evidence — the strongest current material

### Demo (Part A)

- **Before View:** the seeded demo day (`AutoscheduleDemoTrip`) — five real Toronto
  activities in a careless arrangement: museum pinned 11:00, lunch at **3:30pm**,
  High Park **7:30pm** (dark + heavy rain in the seeded forecast), geographic
  zig-zag. Deterministic and offline — it cannot fail on stage.
- **Action:** lock the museum (padlock clicks shut, row tints) → Autoschedule →
  accept defaults → Generate Preview.
- **After View:** proposal under the untouched plan — museum still 11:00 `Locked`,
  lunch moved to 12:37, High Park in the afternoon sun, travel rows visible, six
  improvement cards, before/after minutes.
- **Failure beat (optional, 10s):** add unavailable 10:30–12:00 against the 11:00
  pin → named conflict, plan unchanged.

### Technical section (Part B)

- **Diagram:** `docs/presentation/autoschedule-use-case.puml/.svg/.png` (created by
  this audit — slide-legible; the exhaustive 76-node version stays in
  `docs/autoschedule/diagrams/`).
- **Interactor excerpt** — `AutoScheduleInteractor.preview`, the heart (lines ~114–150
  compressed). What each line proves:

```java
ScheduleConflict invalid = problemValidator.validate(availability, tasks,
        inputData.getUnavailableWindows());          // guard before any work
if (invalid != null) {
    presenter.presentConflict(new AutoScheduleConflictOutputData(invalid));
    return;                                          // failure = data out the boundary
}
matrix = prefetcher.prefetch(tasks, mode, trip.getDate(), availability);
                                                     // all network I/O before the search
WeatherContext weather = weatherFor(trip, inputData.isConsiderWeather(), warnings);
                                                     // via gateway interface (DIP)
SchedulingPreferences preferences = SchedulingPreferences.builtIn(
        registeredPolicies, inputData.isKeepCurrentOrder(), new PolicyContext(weather));
                                                     // Strategy list assembled
RefinementOutcome outcome = searchWithExactTravel(availability, tasks,
        inputData.getUnavailableWindows(), matrix, preferences, mode, trip.getDate());
                                                     // pure engine call
presenter.presentPreview(buildPreview(...));         // Output Boundary; nothing saved
```

- **Design decision:** *"Preview-then-Apply with a fingerprint, instead of applying
  directly: the search result is a proposal until the user commits, and Apply is
  refused if the plan changed underneath it. Cost: an extra step and stale-preview
  handling; benefit: Autoschedule can never destroy a hand-tuned day."* (Alternate:
  all-or-nothing scheduling — fail rather than silently drop an activity.)
- **DIP:** §4 above. **Strategy:** §5 above.

### Likely TA questions, short answers

- *Why is this distinct from Alex's add-to-plan?* — Add-to-plan chooses activities;
  Autoschedule starts only after they're chosen and never adds or removes one.
- *What's optimized vs constrained?* — Hard: hours, availability, locks, overlaps,
  travel feasibility. Optimized: travel + waiting + capped meal/daylight/weather +
  order preference. Hard rules are never traded.
- *How is the dependency rule kept when the Interactor calls the DAO?* — It calls the
  `TripRepository` **interface it owns**; the concrete DAO implements it. Runtime flow
  outward, source dependency inward.
- *What if the routing service fails?* — Prefetch catches it: "Travel times are
  unavailable… your Day Plan was not changed." Failure is data, not an exception
  escaping to Swing.
- *How do you know the search is correct?* — `BruteForceCrossCheckTest`, exhaustive
  vs pruned on randomized days.
- *Unknown opening hours?* — Unknown ≠ closed; the activity keeps its coarse window.
  Treating silence as "shut" would refuse to plan most real days (most OSM places
  publish no hours).

---

## 8. UML diagram

`docs/autoschedule/diagrams/autoschedule-use-case-class-diagram.puml` (76 nodes) was
verified this week against source and remains the exhaustive record. For the slide,
this audit adds a **legible full-slice version** with exact current names:
`docs/presentation/autoschedule-use-case.puml` → rendered `.svg`/`.png` beside it.
Render command if needed again:

```bash
PUML=$(find ~/.m2 -name 'plantuml-*.jar' | head -1)
java -DGRAPHVIZ_DOT=$(which dot) -jar "$PUML" -tsvg docs/presentation/autoschedule-use-case.puml
```
