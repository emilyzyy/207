# July 23 Swing Milestone Report

## Delivered scope

- Integrated Bianca's edit-itinerary branch before building the Swing milestone.
- Added a complete visible Swing dashboard with a header, overview/map placeholder,
  weather card, Search, Bookmarks, Day Plan, Trip Options, and Calendar.
- Added focused State and ViewModel classes for Dashboard, Search, Bookmarks, Day Plan,
  and Trip Options using direct `PropertyChangeSupport`.
- Wired **Create Trip**, **Edit Trip Options**, and **Optimize Current Itinerary** into Swing.
- Integrated Raashid's interactive marker map and optional Nominatim/Overpass places adapter.
- Removed the seeded Toronto trip; Trip Setup now creates the active repository trip.
- Kept mock weather, mock places, and offline map rendering as defaults.
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

- 45 tests run
- 0 failures
- 0 errors
- 1 skipped opt-in live-weather test

The application was also launched through `Main`; the runtime reported that the Swing
dashboard was created on the Swing event-dispatch thread. A non-headless integration
test creates a trip through the form, edits it, and verifies that Optimize resolves the
same stored trip ID.

## Direct unit-test inventory

Direct unit tests currently exist for:

- `OptimizeItineraryInteractor`
- `EditItineraryInteractor`
- `CreateTripUseCase`
- legacy `AutoScheduleTripUseCase`

The following started use cases/interactors do **not** yet have a direct unit-test class:

1. `SearchActivitiesUseCase`
2. `FilterActivitiesUseCase`
3. `BookmarkActivityUseCase`
4. `RemoveBookmarkUseCase`
5. `AddActivityToPlanUseCase`
6. `EditScheduledEventUseCase`
7. `RemoveScheduledEventUseCase`
8. `GetTripSummaryUseCase`
9. `ShareTripUseCase`
10. `GetWeatherWarningUseCase`

Some of these paths receive indirect coverage from composition or legacy integration
checks, but that is not a substitute for a direct unit test. These 10 classes are the
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
| Trip setup | `TripOptionsPanel`, `TripOptionsViewModel`, `TripSetupController`, `TripSetupPresenter` | Shiyuan + Alex | Implementation and automated review complete; Alex confirmation pending |
| Trip overview and weather | `OverviewPanel`, `MapPanel`, `DashboardViewModel` | Raashid + Shiyuan | Raashid implementation integrated and tested by Shiyuan; pair confirmation pending |
| Shared app shell and composition | `HeaderPanel`, `CloseAIFrame`, `AppBuilder` | Raashid + Shiyuan | Integration complete; pair confirmation pending |

Automated verification and integration status are recorded separately from human
pair sign-off. No teammate confirmation is claimed on another person's behalf.
