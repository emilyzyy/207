# Autoschedule — full use-case class diagram

| | |
|---|---|
| **Source** | [`autoschedule-use-case-class-diagram.puml`](autoschedule-use-case-class-diagram.puml) |
| **Vector** | [`autoschedule-use-case-class-diagram.svg`](autoschedule-use-case-class-diagram.svg) — 3506 × 2300, zoomable; use this on screen |
| **Raster** | [`autoschedule-use-case-class-diagram.png`](autoschedule-use-case-class-diagram.png) — 5477 × 3592 (150 dpi), landscape; use this on a slide |
| **Covers** | 63 production classes, interfaces and enums; 0 test doubles |

This is the third Required Element of the Individual Presentation Rubric — *before and
after Views, Use Case Interactor code, and a class diagram for the full Use Case* (S-017).
The other two are the screenshots in [`../screenshots/`](../screenshots/) and
`AutoScheduleInteractor`.

## Regenerating

```bash
# PlantUML comes from Maven; Graphviz supplies the layout engine.
./mvnw dependency:get -Dartifact=net.sourceforge.plantuml:plantuml:1.2024.8
PUML=$(find ~/.m2 -name 'plantuml-1.2024.8.jar' | head -1)
cd docs/autoschedule/diagrams
java -DGRAPHVIZ_DOT=$(which dot) -jar "$PUML" -tsvg autoschedule-use-case-class-diagram.puml
java -DPLANTUML_LIMIT_SIZE=16384 -DGRAPHVIZ_DOT=$(which dot) -jar "$PUML" -tpng -Sdpi=150 \
     autoschedule-use-case-class-diagram.puml
```

## Course conventions followed

| Convention | Source | How it is applied |
|---|---|---|
| PlantUML as the diagramming tool | `10-CleanArchitecture (1).pdf` p.54 shows a worked `@startuml … @enduml` example | The editable source is `.puml` |
| UML class-diagram boxes: name / attributes / methods, linked by dependency arrows | `04-entity-discovery (1).pdf` p.4 | Every box carries all three compartments; arrows are typed |
| Clean Architecture layering | `10-CleanArchitecture (1).pdf` | Four packages, dependencies pointing inward only |

Layers read **left to right**, which is also the direction every dependency arrow points:
Frameworks & Drivers → Interface Adapters → Application Business Rules → Entities. Nothing
points back out.

## Notation key

| Symbol | Meaning |
|---|---|
| `..|>` hollow triangle, dashed | implements (realization) |
| `-->` solid arrow | association — usually a constructor-injected field |
| `..>` dashed arrow | uses transiently — creates, returns, or reads a type |
| `*--` filled diamond | composition — the whole owns the part |
| `o--` hollow diamond | aggregation — the whole holds parts it does not own |
| `«interface»` | interface; also drawn with the `I` badge |
| `«DTO»`, `«value object»`, `«entity»` | stereotypes, used only where the role is not obvious from the name |

Multiplicities appear only where the code supports them — for example
`AutoScheduleInteractor "1" o-- "3" SoftPolicy`, because `AppBuilder` injects exactly the
three policies, and `SchedulePlan "1" *-- "1..*" PlacedActivity`, because the constructor
rejects an empty placement list.

## Reading the Clean Architecture story

### 1. Controller → Input Boundary

`AutoScheduleController` holds an `AutoScheduleInputBoundary`, never the Interactor. It
converts `AutoScheduleSettings` (a plain adapter-layer DTO read out of the Swing dialog)
into `AutoScheduleInputData`, and hands it across. It contains no scheduling logic — that
is what lets the same use case be driven by a test, or later a different interface.

`loadWeatherOption(Consumer<WeatherOption>)` is the same idea for the capability question
the settings dialog asks while drawing itself.

### 2. Interactor

`AutoScheduleInteractor` is the only implementation of the input boundary. It orchestrates
and delegates: `ProblemValidator` checks the request, `TravelMatrixPrefetcher` gathers
travel, `ScheduleEngine` searches, `SchedulePlanRebuilder` and `PlanValidator` re-time and
re-check, `ReasonCollector` explains. It holds no search logic itself, and the engine holds
no I/O — that split is why the whole feature is testable without a network.

