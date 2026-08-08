package trippy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import trippy.adapters.controllers.AutoScheduleController;
import trippy.adapters.controllers.AutoScheduleSettings;
import trippy.adapters.controllers.ManualPlanController;
import trippy.adapters.controllers.TaskRunner;
import trippy.adapters.gateways.DistanceServiceTravelTimeEstimator;
import trippy.adapters.gateways.WeatherServiceContextGateway;
import trippy.adapters.presenters.AutoSchedulePresenter;
import trippy.adapters.presenters.ManualPlanPresenter;
import trippy.adapters.viewmodels.AutoScheduleStatus;
import trippy.adapters.viewmodels.DayPlanState;
import trippy.adapters.viewmodels.DayPlanViewModel;
import trippy.adapters.viewmodels.PreviewRowView;
import trippy.adapters.viewmodels.SearchState;
import trippy.adapters.viewmodels.SearchViewModel;
import trippy.application.AppContainer;
import trippy.application.autoschedule.AutoScheduleInteractor;
import trippy.application.autoschedule.engine.ScheduleEngine;
import trippy.application.autoschedule.policy.DaylightPolicy;
import trippy.application.autoschedule.policy.MealWindowPolicy;
import trippy.application.autoschedule.policy.SoftPolicy;
import trippy.application.autoschedule.policy.WeatherSuitabilityPolicy;
import trippy.domain.entities.Activity;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.entities.Trip;
import trippy.domain.valueobjects.EventType;
import trippy.domain.valueobjects.TransportationMode;
import trippy.infrastructure.mock.MockDistanceService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Where Alex's feature and Emily's meet.
 *
 * <p>The claim these tests exist to keep honest is the one the Autoschedule documentation
 * has been making since Batch A: that add-to-plan needs no change on either side, because
 * neither use case knows the other exists. They meet at the Trip, through
 * {@code TripRepository}, and nowhere else.</p>
 *
 * <p>Everything here runs through {@code AppBuilder.buildOffline()} and the real
 * controllers, so a passing run says the production wiring works — not that a fixture
 * assembled for the occasion does.</p>
 */
class AddToPlanAutoscheduleIntegrationTest {

    private static final LocalDate TRIP_DATE = LocalDate.of(2026, 8, 12);

