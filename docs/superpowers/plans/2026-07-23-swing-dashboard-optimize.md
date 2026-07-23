# Swing Dashboard and Optimize Current Itinerary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Launch a complete seeded CloseAI Swing dashboard and fully wire one first-pass Optimize Current Itinerary workflow that compacts only activities already in the current schedule.

**Architecture:** `Main` launches a Swing `CloseAIFrame` assembled by `AppBuilder`. Focused Views observe focused ViewModels/States. The optimize controller invokes an input boundary; the interactor reads and saves through `TripRepository`; an output boundary and presenter update the shared `DayPlanViewModel` observed by both Day Plan and Calendar.

**Tech Stack:** Java 11, Java Swing, JUnit 5, Maven, existing CloseAI domain/application ports.

## Global Constraints

- Preserve the existing backend constructors and retained web prototype.
- Do not wire the legacy bookmark-driven `AutoScheduleTripUseCase` into Swing.
- Do not add a duplicate persistence abstraction or DAO.
- Preserve all non-schedule `Trip` state when compacting the schedule.
- Only Optimize Current Itinerary is functional; other actions must visibly identify themselves as unavailable.
- Calendar and Day Plan must observe the same `DayPlanViewModel` instance.
- No real map, drag-and-drop, export, authentication, collaboration, or new API work.

---

### Task 1: Optimize Current Itinerary interactor

**Files:**
- Create: `src/main/java/closeai/application/usecases/OptimizeItineraryInputData.java`
- Create: `src/main/java/closeai/application/usecases/OptimizeItineraryInputBoundary.java`
- Create: `src/main/java/closeai/application/usecases/OptimizeItineraryOutputData.java`
- Create: `src/main/java/closeai/application/usecases/OptimizeItineraryOutputBoundary.java`
- Create: `src/main/java/closeai/application/usecases/OptimizeItineraryInteractor.java`
- Test: `src/test/java/closeai/application/usecases/OptimizeItineraryInteractorTest.java`

**Interfaces:**
- Consumes: `TripRepository.findById(String)` and `TripRepository.save(Trip)`.
- Produces: `void OptimizeItineraryInputBoundary.execute(OptimizeItineraryInputData)`.
- Presents: `presentSuccess(OptimizeItineraryOutputData)` or `presentFailure(String)`.

- [ ] **Step 1: Write the failing contract test**

Create a test fixture with two scheduled activities, travel events, and one
unrelated bookmark. Use a fake `TripRepository` and recording output boundary.
Assert that the saved/output trip preserves all trip options and bookmarks but
contains only the two planned activities with preserved IDs and durations.

```java
@Test
void compactsOnlyCurrentActivitiesAndPreservesTripState() {
    Trip trip = tripWithOptions();
    Activity first = activity("first", LocalTime.of(9, 0), LocalTime.of(18, 0));
    Activity second = activity("second", LocalTime.of(9, 0), LocalTime.of(18, 0));
    Activity unrelatedBookmark = activity("bookmark-only", LocalTime.of(9, 0), LocalTime.of(18, 0));
    trip.bookmark(unrelatedBookmark);
    trip.addEvent(travel("travel-before", LocalTime.of(9, 0), LocalTime.of(9, 20)));
    trip.addEvent(event("event-first", first, LocalTime.of(10, 0), LocalTime.of(11, 0)));
    trip.addEvent(travel("travel-middle", LocalTime.of(11, 0), LocalTime.of(11, 20)));
    trip.addEvent(event("event-second", second, LocalTime.of(13, 0), LocalTime.of(14, 30)));
    FakeTripRepository repository = new FakeTripRepository(trip);
    RecordingOutputBoundary output = new RecordingOutputBoundary();

    new OptimizeItineraryInteractor(repository, output)
            .execute(new OptimizeItineraryInputData(trip.getId()));

    Trip saved = repository.saved;
    assertNotNull(output.success);
    assertEquals(trip.getId(), saved.getId());
    assertEquals(trip.getDestination(), saved.getDestination());
    assertEquals(trip.getDate(), saved.getDate());
    assertEquals(trip.getStartTime(), saved.getStartTime());
    assertEquals(trip.getEndTime(), saved.getEndTime());
    assertEquals(trip.getTransportationMode(), saved.getTransportationMode());
    assertEquals(List.of("bookmark-only"), saved.getBookmarkedActivities().stream()
            .map(Activity::getId).collect(Collectors.toList()));
    assertEquals(List.of("event-first", "event-second"), saved.getScheduledEvents().stream()
            .map(ScheduledEvent::getId).collect(Collectors.toList()));
    assertEquals(LocalTime.of(9, 0), saved.getScheduledEvents().get(0).getStartTime());
    assertEquals(LocalTime.of(10, 0), saved.getScheduledEvents().get(0).getEndTime());
    assertEquals(LocalTime.of(10, 0), saved.getScheduledEvents().get(1).getStartTime());
    assertEquals(LocalTime.of(11, 30), saved.getScheduledEvents().get(1).getEndTime());
}
```