### 3. Gateway interfaces (dependency inversion)

Three interfaces are **declared inside the use-case package** and implemented further out:

| Interface (inner) | Implementation (outer) | Wraps |
|---|---|---|
| `TravelTimeEstimator` | `DistanceServiceTravelTimeEstimator` | `DistanceService` → `OsrmDistanceService` (OSRM / TomTom / Transitous) |
| `WeatherContextGateway` | `WeatherServiceContextGateway` | `WeatherService` → `OpenMeteoWeatherService` |
| `TripRepository` | `InMemoryTripRepository` | in-memory persistence |

This is the inversion: the arrow from `DistanceServiceTravelTimeEstimator` to
`TravelTimeEstimator` points *inward*, from the adapter layer into the use case, even though
the data flows outward at runtime. The Interactor never names OSRM, TomTom or Open-Meteo.
`TaskRunner` / `SwingTaskRunner` inverts the same way for threading, which is what keeps
`SwingWorker` out of the Controller.

### 4. Output Boundary → Presenter

`AutoScheduleInteractor` calls `AutoScheduleOutputBoundary`, which `AutoSchedulePresenter`
implements. Four DTOs cross that boundary — `AutoSchedulePreviewOutputData`,
`AutoScheduleAppliedOutputData`, `AutoScheduleConflictOutputData`, and a plain failure
message. No entity ever crosses in either direction: Apply reconstructs events from the
Trip's own activities.

### 5. ViewModel / Observer update

`AutoSchedulePresenter` turns reason codes into sentences and calls
`DayPlanViewModel.setState(DayPlanState)`. `DayPlanViewModel` holds a
`PropertyChangeSupport` and fires `"state"`; `DayPlanPanel` is a listener and re-renders.
The Presenter never touches Swing, and the View never calls the Interactor.

`DayPlanState` is immutable and carries the proposal (`previewRows`, `metrics`, `warnings`)
separately from `events`, the real itinerary — which is precisely why a Preview does not
disturb the Calendar.

### 6. Where dependency injection happens

`AppBuilder.buildAutoSchedule(..)` is the single composition root. It constructs the
presenter, the three policies, both gateway adapters, the engine, the Interactor and the
task runner, and returns the Controller. Nothing else in the feature calls `new` on a
dependency. The diagram draws only the edge `AppBuilder ..> AutoScheduleController` and puts
the rest in a note: six `«creates»` arrows fanning across the canvas would have said nothing
extra while costing every other relationship its readability.

## Verification

Every node and relationship was read out of the source on `feature/emily-autoschedule` —
constructor signatures, `implements` clauses, field types and the `AppBuilder` wiring — not
from planning documents. An automated cross-check confirms:

- 63 / 63 diagram nodes exist as production source files;
- every class the brief asked for is present;
- no test double, fake or fixture appears.

## Known simplifications

Stated so the diagram is not read as claiming more than it shows:

- **Value objects used only as parameters or returns are omitted** where they would add a
  box and no insight: `TravelEstimate`, `TravelLeg`, `Reason`, `ReasonCode`, `PolicyId`,
  `DeparturePeriod`, `PeriodPlan`, `TravelLegKey`, `SearchState`, `PlacementRule`,
  `BlockedPeriods`, `ScheduleMetrics`. They are all in
  `closeai.application.autoschedule`.
- **`AutoScheduleSettingsValidator`** is drawn in the adapter layer because that is where it
  lives; the Interactor re-validates everything independently, which the diagram shows as
  `ProblemValidator`.
- **The Calendar** side of the ViewModel is out of scope here; `CalendarViewModel` observes
  the same `DayPlanViewModel` and is covered in [`../architecture.md`](../architecture.md).
- `InMemoryItineraryDataAccessObject` also implements `TripRepository`, but production wiring
  uses `InMemoryTripRepository`, so only that one is drawn.
