# Autoschedule architecture

Emily Yan — CSC207 individual feature. Reorders and retimes the activities already in a
Day Plan, previews the result, and applies it only when the traveller agrees.

## Component diagram

Every arrow is a source-code dependency, and every one of them points inward. The engine
sits at the centre and knows nothing about repositories, HTTP or Swing.

```text
  FRAMEWORKS / UI                INTERFACE ADAPTERS                 APPLICATION
  ───────────────                ──────────────────                 ───────────

  DayPlanPanel  ─────────────▶  AutoScheduleController ────▶ «interface»
  AutoScheduleSettingsDialog                │                AutoScheduleInputBoundary
        ▲                                   │                         △
        │ observes                          │ TaskRunner              │ implements
        │                                   │ (SwingTaskRunner)       │
  DayPlanViewModel  ◀──────  AutoSchedulePresenter        AutoScheduleInteractor
  DayPlanState                       △                     │    │      │       │
        │                            │ implements          │    │      │       │
        │ observes                   └──── «interface» ◀───┘    │      │       │
        ▼                                 AutoScheduleOutputBoundary   │       │
  CalendarViewModel                                          │        │       │
  CalendarPanel                                              ▼        ▼       ▼
                                                    «interface»  «interface»  ScheduleEngine
                                                    TripRepository TravelTime   │
                                                          △        Estimator    │ uses
                                                          │             △       ▼
                                                          │             │   ActivityPlacer
  InMemoryTripRepository ─────────────────────────────────┘             │   GreedyPlanner
                                                                        │   PlanValidator
  OsrmDistanceService ◀── DistanceServiceTravelTimeEstimator ───────────┘   SchedulePlanRebuilder
  (OSRM / Transitous / TomTom)                                              List<SoftPolicy>
                                                                              │
  OpenMeteoWeatherService ◀── WeatherServiceContextGateway ──▶ «interface»    ├─ WeatherSuitabilityPolicy
                                                          WeatherContextGateway ├─ MealWindowPolicy
                                                                              └─ DaylightPolicy
```

### Who owns what

| Layer | Classes | Owner |
|---|---|---|
| View | `DayPlanPanel`, `AutoScheduleSettingsDialog` | Emily |
| Controller | `AutoScheduleController`, `AutoScheduleSettings`, `AutoScheduleSettingsValidator`, `TaskRunner`/`SwingTaskRunner` | Emily |
| Presenter / ViewModel | `AutoSchedulePresenter`, `PreviewRowView`, `PreviewMetricsView`, `AutoScheduleStatus`; additive fields on `DayPlanState` | Emily |
| Boundaries and DTOs | `AutoScheduleInputBoundary`, `AutoScheduleOutputBoundary`, `AutoScheduleInputData`, `AutoScheduleApplyInputData`, `ProposedEventData`, `AutoSchedulePreviewOutputData`, `AutoScheduleAppliedOutputData`, `AutoScheduleConflictOutputData` | Emily |
| Use case | `AutoScheduleInteractor` | Emily |
| Engine | `ScheduleEngine`, `ActivityPlacer`, `GreedyPlanner`, `PlanValidator`, `SchedulePlanRebuilder`, `SearchBudget`, `PlacementRule` | Emily |
| Policies | `SoftPolicy` + weather / meal / daylight implementations | Emily |
| Inward gateways | `TravelTimeEstimator`, `WeatherContextGateway` | Emily |
| Gateway adapters | `DistanceServiceTravelTimeEstimator`, `WeatherServiceContextGateway` | Emily |
| Routing service | `OsrmDistanceService` (OSRM, Transitous, TomTom) | Raashid |
| Forecast service | `OpenMeteoWeatherService` | Shiyuan |
| Repository, entities | `TripRepository`, `Trip`, `Activity`, `ScheduledEvent` | Shared |

Emily owns the policy — what the numbers mean for a schedule — and the ports that express
what the use case needs. The teammates own the concrete services behind those ports.

## Data flow: Preview