- [ ] **Step 2: Add failure tests**

Add one test for no scheduled activities and one for an activity that cannot fit.
Both assert `presentFailure`, `repository.saved == null`, and no success output.

- [ ] **Step 3: Run the new test and verify failure**

Run:

```bash
./mvnw -Dtest=OptimizeItineraryInteractorTest test
```

Expected: test compilation fails because optimize workflow classes do not exist.

- [ ] **Step 4: Implement immutable input/output contracts**

`OptimizeItineraryInputData` contains a required trip ID.
`OptimizeItineraryOutputData` contains the updated `Trip` and success message.
The two boundaries use the signatures documented above.

- [ ] **Step 5: Implement first-pass valid schedule compaction**

Implement `OptimizeItineraryInteractor` so it:

```java
public void execute(OptimizeItineraryInputData inputData) {
    try {
        if (inputData == null || inputData.getTripId() == null
                || inputData.getTripId().trim().isEmpty()) {
            throw new IllegalArgumentException("Trip id is required");
        }
        Trip trip = trips.findById(inputData.getTripId())
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        List<ScheduledEvent> activities = trip.getScheduledEvents().stream()
                .filter(event -> event.getEventType() == EventType.ACTIVITY)
                .collect(Collectors.toList());
        if (activities.isEmpty()) {
            throw new IllegalArgumentException(
                    "Add activities to the Day Plan before optimizing");
        }
        List<ScheduledEvent> compacted = new ArrayList<ScheduledEvent>();
        LocalTime cursor = trip.getStartTime();
        for (ScheduledEvent existing : activities) {
            Activity activity = existing.getActivity();
            long duration = Duration.between(
                    existing.getStartTime(), existing.getEndTime()).toMinutes();
            if (activity == null || duration <= 0 || duration > Integer.MAX_VALUE) {
                throw new IllegalStateException("Scheduled activity duration is invalid");
            }
            LocalTime start = cursor.isBefore(activity.getOpeningTime())
                    ? activity.getOpeningTime() : cursor;
            LocalTime end = plusWithoutDayRollover(start, (int) duration);
            if (end == null || end.isAfter(activity.getClosingTime())
                    || end.isAfter(trip.getEndTime())) {
                throw new IllegalStateException(
                        "Current itinerary cannot be compacted inside trip and opening hours");
            }
            compacted.add(new ScheduledEvent(existing.getId(), activity, start, end,
                    EventType.ACTIVITY, existing.getNotes()));
            cursor = end;
        }
        Trip updated = trip.copyWithSchedule(compacted);
        Trip saved = trips.save(updated);
        output.presentSuccess(new OptimizeItineraryOutputData(
                saved, "Current itinerary compacted successfully"));
    } catch (IllegalArgumentException | IllegalStateException exception) {
        output.presentFailure(exception.getMessage());
    }
}
```

Use a helper that rejects `LocalTime` day rollover.

- [ ] **Step 6: Run optimize tests**

Run:

