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
5. **`WeatherContextGateway`** supplies forecast context; a failure becomes "unavailable"
   rather than an exception, so weather can never cost the traveller a schedule.
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
