# July 23 Swing Milestone Report

## Delivered scope

- Integrated Bianca's edit-itinerary branch before building the Swing milestone.
- Added a complete visible Swing dashboard with a header, overview/map placeholder,
  weather card, Search, Bookmarks, Day Plan, Trip Options, and Calendar.
- Added focused State and ViewModel classes for Dashboard, Search, Bookmarks, Day Plan,
  and Trip Options using direct `PropertyChangeSupport`.
- Wired only **Optimize Current Itinerary** into Swing.
- Kept the existing web prototype available through `Main --web`.

The optimizer is a **first-pass valid schedule compaction**, not a complete optimization
algorithm. It uses only activity events already in the current itinerary, ignores
bookmarks, introduces no new activities, removes existing travel events for this
milestone, and saves a valid schedule. It uses `TripRepository` and saves a copied Trip
whose ID, destination, date, trip window, transportation mode, bookmarks, and other
existing non-schedule state are preserved.

`CalendarDialog` is owned by `CloseAIFrame`, opened from `DayPlanPanel`, and observes the
same `DayPlanViewModel` instance as the Day Plan.

## Verification

The final `./mvnw clean test` run reported:

- 29 tests run
- 0 failures
- 0 errors
- 1 skipped opt-in live-weather test

The application was also launched through `Main`; the runtime reported that the Swing
dashboard was created on the Swing event-dispatch thread.

## Direct unit-test inventory

Direct unit tests currently exist for:

- `OptimizeItineraryInteractor`
- `EditItineraryInteractor`
- legacy `AutoScheduleTripUseCase`

The following started use cases/interactors do **not** yet have a direct unit-test class:

1. `CreateTripUseCase`
2. `SearchActivitiesUseCase`
3. `FilterActivitiesUseCase`
4. `BookmarkActivityUseCase`
5. `RemoveBookmarkUseCase`
6. `AddActivityToPlanUseCase`
7. `EditScheduledEventUseCase`
8. `RemoveScheduledEventUseCase`
9. `GetTripSummaryUseCase`
10. `ShareTripUseCase`
11. `GetWeatherWarningUseCase`

Some of these paths receive indirect coverage from composition or legacy integration
checks, but that is not a substitute for a direct unit test. These 11 classes are the
remaining milestone testing gap.

## Swing pair review record

The table records the required two-person understanding/review assignment from the
team's task split. It does **not** claim co-authorship or completed review. Each pair must
confirm its sign-off after walking through the View, its ViewModel/state source, and its
placeholder-versus-wired behavior.

| Swing area | View / ViewModel | Required pair | Status |
| --- | --- | --- | --- |
| Main planner and calendar | `PlannerPanel`, `DayPlanPanel`, `CalendarDialog`, `DayPlanViewModel` | Emily + Bianca | Pending pair sign-off |
| Activity discovery and saved activities | `SearchPanel`, `SearchViewModel`, `BookmarksPanel`, `BookmarksViewModel` | Alex + Raashid | Pending pair sign-off |
| Trip setup | `TripOptionsPanel`, `TripOptionsViewModel` | Shiyuan + Alex | Pending pair sign-off |
| Trip overview and weather | `OverviewPanel`, `DashboardViewModel` | Raashid + Shiyuan | Pending pair sign-off |
| Shared app shell and composition | `HeaderPanel`, `CloseAIFrame`, `AppBuilder` | Raashid + Shiyuan | Pending pair sign-off |

No review confirmation was available when this report was written, so none of these
rows is marked complete.