```bash
./mvnw -Dtest=OptimizeItineraryInteractorTest test
```

Expected: all optimize tests pass.

- [ ] **Step 7: Commit the use case**

```bash
git add src/main/java/closeai/application/usecases/OptimizeItinerary*.java \
  src/test/java/closeai/application/usecases/OptimizeItineraryInteractorTest.java
git commit -m "feat: add current itinerary compaction interactor"
```

### Task 2: Day Plan presentation path

**Files:**
- Create: `src/main/java/closeai/adapters/viewmodels/DayPlanState.java`
- Create: `src/main/java/closeai/adapters/viewmodels/DayPlanViewModel.java`
- Create: `src/main/java/closeai/adapters/controllers/OptimizeItineraryController.java`
- Create: `src/main/java/closeai/adapters/presenters/OptimizeItineraryPresenter.java`
- Test: `src/test/java/closeai/adapters/presenters/OptimizeItineraryPresenterTest.java`

**Interfaces:**
- Consumes: `OptimizeItineraryInputBoundary`.
- Produces: one observable `DayPlanState` shared by Day Plan and Calendar.

- [ ] **Step 1: Write the failing presenter test**

```java
@Test
void successAndFailureUpdateDayPlanState() {
    DayPlanViewModel viewModel = new DayPlanViewModel(
            new DayPlanState("trip-1", Collections.emptyList(), "", false));
    OptimizeItineraryPresenter presenter = new OptimizeItineraryPresenter(viewModel);
    Trip trip = sampleTripWithOneEvent();

    presenter.presentSuccess(new OptimizeItineraryOutputData(trip, "Compacted"));
    assertEquals(trip.getScheduledEvents(), viewModel.getState().getEvents());
    assertEquals("Compacted", viewModel.getState().getMessage());
    assertFalse(viewModel.getState().isError());

    presenter.presentFailure("Cannot compact");
    assertEquals("Cannot compact", viewModel.getState().getMessage());
    assertTrue(viewModel.getState().isError());
}
```

- [ ] **Step 2: Run the presenter test and verify failure**

Run:

```bash
./mvnw -Dtest=OptimizeItineraryPresenterTest test
```

Expected: compilation failure because presentation classes do not exist.

- [ ] **Step 3: Implement State and ViewModel**

`DayPlanState` stores trip ID, an unmodifiable event snapshot, message, and
error flag. `DayPlanViewModel` owns its own `PropertyChangeSupport` and fires a
`"state"` change when replaced.

- [ ] **Step 4: Implement controller and presenter**

```java
public final class OptimizeItineraryController {
    private final OptimizeItineraryInputBoundary interactor;
    private final String tripId;

    public void execute() {
        interactor.execute(new OptimizeItineraryInputData(tripId));
    }
}
```

The presenter updates the existing event snapshot on failure and replaces it
from output data on success.

- [ ] **Step 5: Run presenter and optimize tests**

Run:

```bash
./mvnw -Dtest=OptimizeItineraryPresenterTest,OptimizeItineraryInteractorTest test
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit the presentation path**

```bash
git add src/main/java/closeai/adapters/controllers/OptimizeItineraryController.java \
  src/main/java/closeai/adapters/presenters/OptimizeItineraryPresenter.java \
  src/main/java/closeai/adapters/viewmodels/DayPlanState.java \
  src/main/java/closeai/adapters/viewmodels/DayPlanViewModel.java \
  src/test/java/closeai/adapters/presenters/OptimizeItineraryPresenterTest.java
