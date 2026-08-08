package trippy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import trippy.adapters.controllers.AutoScheduleController;
import trippy.adapters.controllers.AutoScheduleSettings;
import trippy.adapters.controllers.TaskRunner;
import trippy.adapters.gateways.DistanceServiceTravelTimeEstimator;
import trippy.adapters.gateways.WeatherServiceContextGateway;
import trippy.adapters.presenters.AutoSchedulePresenter;
import trippy.adapters.viewmodels.AutoScheduleStatus;
import trippy.adapters.viewmodels.CalendarViewModel;
import trippy.adapters.viewmodels.DashboardState;
import trippy.adapters.viewmodels.DashboardViewModel;
import trippy.adapters.viewmodels.DayPlanState;
import trippy.adapters.viewmodels.DayPlanViewModel;
import trippy.adapters.views.TrippyFrame;
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
import trippy.domain.valueobjects.ActivityCategory;
import trippy.domain.valueobjects.EventType;
import trippy.domain.valueobjects.IndoorOutdoorType;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * End-to-end checks over the wiring: one Autoschedule path exists, the replaced mockup is
 * gone, and a schedule reaches the Calendar only once it has been applied.
 */
class AutoScheduleWiringIntegrationTest {

    private static Activity activity(String id, int openHour, int closeHour) {
        return new Activity(id, "Name of " + id, ActivityCategory.MUSEUM,
                new Location(43.65 + Math.random() / 1000, -79.38, id), 4.5, 60,
                LocalTime.of(openHour, 0), LocalTime.of(closeHour, 0),
                IndoorOutdoorType.INDOOR, "none");
    }

    private static Trip tripWithActivities(String id, int count) {
        Trip trip = new Trip(id, "Toronto", LocalDate.of(2026, 8, 12),
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        List<ScheduledEvent> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalTime start = LocalTime.of(9 + i * 3, 0);
            events.add(new ScheduledEvent("event-" + i, activity("a" + i, 9, 21),
                    start, start.plusMinutes(60), EventType.ACTIVITY, ""));
        }
        trip.replaceSchedule(events);
        return trip;
    }

    /** Builds the same slice AppBuilder does, against an injected trip. */
    private static AutoScheduleController wire(AppContainer app, DayPlanViewModel viewModel) {
        List<SoftPolicy> builtIn = Arrays.asList(new WeatherSuitabilityPolicy(),
                new MealWindowPolicy(), new DaylightPolicy());
        AutoScheduleInteractor interactor = new AutoScheduleInteractor(app.trips,
                new DistanceServiceTravelTimeEstimator(
                        new trippy.infrastructure.mock.MockDistanceService()),
                new WeatherServiceContextGateway(app.weather),
                new AutoSchedulePresenter(viewModel), builtIn, new ScheduleEngine());
        return new AutoScheduleController(interactor, viewModel, TaskRunner.immediate());
    }

    private static AutoScheduleSettings defaultSettings() {
        return new AutoScheduleSettings(LocalTime.of(9, 0), LocalTime.of(21, 0),
                Collections.emptyList(), true, true);
    }

    @Test
    void theFrameOffersAutoscheduleAndNoLongerOffersTheOldMockup() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppBuilder builder = new AppBuilder();
            TrippyFrame frame = builder.buildSwingApplication(builder.buildOffline());