0. **Capability lookup.** `DayPlanPanel` opens the dialog immediately with "Consider
   weather" disabled and unticked, then asks `AutoScheduleController.loadWeatherOption`
   whether the preference can honestly be offered. That runs on the `TaskRunner`, because
   answering it means asking a forecast provider, and the panel marshals the reply back to
   the event thread itself — knowing this is Swing is the view's job, not the controller's.
   The question travels through `AutoScheduleInputBoundary.weatherOptionFor`, so no Swing
   class ever learns that Open-Meteo exists. It comes back as a plain `WeatherOption`:
   available, selected-by-default, and a reason string when withheld. See "The weather
   capability gate" below.
1. **`DayPlanPanel`** opens `AutoScheduleSettingsDialog`. The dialog validates locally so a
   typo is caught while it is still open.
2. **`AutoScheduleController`** reads plain values, adds the pinned event ids held in the
   view model, sets a `LOADING` state, and hands the work to `TaskRunner`. In the running
   application that is a `SwingWorker`, so the event thread is free immediately.
3. **`AutoScheduleInteractor`** loads the `Trip`, validates the request (availability inside
   the trip's hours, pins that still exist and are legal, non-overlapping unavailable
   periods), and turns the Day Plan's events into `ScheduleTask`s, preserving each event's
   current duration.
4. **`TravelMatrixPrefetcher`** asks `TravelTimeEstimator` for every directed pair, once per
   active departure period. This is the only place travel requests are made.
5. **`WeatherContextGateway`** supplies forecast context, but only when the traveller
   selected the preference — weather not asked for is weather not fetched. A failure becomes
   "unavailable" rather than an exception, so weather can never cost the traveller a
   schedule.
6. **`ScheduleEngine`** searches. Pure, deterministic, no I/O: bounded branch-and-bound over
   visit orders, each activity placed at its earliest feasible time, with a greedy
   incumbent and an admissible lower bound.
7. **Refinement**: each leg of the winning order is re-estimated for the time it will really
   be travelled, the order is re-timed, and `PlanValidator` re-checks every hard rule. If
   the truer numbers break it, the search runs again knowing them, at most twice, and a day
   that cannot be salvaged returns a conflict instead of a wrong schedule.
8. **`AutoSchedulePresenter`** turns reason codes into sentences and updates
   `DayPlanViewModel`. The proposal goes into the preview rows; `getEvents()` still holds
   the real itinerary, which is why the Calendar keeps showing agreed times.

Nothing has been written at any point in this sequence.

## Data flow: Apply

1. **`AutoScheduleController`** rebuilds the proposed rows from what is on screen and sends
   them with the fingerprint the preview was built from.
2. **`AutoScheduleInteractor`** reloads the `Trip` and recomputes the fingerprint over its
   activity ids and times. A mismatch means the Day Plan moved on, and Apply is refused.
3. Events are reconstructed from the trip's own activities, then saved atomically through
   `Trip.copyWithSchedule` — the entity re-validates its own invariants as a final guard.
4. **`AutoSchedulePresenter`** re-times the events it already holds so the Calendar keeps
   real activity details, and publishes `APPLIED`. The Calendar updates through the
   `PropertyChangeSupport` it already observed.

## The weather capability gate

Weather is the only soft objective the traveller is asked about, and the reason is a
product one. Providers return an hourly forecast for a trip a few days out and a single
whole-day outlook for one further ahead. A whole-day outlook scores every candidate slot
identically, so it cannot say whether a park is better at 10 a.m. or 3 p.m. A checkbox
backed by it would look like a choice and change nothing.

**The capability is asked, not assumed.** `WeatherContextGateway.optionFor(Trip)` derives
the answer from what the provider actually returned — `WeatherContext.canDistinguishTimes()`
— rather than from a hard-coded date cutoff. A cutoff would encode a guess about someone
else's service and go stale the moment they changed their horizon. A trip beyond the hourly
range therefore needs no special case: it arrives as a coarse forecast and is refused for
that reason, like any other coarse forecast.