git commit -m "feat: present optimized itinerary to day plan"
```

### Task 3: Remaining ViewModels and States

**Files:**
- Create: `src/main/java/closeai/adapters/viewmodels/DashboardState.java`
- Create: `src/main/java/closeai/adapters/viewmodels/DashboardViewModel.java`
- Create: `src/main/java/closeai/adapters/viewmodels/SearchState.java`
- Create: `src/main/java/closeai/adapters/viewmodels/SearchViewModel.java`
- Create: `src/main/java/closeai/adapters/viewmodels/BookmarksState.java`
- Create: `src/main/java/closeai/adapters/viewmodels/BookmarksViewModel.java`
- Create: `src/main/java/closeai/adapters/viewmodels/TripOptionsState.java`
- Create: `src/main/java/closeai/adapters/viewmodels/TripOptionsViewModel.java`

**Interfaces:**
- Consumes: seeded domain snapshots from `AppBuilder`.
- Produces: focused observable state for each major feature area.

- [ ] **Step 1: Implement immutable State snapshots**

States expose only constructor-initialized values and defensive/unmodifiable
list snapshots. `DashboardState` includes destination/date/weather condition and
message. `TripOptionsState` includes destination/date/start/end/mode.

- [ ] **Step 2: Implement explicit ViewModels**

Each ViewModel directly owns:

```java
private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
private StateType state;