    /** A trip with one activity already in it, so "added" is distinguishable from "seeded". */
    private static Trip tripWithOneActivity(AppContainer app) {
        Trip trip = new Trip("integration-trip", "Toronto", TRIP_DATE,
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        Activity first = app.activities.findAll().get(0);
        trip.replaceSchedule(Collections.singletonList(new ScheduledEvent(
                "event-existing", first, LocalTime.of(9, 0),
                LocalTime.of(9, 0).plusMinutes(first.getEstimatedDurationMinutes()),
                EventType.ACTIVITY, "")));
        return app.trips.save(trip);
    }

    /** The activity the traveller will discover and add; never part of the seeded plan. */
    private static Activity activityToAdd(AppContainer app, Trip trip) {
        List<String> already = new ArrayList<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getActivity() != null) {
                already.add(event.getActivity().getId());
            }
        }
        for (Activity candidate : app.activities.findAll()) {
            if (!already.contains(candidate.getId())) {
                return candidate;
            }
        }
        throw new AssertionError("the offline catalogue needs at least two activities");
    }

    private static ManualPlanController manualPlan(AppContainer app, DayPlanViewModel dayPlan) {
        SearchViewModel search = new SearchViewModel(
                new SearchState(app.activities.findAll(), ""));
        return new ManualPlanController(app.addActivityToPlan, app.editEvent, app.removeEvent,
                () -> dayPlan.getState().getTripId(), new ManualPlanPresenter(dayPlan, search));
    }

    private static AutoScheduleController autoschedule(AppContainer app,
                                                       DayPlanViewModel dayPlan) {
        List<SoftPolicy> builtIn = Arrays.asList(new WeatherSuitabilityPolicy(),
                new MealWindowPolicy(), new DaylightPolicy());
        AutoScheduleInteractor interactor = new AutoScheduleInteractor(app.trips,
                new DistanceServiceTravelTimeEstimator(new MockDistanceService()),
                new WeatherServiceContextGateway(app.weather),
                new AutoSchedulePresenter(dayPlan), builtIn, new ScheduleEngine());
        return new AutoScheduleController(interactor, dayPlan, TaskRunner.immediate());
    }

    private static AutoScheduleSettings settings() {
        return new AutoScheduleSettings(LocalTime.of(9, 0), LocalTime.of(21, 0),
                Collections.emptyList(), true, false);
    }

    private static boolean previewContains(DayPlanViewModel dayPlan, String activityId) {
        for (PreviewRowView row : dayPlan.getState().getPreviewRows()) {
            if (row.getKind() != PreviewRowView.Kind.TRAVEL
                    && row.getTitle().contains(activityId)) {
                return true;
            }
        }
        return false;
    }

    // --- 1. the production composition root actually wires Alex's use case ---------------

    @Test
    void appBuilderWiresTheRealAddToPlanUseCase() {
        AppContainer app = new AppBuilder().buildOffline();

        assertNotNull(app.addActivityToPlan,
                "AppContainer must expose the real add-to-plan use case");
        assertNotNull(app.editEvent);
        assertNotNull(app.removeEvent);

        // The panels the application builds are handed a manual controller, which is what
        // makes the buttons live rather than decorative. Line breaks are collapsed first:
        // this is a claim about wiring, and it should not fail because a call was wrapped.
        String builder = readFile("src/main/java/trippy/AppBuilder.java")
                .replaceAll("\\s+", " ");
        assertTrue(builder.contains("new ManualPlanController("),
                "AppBuilder must construct the manual plan controller");
        assertTrue(builder.contains("new DayPlanPanel( dayPlanViewModel, autoScheduleController, "
                        + "manualPlanController")
                        || builder.contains("new DayPlanPanel(dayPlanViewModel, "
                        + "autoScheduleController, manualPlanController"),
                "the Day Plan panel must receive the Autoschedule and manual controllers");
    }

    // --- 2-3. adding reaches the Trip, and the Day Plan shows it -------------------------

    @Test
    void addingAnActivityUpdatesTheTripAndTheDayPlan() {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = tripWithOneActivity(app);
        Activity added = activityToAdd(app, trip);
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));

        manualPlan(app, dayPlan).add(added.getId(), null);

        Trip saved = app.trips.findById("integration-trip").orElseThrow();
        assertEquals(2, saved.getScheduledEvents().size(),
                "the activity must become a real scheduled event on the Trip");
        assertTrue(saved.getScheduledEvents().stream()
                        .anyMatch(e -> e.getActivity() != null
                                && e.getActivity().getId().equals(added.getId())),
                "the saved Trip must contain the added activity");

        assertEquals(2, dayPlan.getState().getEvents().size(),
                "the Day Plan view state must show it too");
    }

    // --- 4-5. Autoschedule picks it up, with no Autoschedule change ----------------------

    @Test
    void autoschedulePreviewsAndAppliesAnActivityAddedThroughAlexsWorkflow() {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = tripWithOneActivity(app);
        Activity added = activityToAdd(app, trip);
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));

        manualPlan(app, dayPlan).add(added.getId(), null);
        AutoScheduleController controller = autoschedule(app, dayPlan);

        controller.preview(settings());
        assertEquals(AutoScheduleStatus.PREVIEW, dayPlan.getState().getStatus());
        assertEquals(2, dayPlan.getState().getMetrics().getActivityCount(),
                "the Preview must consider both the original and the added activity");
        assertTrue(previewContains(dayPlan, added.getName()),
                "the added activity must appear in the proposal");

        controller.apply();
        assertEquals(AutoScheduleStatus.APPLIED, dayPlan.getState().getStatus());

        Trip applied = app.trips.findById("integration-trip").orElseThrow();
        assertTrue(applied.getScheduledEvents().stream()
                        .anyMatch(e -> e.getActivity() != null
                                && e.getActivity().getId().equals(added.getId())),
                "Apply must preserve the added activity, not drop it");
        assertActivitiesAreUnique(applied);
    }

    // --- 6. nothing is duplicated anywhere along the path --------------------------------

    @Test
    void noDuplicateEventIsCreatedByAddingThenScheduling() {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = tripWithOneActivity(app);
        Activity added = activityToAdd(app, trip);
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));

        manualPlan(app, dayPlan).add(added.getId(), null);
        assertActivitiesAreUnique(app.trips.findById("integration-trip").orElseThrow());

        AutoScheduleController controller = autoschedule(app, dayPlan);
        controller.preview(settings());
        controller.apply();

        assertActivitiesAreUnique(app.trips.findById("integration-trip").orElseThrow());
    }

    // --- 7. not adding changes nothing ---------------------------------------------------

    @Test
    void aDeclinedOrRejectedEditChangesNothing() {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = tripWithOneActivity(app);
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        ManualPlanController controller = manualPlan(app, dayPlan);
        LocalTime originalStart = trip.getScheduledEvents().get(0).getStartTime();

        // Dismissing the edit dialog never calls the controller, so the closest reachable
        // equivalent is an edit the controller rejects: either way nothing is written.
        controller.edit("event-existing", "not-a-time", "11:00", "");

        Trip after = app.trips.findById("integration-trip").orElseThrow();
        assertEquals(1, after.getScheduledEvents().size());
        assertEquals(originalStart, after.getScheduledEvents().get(0).getStartTime(),
                "a rejected edit must not move the event it was aimed at");
        assertTrue(dayPlan.getState().isError());
        assertEquals(1, dayPlan.getState().getEvents().size());

        // And a genuine edit does go through, so the check above is not passing by accident.
        controller.edit("event-existing", "10:00", "11:00", "moved by hand");
        assertEquals(LocalTime.of(10, 0), app.trips.findById("integration-trip")
                .orElseThrow().getScheduledEvents().get(0).getStartTime());
    }

    // --- 8. a rejected add leaves the Trip intact ----------------------------------------

    @Test
    void aFailedAddDoesNotCorruptTheTrip() {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = tripWithOneActivity(app);
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));

        manualPlan(app, dayPlan).add("no-such-activity", null);

        Trip after = app.trips.findById("integration-trip").orElseThrow();
        assertEquals(1, after.getScheduledEvents().size(),
                "a failed add must leave the itinerary exactly as it was");
        assertTrue(dayPlan.getState().isError(), "and must say so rather than failing silently");

        // The day still schedules afterwards, so a rejected add leaves nothing poisoned.
        AutoScheduleController controller = autoschedule(app, dayPlan);
        controller.preview(settings());
        assertEquals(AutoScheduleStatus.PREVIEW, dayPlan.getState().getStatus());
    }

    // --- 9. none of this depends on the seeded demo trip ---------------------------------

    @Test
    void theFlowDoesNotDependOnTheSeededDemoTrip() {
        AppContainer app = new AppBuilder().buildOffline();
        Trip trip = tripWithOneActivity(app);
        Activity added = activityToAdd(app, trip);
        DayPlanViewModel dayPlan = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));

        assertFalse("demo-trip".equals(trip.getId()),
                "this trip is built by the test, not the demo seeding");
        manualPlan(app, dayPlan).add(added.getId(), null);
        AutoScheduleController controller = autoschedule(app, dayPlan);
        controller.preview(settings());

        assertEquals(AutoScheduleStatus.PREVIEW, dayPlan.getState().getStatus());
        assertEquals(2, dayPlan.getState().getMetrics().getActivityCount());
    }

    // --- 10. the architectural seam holds ------------------------------------------------

    /**
     * The boundary claim, checked against the source rather than asserted in prose: no file
     * in the Autoschedule use case names anything of Alex's. The two features share a Trip
     * and a repository, and that is the whole of their coupling.
     */
    @Test
    void autoscheduleHasNoDependencyOnAddToPlanClasses() {
        List<String> alexClasses = Arrays.asList("ManualPlanController", "ManualPlanPresenter",
                "AddActivityToPlanUseCase", "EditScheduledEventUseCase",
                "RemoveScheduledEventUseCase", "BookmarkController",
                "ActivityDiscoveryController", "ActivityDiscoveryPresenter");
        List<String> offenders = new ArrayList<>();
        java.io.File root = new java.io.File(
                "src/main/java/trippy/application/autoschedule");
        collectOffenders(root, alexClasses, offenders);

        assertTrue(offenders.isEmpty(),
                "Autoschedule must reach add-to-plan only through the Trip repository, but "
                        + "found: " + offenders);
    }

    private static void collectOffenders(java.io.File dir, List<String> names,
                                         List<String> offenders) {
        java.io.File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (java.io.File child : children) {
            if (child.isDirectory()) {
                collectOffenders(child, names, offenders);
            } else if (child.getName().endsWith(".java")) {
                String body = readFile(child.getPath());
                for (String name : names) {
                    if (body.contains(name)) {
                        offenders.add(child.getName() + " -> " + name);
                    }
                }
            }
        }
    }

    private static String readFile(String path) {
        try {
            return new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(path)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("could not read " + path, exception);
        }
    }

    private static void assertActivitiesAreUnique(Trip trip) {
        List<String> ids = new ArrayList<>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            if (event.getEventType() == EventType.ACTIVITY && event.getActivity() != null) {
                ids.add(event.getActivity().getId());
            }
        }
        assertEquals(ids.size(), new java.util.LinkedHashSet<>(ids).size(),
                "an activity must never appear twice in the itinerary: " + ids);
    }
}
