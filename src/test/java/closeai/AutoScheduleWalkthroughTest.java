package closeai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.adapters.controllers.AutoScheduleController;
import closeai.adapters.controllers.AutoScheduleSettings;
import closeai.adapters.controllers.TaskRunner;
import closeai.adapters.gateways.DistanceServiceTravelTimeEstimator;
import closeai.adapters.gateways.WeatherServiceContextGateway;
import closeai.adapters.presenters.AutoSchedulePresenter;
import closeai.adapters.viewmodels.AutoScheduleStatus;
import closeai.adapters.viewmodels.CalendarViewModel;
import closeai.adapters.viewmodels.DashboardState;
import closeai.adapters.viewmodels.DashboardViewModel;
import closeai.adapters.viewmodels.DayPlanState;
import closeai.adapters.viewmodels.DayPlanViewModel;
import closeai.adapters.viewmodels.PreviewRowView;
import closeai.application.AppContainer;
import closeai.application.autoschedule.AutoScheduleInteractor;
import closeai.application.autoschedule.engine.ScheduleEngine;
import closeai.application.autoschedule.policy.DaylightPolicy;
import closeai.application.autoschedule.policy.MealWindowPolicy;
import closeai.application.autoschedule.policy.SoftPolicy;
import closeai.application.autoschedule.policy.WeatherSuitabilityPolicy;
import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import closeai.infrastructure.mock.MockDistanceService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The demo, executed as a test.
 *
 * <p>This walks the same steps the presentation will: an inefficient day, settings, a
 * pin, a preview, cancel, apply, a conflict. It runs through the production wiring the
 * application builds, so if the walkthrough passes here the demo is not relying on
 * anything assembled specially for it.</p>
 */
class AutoScheduleWalkthroughTest {

    private static final LocalDate TRIP_DATE = LocalDate.of(2026, 8, 12);

    private static Activity activity(String id, String name, ActivityCategory category,
                                     IndoorOutdoorType exposure, double lat, double lng,
                                     int openHour, int closeHour) {
        return new Activity(id, name, category, new Location(lat, lng, id), 4.5, 60,
                LocalTime.of(openHour, 0), LocalTime.of(closeHour, 0), exposure, "none");
    }