public StateType getState() { return state; }
public void setState(StateType state) {
    StateType old = this.state;
    this.state = Objects.requireNonNull(state);
    changes.firePropertyChange("state", old, state);
}
public void addPropertyChangeListener(PropertyChangeListener listener) {
    changes.addPropertyChangeListener(listener);
}
```

Do not add a generic ViewModel base.

- [ ] **Step 3: Compile**

Run:

```bash
./mvnw -DskipTests compile
```

Expected: build succeeds.

- [ ] **Step 4: Commit the ViewModels**

```bash
git add src/main/java/closeai/adapters/viewmodels
git commit -m "feat: add focused Swing view models and states"
```

### Task 4: Complete visible Swing dashboard

**Files:**
- Create: `src/main/java/closeai/adapters/views/SwingTheme.java`
- Create: `src/main/java/closeai/adapters/views/HeaderPanel.java`
- Create: `src/main/java/closeai/adapters/views/OverviewPanel.java`
- Create: `src/main/java/closeai/adapters/views/SearchPanel.java`
- Create: `src/main/java/closeai/adapters/views/BookmarksPanel.java`
- Create: `src/main/java/closeai/adapters/views/DayPlanPanel.java`
- Create: `src/main/java/closeai/adapters/views/TripOptionsPanel.java`
- Create: `src/main/java/closeai/adapters/views/PlannerPanel.java`
- Create: `src/main/java/closeai/adapters/views/CalendarDialog.java`
- Create: `src/main/java/closeai/adapters/views/CloseAIFrame.java`

**Interfaces:**
- Consumes: the five focused ViewModels and `OptimizeItineraryController`.
- Produces: a complete dashboard resembling the web prototype.

- [ ] **Step 1: Implement shared visual constants**

`SwingTheme` exposes the navy, blue, muted, panel, background, line, success,
and error colors plus consistent title/body fonts.

- [ ] **Step 2: Implement header and overview**

`HeaderPanel` listens to `DashboardViewModel`, displays CloseAI,
destination/date, and a disabled `Share (not wired)` button.

`OverviewPanel` listens to the same ViewModel, draws a simple map-style
placeholder with labeled mock pins, and displays a weather card explicitly
labeled as mock/preview data.

- [ ] **Step 3: Implement Search, Bookmarks, and Options**

Each panel renders its focused State. Controls are visible but disabled or show
the exact text `Not wired for this milestone`; they do not mutate State.

- [ ] **Step 4: Implement live Day Plan**

`DayPlanPanel` listens to `DayPlanViewModel`, rebuilds its event list, displays
presenter status, calls the optimize controller from the only active workflow,
and exposes a `setOpenCalendarAction(Runnable)` hook.

- [ ] **Step 5: Implement shared-state Calendar**

`CalendarDialog` receives the same `DayPlanViewModel` instance as
`DayPlanPanel`, listens for `"state"` changes, and rebuilds a read-only timeline
list. It contains no second schedule copy.

- [ ] **Step 6: Implement Planner and main frame**

`PlannerPanel` owns the four-tab `JTabbedPane`. `CloseAIFrame` owns header,
overview/planner split, and `CalendarDialog`; it connects the Day Plan Calendar
button to the dialog.

- [ ] **Step 7: Compile**

Run:

```bash
./mvnw -DskipTests compile
```

Expected: build succeeds.

- [ ] **Step 8: Commit the Views**

```bash
git add src/main/java/closeai/adapters/views
git commit -m "feat: add CloseAI Swing dashboard skeleton"
```

### Task 5: Compose Swing in AppBuilder and launch from Main

**Files:**
- Modify: `src/main/java/closeai/AppBuilder.java`
- Modify: `src/main/java/closeai/Main.java`

**Interfaces:**
- Preserves: `AppBuilder.build()`, `buildOffline()`, and `buildLive()`.
- Produces: `CloseAIFrame AppBuilder.buildSwingApplication()`.

- [ ] **Step 1: Add replaceable demo bootstrap**

In `AppBuilder`, create an offline `AppContainer`, a trip, three manually
scheduled activities via the existing add-to-plan use case, unrelated
bookmarks, and mock weather. Convert these to the five State snapshots.

- [ ] **Step 2: Compose optimize dependencies**

Create one `DayPlanViewModel`, one `OptimizeItineraryPresenter`, one
`OptimizeItineraryInteractor(app.trips, presenter)`, and one controller. Pass
the same Day Plan ViewModel to `DayPlanPanel` and `CloseAIFrame`/Calendar.

- [ ] **Step 3: Preserve the web prototype entry**

Move the existing HTTP startup code into a private `startWebPrototype()` method.
When `--web` is supplied, run that method. Otherwise build Swing.

- [ ] **Step 4: Launch on the EDT**

```java
public static void main(String[] args) throws Exception {
    if (args.length > 0 && "--web".equals(args[0])) {
        startWebPrototype();
        return;
    }
    SwingUtilities.invokeLater(() -> {
        CloseAIFrame frame = new AppBuilder().buildSwingApplication();
        frame.setVisible(true);
        System.out.println("CloseAI Swing dashboard launched on EDT: "
                + SwingUtilities.isEventDispatchThread());
    });
}
```

- [ ] **Step 5: Run the full suite**

Run:

```bash
./mvnw clean test
```

Expected: all ordinary tests pass; only the explicit live Open-Meteo test is
skipped.

- [ ] **Step 6: Commit composition**

```bash
git add src/main/java/closeai/AppBuilder.java src/main/java/closeai/Main.java
git commit -m "feat: launch composed Swing application"
```

### Task 6: Launch, inspect, and report milestone gaps

**Files:**
- Modify only if verification exposes a defect in files from Tasks 1-5.

**Interfaces:**
- Verifies: Main launch, visible dashboard, Calendar refresh, optimize flow.

- [ ] **Step 1: Run static checks**

```bash
git diff --check
git status --short --branch
```

Expected: no whitespace errors and no unintended files.

- [ ] **Step 2: Launch Swing**

Run:

```bash
./mvnw compile exec:java -Dexec.mainClass=closeai.Main
```

Expected console evidence:

```text
CloseAI Swing dashboard launched on EDT: true
```

- [ ] **Step 3: Inspect the UI**

Confirm header, map placeholder, weather card, four tabs, seeded data, disabled
placeholder actions, Day Plan, and Calendar are visible. Invoke Optimize and
confirm both Day Plan and Calendar show the same compacted schedule.

- [ ] **Step 4: Re-run tests after any verification fix**

```bash
./mvnw clean test
```

Expected: all ordinary tests pass.

- [ ] **Step 5: Report untested interactors and pair-review status**

List the eleven existing interactors without direct tests exactly as recorded
in the approved design. State that only Emily/Bianca, Alex/Raashid,
Shiyuan/Alex, and Raashid/Shiyuan are assigned review pairs; do not claim review
completion without evidence from those people.

- [ ] **Step 6: Final branch status**

```bash
git status --short --branch
git log --oneline --decorate -10
```

Expected: integration branch contains Bianca’s merge, the approved
specification/plan, and focused implementation commits; nothing is pushed.