**The backend is the final authority.** The dialog's belief is an optimisation, not a
decision. `AutoScheduleInteractor` applies two gates in order: weather not requested is
never fetched, and weather that comes back coarse or unavailable contributes zero, produces
an honest warning, and is left out of the applied-objectives list. A stale or mistaken tick
costs nothing but the tick, and the schedule is produced either way.

**It stays soft and bounded.** `WeatherSuitabilityPolicy` charges equivalent wasted minutes
capped at `MAX_PENALTY_MINUTES` (60). That ceiling is what guarantees avoiding bad weather
can never buy an hours-long detour, and weather can never make a day unschedulable or
override a lock, an unavailable window, opening hours or travel feasibility.

| State | Checkbox | Explanation shown |
|---|---|---|
| Hourly forecast available | Enabled, **ticked by default**, may be unticked | none |
| Whole-day forecast | Disabled, unticked | "Hourly weather is not available for this trip date." |
| Beyond the provider's hourly range | Disabled, unticked | same — it arrives as a coarse forecast |
| No forecast obtainable | Disabled, unticked | "Weather information is not available for this trip date." |
| Lookup still running | Disabled, unticked | "Checking hourly weather for this trip date..." |

The explanation is a visible label, not colour or absence, and it is repeated on the
checkbox's accessible description because a disabled control can be skipped in focus
traversal.

**Today, only the disabled row is reachable in production:** both shipped adapters report
one severity per trip. That is verified rather than assumed — the live run records
`available=true canDistinguishTimes=false`, and `AutoScheduleWalkthroughTest` asserts both
the withheld state through the real wiring and that an hourly gateway would offer the
preference with no other change.

## Class diagram

The full use-case class diagram — 63 production classes and interfaces, every node
cross-checked against source — is in
[`diagrams/autoschedule-use-case-class-diagram.md`](diagrams/autoschedule-use-case-class-diagram.md),
with PlantUML source and rendered SVG/PNG beside it. It follows the course's own conventions:
PlantUML, as used in the Clean Architecture lecture, and name/attribute/method class boxes as
introduced in the entity-discovery lecture.

## Course concepts genuinely present

| Concept | Where it is real | Source |
|---|---|---|
| Clean Architecture boundaries | Controller → Input Boundary → Interactor → Output Boundary → Presenter → ViewModel | `10-CleanArchitecture (1).pdf`; Piazza @230 |
| Dependency Rule | The engine has no outward imports; adapters implement inward ports | Piazza @229 |
| Dependency Inversion | `TravelTimeEstimator` and `WeatherContextGateway` are owned by the use case; adapters live outward | Piazza @273/@274 |
| Dependency Injection | `AppBuilder` chooses engine, policies and adapters; nothing self-constructs | `14-DesignPatterns-1 (1).pdf` |
| Strategy | Each soft policy is a separate class; the engine scores whatever list it is handed and contains no policy-specific branching | `14-DesignPatterns-1 (1).pdf` |
| Open/Closed | A new preference is a new class plus one registration; the engine and its tests do not change | `09-SOLID (1).pdf` |
| Adapter | `DistanceServiceTravelTimeEstimator`, `WeatherServiceContextGateway` wrap teammate services | `14-DesignPatterns-1 (1).pdf` |
| Observer | `DayPlanViewModel`'s existing `PropertyChangeSupport`; the Calendar was not modified | existing code |
| DTOs across boundaries | No entity crosses either boundary, asserted by a reflection test | Piazza @296 |
| Single Responsibility | Controller has no scheduling logic; presenter owns all wording; placer answers "when can this happen" in one place | `09-SOLID (1).pdf` |
| Testing with fakes | Fake estimator, fake repository, fake gateway, recording presenter | `12-TestingInCA (1).pdf` pp. 9–15 |
| Packaging | `closeai.application.autoschedule` with `engine` and `policy` subpackages | `13-Packaging.pdf` |

Deliberately **not** claimed: Composite and Specification are not in the taught pattern list,
so `List<SoftPolicy>` is described as a list and `PlacementRule` is not renamed. `Clock`
injection was considered and rejected — the use case never reads the wall clock.