    /**
     * A deliberately poor day: the museum is scheduled after it closes, the two far-apart
     * places sit next to each other, and lunch is at half past three.
     */
    private static Trip inefficientDay() {
        Trip trip = new Trip("demo-trip", "Toronto", TRIP_DATE,
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        List<ScheduledEvent> events = new ArrayList<>();
        events.add(new ScheduledEvent("event-park",
                activity("park", "High Park", ActivityCategory.OUTDOOR,
                        IndoorOutdoorType.OUTDOOR, 43.6465, -79.4637, 6, 22),
                LocalTime.of(9, 0), LocalTime.of(10, 0), EventType.ACTIVITY, ""));
        events.add(new ScheduledEvent("event-museum",
                activity("museum", "Royal Ontario Museum", ActivityCategory.MUSEUM,
                        IndoorOutdoorType.INDOOR, 43.6677, -79.3948, 10, 17),
                LocalTime.of(11, 0), LocalTime.of(12, 0), EventType.ACTIVITY, ""));
        events.add(new ScheduledEvent("event-lunch",
                activity("lunch", "St Lawrence Market", ActivityCategory.FOOD,
                        IndoorOutdoorType.INDOOR, 43.6487, -79.3716, 9, 19),
                LocalTime.of(15, 30), LocalTime.of(16, 30), EventType.ACTIVITY, ""));
        trip.replaceSchedule(events);
        return trip;
    }

    private static AutoScheduleController wire(AppContainer app, DayPlanViewModel viewModel) {
        List<SoftPolicy> builtIn = Arrays.asList(new WeatherSuitabilityPolicy(),
                new MealWindowPolicy(), new DaylightPolicy());
        AutoScheduleInteractor interactor = new AutoScheduleInteractor(app.trips,
                new DistanceServiceTravelTimeEstimator(new MockDistanceService()),
                new WeatherServiceContextGateway(app.weather),
                new AutoSchedulePresenter(viewModel), builtIn, new ScheduleEngine());
        return new AutoScheduleController(interactor, viewModel, TaskRunner.immediate());
    }

    private static AutoScheduleSettings settings(boolean keepOrder,
                                                 AutoScheduleSettings.Window... unavailable) {
        return new AutoScheduleSettings(LocalTime.of(9, 0), LocalTime.of(21, 0),
                TransportationMode.WALKING, Arrays.asList(unavailable), keepOrder);
    }

    @Test
    void theWholeDemoRunsThroughTheProductionWiring() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(inefficientDay());

        // 1. A populated Day Plan opens.
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        CalendarViewModel calendar = new CalendarViewModel(
                new DashboardViewModel(new DashboardState("Toronto", TRIP_DATE, "", "")),
                viewModel, () -> TRIP_DATE);
        AutoScheduleController controller = wire(app, viewModel);
        assertEquals(3, viewModel.getState().getEvents().size());
        assertEquals(AutoScheduleStatus.IDLE, viewModel.getState().getStatus());

        // 2-3. Settings are read, including a pin and an unavailable period.
        controller.toggleLock("event-museum");
        assertTrue(viewModel.getState().getLockedEventIds().contains("event-museum"));

        // 4-5. Preview runs, and the real itinerary is untouched while it is on screen.
        controller.preview(settings(true,
                new AutoScheduleSettings.Window(LocalTime.of(13, 0), LocalTime.of(14, 0))));
        DayPlanState previewing = viewModel.getState();
        assertEquals(AutoScheduleStatus.PREVIEW, previewing.getStatus());
        assertEquals(LocalTime.of(9, 0),
                app.trips.findById("demo-trip").orElseThrow()
                        .getScheduledEvents().get(0).getStartTime(),
                "the stored plan must not move during a preview");
        assertEquals(LocalTime.of(9, 0),
                calendar.getState().getEvents().get(0).getStartTime(),
                "the Calendar must not show an unapplied proposal");

        // 6. Metrics, an objective summary and reasons are all present.
        assertNotNull(previewing.getMetrics());
        assertEquals(3, previewing.getMetrics().getActivityCount());
        assertTrue(previewing.getObjectiveSummary().contains("less travel"));
        assertTrue(previewing.getPreviewRows().stream()
                        .anyMatch(row -> !row.getAllReasons().isEmpty()),
                "at least one row should explain itself");

        // The pinned museum kept its time; nothing sits in the unavailable hour.
        PreviewRowView museum = previewing.getPreviewRows().stream()
                .filter(row -> row.getEventId().equals("event-museum"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(LocalTime.of(11, 0), museum.getStart());
        assertTrue(museum.isLocked());
        for (PreviewRowView row : previewing.getPreviewRows()) {
            boolean insideBlock = row.getStart().isBefore(LocalTime.of(14, 0))
                    && row.getEnd().isAfter(LocalTime.of(13, 0));
            assertFalse(insideBlock, row.getTitle() + " overlaps the unavailable hour");
        }

        // 7. Cancel changes nothing at all.
        controller.cancel();
        assertEquals(AutoScheduleStatus.IDLE, viewModel.getState().getStatus());
        assertEquals(LocalTime.of(9, 0),
                app.trips.findById("demo-trip").orElseThrow()
                        .getScheduledEvents().get(0).getStartTime());

        // 8. Re-run and Apply: the Trip and the Calendar both follow.
        controller.preview(settings(true));
        int proposedRows = viewModel.getState().getPreviewRows().size();
        controller.apply();
        assertEquals(AutoScheduleStatus.APPLIED, viewModel.getState().getStatus());
        assertEquals(proposedRows,
                app.trips.findById("demo-trip").orElseThrow().getScheduledEvents().size());
        assertEquals(proposedRows, calendar.getState().getEvents().size());
        assertEquals(LocalTime.of(11, 0),
                app.trips.findById("demo-trip").orElseThrow().getScheduledEvents().stream()
                        .filter(event -> event.getId().equals("event-museum"))
                        .findFirst().orElseThrow(AssertionError::new).getStartTime(),
                "the pin survived all the way to the saved itinerary");
    }

    @Test
    void aStalePreviewIsRefused() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(inefficientDay());
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        AutoScheduleController controller = wire(app, viewModel);

        controller.preview(settings(true));

        // Something else edits the Day Plan after the preview was produced.
        Trip edited = inefficientDay();
        List<ScheduledEvent> moved = new ArrayList<>(edited.getScheduledEvents());
        moved.set(0, new ScheduledEvent(moved.get(0).getId(), moved.get(0).getActivity(),
                LocalTime.of(10, 0), LocalTime.of(11, 0), EventType.ACTIVITY, ""));
        app.trips.save(edited.copyWithSchedule(moved));

        controller.apply();

        assertEquals(AutoScheduleStatus.FAILURE, viewModel.getState().getStatus());
        assertTrue(viewModel.getState().getMessage().contains("changed after this Preview"));
    }

    @Test
    void anImpossiblePinProducesAStructuredConflict() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(inefficientDay());
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        AutoScheduleController controller = wire(app, viewModel);

        // The museum is pinned at 11:00, inside an hour the traveller is unavailable.
        controller.toggleLock("event-museum");
        controller.preview(settings(true,
                new AutoScheduleSettings.Window(LocalTime.of(10, 30), LocalTime.of(12, 0))));

        DayPlanState state = viewModel.getState();
        assertEquals(AutoScheduleStatus.CONFLICT, state.getStatus());
        assertTrue(state.isError());
        assertTrue(state.getMessage().contains("Royal Ontario Museum"),
                "the message should name what blocked the day: " + state.getMessage());
        assertTrue(state.getMessage().contains("not changed"));
        assertEquals(3, app.trips.findById("demo-trip").orElseThrow()
                .getScheduledEvents().size());
    }

    @Test
    void anEmptyDayPlanAndAMissingTripBothStaySafe() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip empty = app.trips.save(new Trip("demo-trip", "Toronto", TRIP_DATE,
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING));

        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                empty.getId(), empty.getScheduledEvents(), "", false));
        wire(app, viewModel).preview(settings(true));
        assertEquals(AutoScheduleStatus.FAILURE, viewModel.getState().getStatus());
        assertTrue(viewModel.getState().getMessage().contains("Add activities"));

        DayPlanViewModel noTrip = new DayPlanViewModel(
                new DayPlanState("", Collections.emptyList(), "", false));
        wire(app, noTrip).preview(settings(true));
        assertEquals(AutoScheduleStatus.IDLE, noTrip.getState().getStatus(),
                "with no trip there is nothing to do and nothing to report");
    }

    @Test
    void theWeatherCaveatIsShownWhenTheForecastCoversTheWholeDay() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(inefficientDay());
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));

        wire(app, viewModel).preview(settings(true));

        assertTrue(viewModel.getState().getWarnings().stream()
                        .anyMatch(warning -> warning.contains("covers the whole day")),
                "the mock and live gateways both report one severity per trip, and the UI "
                        + "must say weather did not influence the timing");
    }
}