            List<AbstractButton> autoschedule = findButtons(frame, "Autoschedule");
            assertEquals(1, autoschedule.size(),
                    "there should be exactly one way into Autoschedule");
            assertNull(findButton(frame, "Optimize Itinerary"));
            assertNull(findButton(frame, "Edit (not wired)"));
            frame.dispose();
        });
    }

    @Test
    void aPreviewLeavesBothTheRepositoryAndTheCalendarUntouched() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(tripWithActivities("trip-1", 3));

        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        CalendarViewModel calendar = new CalendarViewModel(
                new DashboardViewModel(new DashboardState("Toronto",
                        LocalDate.of(2026, 8, 12), "", "")),
                viewModel, () -> LocalDate.of(2026, 8, 12));

        wire(app, viewModel).preview(defaultSettings());

        assertEquals(AutoScheduleStatus.PREVIEW, viewModel.getState().getStatus());
        assertFalse(viewModel.getState().getPreviewRows().isEmpty());
        assertEquals(LocalTime.of(9, 0),
                app.trips.findById("trip-1").orElseThrow().getScheduledEvents().get(0).getStartTime(),
                "the stored itinerary must not move until Apply");
        assertEquals(3, calendar.getState().getEvents().size());
        assertEquals(LocalTime.of(9, 0), calendar.getState().getEvents().get(0).getStartTime(),
                "the Calendar must not show an unapplied proposal");
    }

    @Test
    void applyingSavesTheScheduleAndTheCalendarFollows() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(tripWithActivities("trip-1", 3));

        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        CalendarViewModel calendar = new CalendarViewModel(
                new DashboardViewModel(new DashboardState("Toronto",
                        LocalDate.of(2026, 8, 12), "", "")),
                viewModel, () -> LocalDate.of(2026, 8, 12));
        AutoScheduleController controller = wire(app, viewModel);

        controller.preview(defaultSettings());
        int proposedRows = viewModel.getState().getPreviewRows().size();
        controller.apply();

        assertEquals(AutoScheduleStatus.APPLIED, viewModel.getState().getStatus());
        Trip saved = app.trips.findById("trip-1").orElseThrow();
        assertEquals(proposedRows, saved.getScheduledEvents().size());
        assertEquals(proposedRows, calendar.getState().getEvents().size(),
                "the Calendar observes the applied schedule through the shared view model");
        assertTrue(viewModel.getState().getPreviewRows().isEmpty());
    }

    @Test
    void cancellingLeavesEverythingExactlyAsItWas() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(tripWithActivities("trip-1", 3));

        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        AutoScheduleController controller = wire(app, viewModel);

        controller.preview(defaultSettings());
        controller.cancel();

        assertEquals(AutoScheduleStatus.IDLE, viewModel.getState().getStatus());
        assertEquals(LocalTime.of(9, 0),
                app.trips.findById("trip-1").orElseThrow().getScheduledEvents().get(0).getStartTime());
    }

    @Test
    void aPinnedActivityKeepsItsTimeThroughTheWholeFlow() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(tripWithActivities("trip-1", 3));

        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        AutoScheduleController controller = wire(app, viewModel);
        LocalTime pinnedStart = trip.getScheduledEvents().get(2).getStartTime();

        controller.toggleLock("event-2");
        controller.preview(defaultSettings());
        controller.apply();

        ScheduledEvent pinned = app.trips.findById("trip-1").orElseThrow()
                .getScheduledEvents().stream()
                .filter(event -> event.getId().equals("event-2"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(pinnedStart, pinned.getStartTime(),
                "a pinned activity must survive the whole preview-and-apply flow");
    }

    @Test
    void aSeededTripAndAnOrdinaryTripBehaveIdenticallyThroughTheBoundary() {
        AppBuilder builder = new AppBuilder();

        AppContainer seededApp = builder.buildOffline();
        Trip seeded = seededApp.trips.save(tripWithActivities("seeded", 3));
        DayPlanViewModel seededView = new DayPlanViewModel(new DayPlanState(
                seeded.getId(), seeded.getScheduledEvents(), "", false));
        wire(seededApp, seededView).preview(defaultSettings());

        AppContainer ordinaryApp = builder.buildOffline();
        Trip ordinary = ordinaryApp.trips.save(tripWithActivities("ordinary", 3));
        DayPlanViewModel ordinaryView = new DayPlanViewModel(new DayPlanState(
                ordinary.getId(), ordinary.getScheduledEvents(), "", false));
        wire(ordinaryApp, ordinaryView).preview(defaultSettings());

        assertEquals(seededView.getState().getStatus(), ordinaryView.getState().getStatus());
        assertEquals(seededView.getState().getPreviewRows().size(),
                ordinaryView.getState().getPreviewRows().size(),
                "Autoschedule only reads the Day Plan, so where the trip came from is irrelevant");
        assertEquals(seededView.getState().getObjectiveSummary(),
                ordinaryView.getState().getObjectiveSummary());
    }

    @Test
    void anEmptyDayPlanIsExplainedRatherThanCrashing() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip empty = app.trips.save(new Trip("trip-1", "Toronto", LocalDate.of(2026, 8, 12),
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING));

        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                empty.getId(), empty.getScheduledEvents(), "", false));
        wire(app, viewModel).preview(defaultSettings());

        assertEquals(AutoScheduleStatus.FAILURE, viewModel.getState().getStatus());
        assertTrue(viewModel.getState().getMessage().contains("Add activities"));
    }

    @Test
    void theBuiltFrameWiresAutoscheduleToTheSharedDayPlanState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppBuilder builder = new AppBuilder();
            TrippyFrame frame = builder.buildSwingApplication(builder.buildOffline());

            assertNotNull(frame.getDayPlanPanel());
            assertEquals(frame.getDayPlanPanel().getViewModel(),
                    frame.getCalendarDialog().getViewModel(),
                    "Day Plan and Calendar must observe the same state");
            frame.dispose();
        });
    }

    private static AbstractButton findButton(Component component, String text) {
        List<AbstractButton> found = findButtons(component, text);
        return found.isEmpty() ? null : found.get(0);
    }

    private static List<AbstractButton> findButtons(Component component, String text) {
        List<AbstractButton> found = new ArrayList<>();
        collectButtons(component, text, found);
        return found;
    }

    private static void collectButtons(Component component, String text,
                                       List<AbstractButton> found) {
        if (component instanceof AbstractButton
                && text.equals(((AbstractButton) component).getText())) {
            found.add((AbstractButton) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectButtons(child, text, found);
            }
        }
    }
}
