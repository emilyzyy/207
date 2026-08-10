package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import interface_adapter.viewmodels.PreviewRowView;
import app.AppContainer;
import use_case.autoschedule.AutoScheduleInteractor;
import use_case.autoschedule.WeatherContext;
import entity.valueobjects.WeatherOption;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.SoftPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import entity.valueobjects.WeatherSeverity;
import interface_adapter.mock.MockDistanceService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
                activity("park", "High Park", ActivityCategory.PARKS_NATURE,
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
                Arrays.asList(unavailable), keepOrder, true);
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
        // Checked against this proposal's own figures rather than a fixed phrase: the summary
        // may only claim a saving the metrics printed above it actually show.
        String summary = previewing.getObjectiveSummary();
        boolean travelFell = previewing.getMetrics().getTravelBeforeMinutes()
                > previewing.getMetrics().getTravelAfterMinutes();
        assertEquals(travelFell, summary.contains("less travel"),
                "the summary and the figures must agree: " + summary);
        assertFalse(summary.isEmpty(), "and it must say something");
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

    /**
     * The caveat is gone, and that is the point.
     *
     * <p>Until Shiyuan added {@code getHourlyWarnings}, both shipped adapters reported one
     * severity for the whole trip, so the Preview had to say weather could not influence the
     * timing. It now receives an hour-by-hour forecast, so that sentence would be false and
     * must not appear.</p>
     */
    @Test
    void noWeatherCaveatIsShownNowThatTheForecastIsHourly() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(inefficientDay());
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));

        wire(app, viewModel).preview(settings(true));

        assertFalse(viewModel.getState().getWarnings().stream()
                        .anyMatch(warning -> warning.contains("covers the whole day")),
                "an hourly forecast can influence timing, so the old caveat would be a lie");
        assertTrue(viewModel.getState().getObjectiveSummary().length() > 0);
    }

    /**
     * The state the settings dialog is actually in, through the real wiring.
     *
     * <p>This is the assertion that changed when the hourly gateway landed: the preference
     * used to be withheld with a reason, and is now offered and ticked by default. Nothing
     * in the engine, the Interactor or the UI changed to make that happen — only the adapter
     * that reads the provider.</p>
     */
    @Test
    void theWeatherPreferenceIsOfferedThroughTheProductionWiring() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(inefficientDay());
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        AtomicReference<WeatherOption> answer = new AtomicReference<>();

        wire(app, viewModel).loadWeatherOption(answer::set);

        assertTrue(answer.get().isAvailable(),
                "the hourly forecast can tell one time of day from another");
        assertTrue(answer.get().isSelectedByDefault(),
                "offered means on unless the traveller declines");
        assertEquals("", answer.get().getUnavailableReason(),
                "there is nothing left to explain away");
    }

    /**
     * The withheld path still has to work, because a provider can always fail. A gateway
     * that yields no usable forecast must disable the checkbox and say why, rather than
     * offering a choice that would do nothing.
     */
    @Test
    void theWeatherPreferenceIsStillWithheldWhenNoForecastIsUsable() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(inefficientDay());
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        AutoScheduleInteractor noForecast = new AutoScheduleInteractor(app.trips,
                new DistanceServiceTravelTimeEstimator(new MockDistanceService()),
                anyTrip -> WeatherContext.unavailable(),
                new AutoSchedulePresenter(viewModel),
                Arrays.asList(new WeatherSuitabilityPolicy(), new MealWindowPolicy(),
                        new DaylightPolicy()),
                new ScheduleEngine());
        AtomicReference<WeatherOption> answer = new AtomicReference<>();

        new AutoScheduleController(noForecast, viewModel, TaskRunner.immediate())
                .loadWeatherOption(answer::set);

        assertFalse(answer.get().isAvailable());
        assertEquals(WeatherOption.NO_FORECAST, answer.get().getUnavailableReason());
    }

    /**
     * Autoschedule reads whatever the Trip is holding and writes back through
     * {@code copyWithSchedule}. It never asks how an activity got there, which is the whole
     * reason Alex's add-to-plan work needs no change on either side. That has been claimed
     * in the documentation without being asserted, so it is asserted here: an activity
     * appended to the Trip after the fact is scheduled like any other.
     */
    @Test
    void anActivityAddedToThePlanLaterIsScheduledWithNoAutoscheduleChange() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(inefficientDay());

        // Stands in for add-to-plan: a fourth activity appears in the Trip afterwards.
        List<ScheduledEvent> grown = new ArrayList<>(trip.getScheduledEvents());
        grown.add(new ScheduledEvent("event-gallery",
                activity("gallery", "Art Gallery of Ontario", ActivityCategory.MUSEUM,
                        IndoorOutdoorType.INDOOR, 43.6536, -79.3925, 10, 17),
                LocalTime.of(16, 30), LocalTime.of(17, 0), EventType.ACTIVITY, ""));
        Trip larger = app.trips.save(trip.copyWithSchedule(grown));

        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                larger.getId(), larger.getScheduledEvents(), "", false));
        AutoScheduleController controller = wire(app, viewModel);

        controller.preview(settings(true));

        assertEquals(AutoScheduleStatus.PREVIEW, viewModel.getState().getStatus());
        assertEquals(4, viewModel.getState().getMetrics().getActivityCount(),
                "the newly added activity is scheduled like any other");
        assertTrue(viewModel.getState().getPreviewRows().stream()
                        .anyMatch(row -> row.getEventId().equals("event-gallery")),
                "an activity added after the fact must appear in the proposal");

        controller.apply();
        assertEquals(AutoScheduleStatus.APPLIED, viewModel.getState().getStatus());
    }

    /**
     * Nothing about the engine or the Interactor changes when an hourly forecast arrives:
     * the same wiring, given a gateway that can distinguish hours, offers the preference.
     * This is the test that keeps the "no redesign needed" claim honest.
     */
    @Test
    void anHourlyGatewayWouldOfferThePreferenceWithNoOtherChange() {
        AppBuilder builder = new AppBuilder();
        AppContainer app = builder.buildOffline();
        Trip trip = app.trips.save(inefficientDay());
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState(
                trip.getId(), trip.getScheduledEvents(), "", false));
        Map<Integer, WeatherSeverity> byHour = new HashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            byHour.put(hour, WeatherSeverity.LOW);
        }
        AutoScheduleInteractor hourly = new AutoScheduleInteractor(app.trips,
                new DistanceServiceTravelTimeEstimator(new MockDistanceService()),
                anyTrip -> WeatherContext.hourly(byHour),
                new AutoSchedulePresenter(viewModel),
                Arrays.asList(new WeatherSuitabilityPolicy(), new MealWindowPolicy(),
                        new DaylightPolicy()),
                new ScheduleEngine());
        AtomicReference<WeatherOption> answer = new AtomicReference<>();

        new AutoScheduleController(hourly, viewModel, TaskRunner.immediate())
                .loadWeatherOption(answer::set);

        assertTrue(answer.get().isAvailable());
        assertTrue(answer.get().isSelectedByDefault(), "offered means on unless declined");
    }
}
