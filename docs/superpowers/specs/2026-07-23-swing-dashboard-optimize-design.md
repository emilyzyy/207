# Swing Dashboard and Optimize Current Itinerary Design

## Goal

Deliver the July 23 milestone foundation for CloseAI by adding a complete,
visible Java Swing dashboard with focused View/ViewModel/State classes and one
fully wired use case: Emily's Optimize Current Itinerary flow.

The implementation will preserve the existing Java backend and retained web
prototype. It will not attempt to complete every existing use case or add new
external integrations.

## Product Scope

The Swing application launches directly into a seeded demonstration trip so the
team can inspect the complete dashboard immediately. The demo source is isolated
behind application composition so a future Create Trip flow can replace it
without changing the Views.

The visible application contains:

- a header with the CloseAI title, active destination/date, and a visibly
  inactive Share control;
- a left overview area with a map placeholder and weather card;
- a right planner area with Search, Bookmarks, Day Plan, and Trip Options tabs;
- a Calendar dialog opened from the Day Plan or main frame;
- reasonable seeded activity, bookmark, itinerary, weather, and trip-option
  content.

Only Optimize Current Itinerary is fully wired. Controls belonging to unfinished
features remain visible but explicitly identify themselves as unavailable in
this milestone. They do not silently mutate local UI state or imply backend
functionality.

## Architecture

`Main` launches Swing on the Event Dispatch Thread. `AppBuilder` remains the
outer composition root and preserves its existing backend construction methods.
It gains Swing composition that creates the backend container, demo trip,
ViewModels, States, controller, presenter, panels, dialog, and main frame.

```text
Main
└── AppBuilder
    ├── existing AppContainer and backend dependencies
    ├── replaceable demo-trip bootstrap
    ├── ViewModels and States
    ├── Optimize Itinerary controller/presenter wiring
    └── CloseAIFrame
        ├── HeaderPanel
        ├── OverviewPanel
        │   ├── map placeholder
        │   └── weather card
        └── PlannerPanel
            ├── SearchPanel
            ├── BookmarksPanel
            ├── DayPlanPanel
            └── TripOptionsPanel

CloseAIFrame / DayPlanPanel
└── CalendarDialog
```

The Calendar dialog is part of the Day Plan/main-frame flow. It is not owned by
or nested under Trip Options.

## Views, ViewModels, and States

The presentation layer uses focused components rather than one oversized View.
A small generic ViewModel base owns a current state value and
`PropertyChangeSupport`. Swing Views observe their ViewModels and rerender when
the state changes.

Major presentation models are:

- `DashboardViewModel` / `DashboardState`: active destination, date, and weather;
- `SearchViewModel` / `SearchState`: visible activity results and query text;
- `BookmarksViewModel` / `BookmarksState`: the current bookmark snapshot;
- `DayPlanViewModel` / `DayPlanState`: scheduled-event snapshot, success status,
  and error status;
- `TripOptionsViewModel` / `TripOptionsState`: destination, date, trip window,
  and transportation mode.

State objects expose defensive list snapshots. Views display domain-derived
information but cannot mutate the domain collections.

The visible components are:

- `CloseAIFrame`: owns the header, split dashboard, and Calendar dialog launch;
- `HeaderPanel`: product identity, active-trip summary, and Share placeholder;
- `OverviewPanel`: map placeholder and weather summary;
- `PlannerPanel`: owns the four-tab `JTabbedPane`;
- `SearchPanel`: seeded activity cards/list, filters, and unavailable actions;
- `BookmarksPanel`: seeded bookmark list and unavailable bookmark actions;
- `DayPlanPanel`: live itinerary display, Optimize button, Calendar button, and
  clearly unavailable manual-edit controls;
- `TripOptionsPanel`: seeded option fields with a clearly unavailable Save
  action;
- `CalendarDialog`: read-only timeline/list refreshed from `DayPlanViewModel`.

## Optimize Current Itinerary Use Case

The new use case is separate from the existing bookmark-driven
`AutoScheduleTripUseCase`. Existing constructors and behavior remain available
for compatibility.

The dependency flow is:

```text
DayPlanPanel
→ OptimizeItineraryController
→ OptimizeItineraryInputBoundary
→ OptimizeItineraryInteractor
→ ItineraryDataAccessInterface
→ OptimizeItineraryOutputBoundary
→ OptimizeItineraryPresenter
→ DayPlanViewModel
→ DayPlanPanel and CalendarDialog
```

The controller supplies the active trip ID in immutable input data. The
interactor loads and saves through Bianca's `ItineraryDataAccessInterface`; it
does not depend on the in-memory implementation.

For this milestone, the algorithm:

1. Reads the trip's existing scheduled events.
2. Selects only events of type `ACTIVITY`.
3. Rejects an itinerary with no selected activities.
4. Ignores all bookmarks and existing travel events.
5. Preserves the current activity order.
6. Preserves every selected event's ID, activity, notes, and duration.
7. Packs the activities forward from the trip start, waiting until an
   activity's opening time when required.
8. Rejects the operation before saving if an activity would end after its
   closing time or outside the trip window.
9. Saves a separate trip copy containing exactly the selected activity events.

Travel insertion, activity reordering, distance optimization, weather weighting,
and global optimization remain outside this milestone.

## Demo Data

Swing composition creates one replaceable demo trip through existing
application services. It contains:

- three activities already added to the current itinerary;
- at least one unrelated bookmarked activity that is not in the itinerary;
- mock weather from the existing `WeatherService`;
- the existing mock activity catalog.

This makes the Optimize contract visible and testable: optimizing cannot add the
unrelated bookmark.

## Error Handling

The interactor presents a failure without saving when:

- the trip cannot be found;
- no activity is currently scheduled;
- an activity has a non-positive duration;
- packing an activity would exceed its closing time or the trip end.

`OptimizeItineraryPresenter` converts success and failure into
`DayPlanState`. The Day Plan shows success/error status inside the panel. Swing
event handlers do not expose stack traces or mutate the itinerary directly.

Unavailable controls use a consistent visible message such as
"Not wired for this milestone." They do not throw exceptions or imply success.

## Testing and Verification

`OptimizeItineraryInteractorTest` directly tests the interactor with a fake data
access implementation and recording output boundary. Its primary test creates:

- two activities already scheduled;
- one unrelated bookmarked activity;
- existing travel events.

The test verifies that the successful output and saved trip contain exactly the
two scheduled activities, preserve their IDs and durations, omit the bookmark,
and omit old travel events.

Additional focused tests cover no scheduled activities and impossible packing
without persistence.

Completion verification consists of:

1. running `./mvnw clean test`;
2. confirming all ordinary tests pass and only the opt-in live weather test is
   skipped;
3. launching `closeai.Main`;
4. confirming Swing is created on the Event Dispatch Thread;
5. visually checking the complete dashboard and Calendar dialog;
6. invoking Optimize and confirming Day Plan and Calendar both refresh;
7. confirming `git diff --check` is clean.

## Explicit Non-Goals

This milestone does not include:

- a sophisticated or globally optimal scheduling algorithm;
- real map integration;
- polished drag-and-drop;
- Google Calendar integration;
- functional share/export;
- new external APIs;
- authentication;
- collaborative editing;
- full Swing wiring for every existing backend use case.

