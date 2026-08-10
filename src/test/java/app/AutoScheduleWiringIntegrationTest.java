package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import interface_adapter.controllers.AutoScheduleController;
import interface_adapter.controllers.AutoScheduleSettings;
import interface_adapter.controllers.TaskRunner;
import interface_adapter.gateways.DistanceServiceTravelTimeEstimator;
import interface_adapter.gateways.WeatherServiceContextGateway;
import interface_adapter.presenters.AutoSchedulePresenter;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.CalendarViewModel;
import interface_adapter.viewmodels.DashboardState;
import interface_adapter.viewmodels.DashboardViewModel;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import use_case.autoschedule.AutoScheduleInteractor;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.SoftPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import views.TrippyFrame;

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
        final Trip trip = new Trip(id, "Toronto", LocalDate.of(2026, 8, 12),
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        final List<ScheduledEvent> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final LocalTime start = LocalTime.of(9 + i * 3, 0);
            events.add(new ScheduledEvent("event-" + i, activity("a" + i, 9, 21),
                    start, start.plusMinutes(60), EventType.ACTIVITY, ""));
        }
        trip.replaceSchedule(events);
        return trip;
    }

    /** Builds the same slice AppBuilder does, against an injected trip. */
    private static AutoScheduleController wire(AppContainer app, DayPlanViewModel viewModel) {
        final List<SoftPolicy> builtIn = Arrays.asList(new WeatherSuitabilityPolicy(),
                new MealWindowPolicy(), new DaylightPolicy());
        final AutoScheduleInteractor interactor = new AutoScheduleInteractor(app.trips,
                new DistanceServiceTravelTimeEstimator(
                        new interface_adapter.mock.MockDistanceService()),
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
            final AppBuilder builder = new AppBuilder();
            final TrippyFrame frame = builder.buildSwingApplication(builder.buildOffline());

            final List<AbstractButton> autoschedule = findButtons(frame, "Autoschedule");
            assertEquals(1, autoschedule.size(),
                    "there should be exactly one way into Autoschedule");
            assertNull(findButton(frame, "Optimize Itinerary"));
            assertNull(findButton(frame, "Edit (not wired)"));
            frame.dispose();
        });
    }

    @Test
    void aPreviewLeavesBothTheRepositoryAndTheCalendarUntouched() {
        final AppBuilder builder = new AppBuilder();
        final AppContainer app = builder.buildOffline();
        final Trip trip = app.trips.save(tripWithActivities("trip-1", 3));

        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        final CalendarViewModel calendar = new CalendarViewModel(
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
        final AppBuilder builder = new AppBuilder();
        final AppContainer app = builder.buildOffline();
        final Trip trip = app.trips.save(tripWithActivities("trip-1", 3));

        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        final CalendarViewModel calendar = new CalendarViewModel(
                new DashboardViewModel(new DashboardState("Toronto",
                        LocalDate.of(2026, 8, 12), "", "")),
                viewModel, () -> LocalDate.of(2026, 8, 12));
        final AutoScheduleController controller = wire(app, viewModel);

        controller.preview(defaultSettings());
        final int proposedRows = viewModel.getState().getPreviewRows().size();
        controller.apply();

        assertEquals(AutoScheduleStatus.APPLIED, viewModel.getState().getStatus());
        final Trip saved = app.trips.findById("trip-1").orElseThrow();
        assertEquals(proposedRows, saved.getScheduledEvents().size());
        assertEquals(proposedRows, calendar.getState().getEvents().size(),
                "the Calendar observes the applied schedule through the shared view model");
        assertTrue(viewModel.getState().getPreviewRows().isEmpty());
    }

    @Test
    void cancellingLeavesEverythingExactlyAsItWas() {
        final AppBuilder builder = new AppBuilder();
        final AppContainer app = builder.buildOffline();
        final Trip trip = app.trips.save(tripWithActivities("trip-1", 3));

        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        final AutoScheduleController controller = wire(app, viewModel);

        controller.preview(defaultSettings());
        controller.cancel();

        assertEquals(AutoScheduleStatus.IDLE, viewModel.getState().getStatus());
        assertEquals(LocalTime.of(9, 0),
                app.trips.findById("trip-1").orElseThrow().getScheduledEvents().get(0).getStartTime());
    }

    @Test
    void aPinnedActivityKeepsItsTimeThroughTheWholeFlow() {
        final AppBuilder builder = new AppBuilder();
        final AppContainer app = builder.buildOffline();
        final Trip trip = app.trips.save(tripWithActivities("trip-1", 3));

        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        final AutoScheduleController controller = wire(app, viewModel);
        final LocalTime pinnedStart = trip.getScheduledEvents().get(2).getStartTime();

        controller.toggleLock("event-2");
        controller.preview(defaultSettings());
        controller.apply();

        final ScheduledEvent pinned = app.trips.findById("trip-1").orElseThrow()
                .getScheduledEvents().stream()
                .filter(event -> event.getId().equals("event-2"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(pinnedStart, pinned.getStartTime(),
                "a pinned activity must survive the whole preview-and-apply flow");
    }

    @Test
    void aSeededTripAndAnOrdinaryTripBehaveIdenticallyThroughTheBoundary() {
        final AppBuilder builder = new AppBuilder();

        final AppContainer seededApp = builder.buildOffline();
        final Trip seeded = seededApp.trips.save(tripWithActivities("seeded", 3));
        final DayPlanViewModel seededView = new DayPlanViewModel(new DayPlanState(
                seeded.getId(), seeded.getScheduledEvents(), "", false));
        wire(seededApp, seededView).preview(defaultSettings());

        final AppContainer ordinaryApp = builder.buildOffline();
        final Trip ordinary = ordinaryApp.trips.save(tripWithActivities("ordinary", 3));
        final DayPlanViewModel ordinaryView = new DayPlanViewModel(new DayPlanState(
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
        final AppBuilder builder = new AppBuilder();
        final AppContainer app = builder.buildOffline();
        final Trip empty = app.trips.save(new Trip("trip-1", "Toronto", LocalDate.of(2026, 8, 12),
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING));

        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                empty.getId(), empty.getScheduledEvents(), "", false));
        wire(app, viewModel).preview(defaultSettings());

        assertEquals(AutoScheduleStatus.FAILURE, viewModel.getState().getStatus());
        assertTrue(viewModel.getState().getMessage().contains("Add activities"));
    }

    @Test
    void theBuiltFrameWiresAutoscheduleToTheSharedDayPlanState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final AppBuilder builder = new AppBuilder();
            final TrippyFrame frame = builder.buildSwingApplication(builder.buildOffline());

            assertNotNull(frame.getDayPlanPanel());
            assertEquals(frame.getDayPlanPanel().getViewModel(),
                    frame.getCalendarDialog().getViewModel(),
                    "Day Plan and Calendar must observe the same state");
            frame.dispose();
        });
    }

    private static AbstractButton findButton(Component component, String text) {
        final List<AbstractButton> found = findButtons(component, text);
        return found.isEmpty() ? null : found.get(0);
    }

    private static List<AbstractButton> findButtons(Component component, String text) {
        final List<AbstractButton> found = new ArrayList<>();
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
