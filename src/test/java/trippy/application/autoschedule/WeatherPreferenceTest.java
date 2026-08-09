package trippy.application.autoschedule;

import static trippy.application.autoschedule.ProblemFixtures.at;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import trippy.application.autoschedule.engine.ScheduleEngine;
import trippy.application.autoschedule.policy.DaylightPolicy;
import trippy.application.autoschedule.policy.MealWindowPolicy;
import trippy.application.autoschedule.policy.SoftPolicy;
import trippy.application.autoschedule.policy.WeatherSuitabilityPolicy;
import trippy.application.autoschedule.testdoubles.FakeTravelTimeEstimator;
import trippy.application.autoschedule.testdoubles.FakeTripRepository;
import trippy.application.autoschedule.testdoubles.FakeWeatherContextGateway;
import trippy.application.autoschedule.testdoubles.RecordingPresenter;
import trippy.domain.entities.Activity;
import trippy.domain.entities.ScheduledEvent;
import trippy.domain.entities.Trip;
import trippy.domain.valueobjects.ActivityCategory;
import trippy.domain.valueobjects.EventType;
import trippy.domain.valueobjects.IndoorOutdoorType;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import trippy.domain.valueobjects.WeatherSeverity;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Weather is the one soft objective the traveller is asked about, and only when asking is
 * honest.
 *
 * <p>The product reason is that a forecast far enough ahead comes back as a single
 * whole-day outlook. That cannot say whether a park is better at 10 a.m. or 3 p.m., so a
 * checkbox backed by it would look like a choice while changing nothing. These tests fix
 * both halves of the resulting contract: the capability question the settings dialog asks
 * before drawing the checkbox, and the guarantee that whatever the dialog believes, the
 * use case reaches its own conclusion and never lets weather cost a traveller their
 * schedule.</p>
 */
class WeatherPreferenceTest {

    private static final List<SoftPolicy> REGISTERED = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    private final RecordingPresenter presenter = new RecordingPresenter();
    private final FakeWeatherContextGateway weather = new FakeWeatherContextGateway();
    private final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator();

    // --- the capability question the dialog asks ------------------------------------

    @Test
    void anHourlyForecastMakesTheWeatherPreferenceAvailable() {
        weather.returning(WeatherContext.hourly(allDay(WeatherSeverity.LOW)));

        WeatherOption option = interactorFor(oneOutdoorDay()).weatherOptionFor("trip-1");

        assertTrue(option.isAvailable(),
                "an hourly forecast can tell 10am from 3pm, so the choice is a real one");
        assertTrue(option.isSelectedByDefault(),
                "where weather can help it should help unless the traveller says otherwise");
        assertEquals("", option.getUnavailableReason());
    }

    @Test
    void aWholeDayForecastWithholdsThePreferenceAndSaysWhy() {
        weather.returning(WeatherContext.tripLevel(WeatherSeverity.HIGH));

        WeatherOption option = interactorFor(oneOutdoorDay()).weatherOptionFor("trip-1");

        assertFalse(option.isAvailable(),
                "one severity for the whole day scores every candidate time alike");
        assertFalse(option.isSelectedByDefault());
        assertEquals(WeatherOption.NO_HOURLY_FORECAST, option.getUnavailableReason());
    }

    /**
     * A trip beyond the provider's hourly horizon is not a special case in the code, and
     * deliberately so: it arrives as a coarse or missing forecast like any other, and is
     * refused for that reason rather than by a date cutoff that would go stale the moment
     * a provider changed its horizon.
     */
    @Test
    void aTripBeyondTheHourlyHorizonWithholdsThePreference() {
        weather.returning(WeatherContext.tripLevel(WeatherSeverity.LOW));
        Trip farFuture = tripOn(java.time.LocalDate.now().plusMonths(6));

        WeatherOption option = interactorFor(new FakeTripRepository(farFuture))
                .weatherOptionFor("trip-1");

        assertFalse(option.isAvailable());
        assertEquals(WeatherOption.NO_HOURLY_FORECAST, option.getUnavailableReason());
    }

    @Test
    void anUnobtainableForecastWithholdsThePreference() {
        weather.returning(WeatherContext.unavailable());

        WeatherOption option = interactorFor(oneOutdoorDay()).weatherOptionFor("trip-1");

        assertFalse(option.isAvailable());
        assertEquals(WeatherOption.NO_FORECAST, option.getUnavailableReason());
    }

    @Test
    void askingAboutWeatherNeverThrowsAndNeverReportsAFailure() {
        AutoScheduleInteractor interactor = new AutoScheduleInteractor(oneOutdoorDay(),
                estimator, new FakeWeatherContextGateway().thatFails(), presenter, REGISTERED,
                new ScheduleEngine());

        WeatherOption option = interactor.weatherOptionFor("trip-1");

        assertFalse(option.isAvailable());
        assertEquals(WeatherOption.NO_FORECAST, option.getUnavailableReason());
        assertNull(presenter.getFailure(),
                "drawing a checkbox must not put an error on screen for something the "
                        + "traveller never did");
    }

    @Test
    void anUnknownTripYieldsAnUnavailableOptionRatherThanAnError() {
        weather.returning(WeatherContext.hourly(allDay(WeatherSeverity.LOW)));
        AutoScheduleInteractor interactor = interactorFor(oneOutdoorDay());

        assertFalse(interactor.weatherOptionFor("no-such-trip").isAvailable());
        assertFalse(interactor.weatherOptionFor("").isAvailable());
        assertFalse(interactor.weatherOptionFor(null).isAvailable());
        assertNull(presenter.getFailure(), "a silent query must stay silent");
    }

    // --- what the preference does, and does not, do to the schedule -----------------

    @Test
    void anUntickedPreferenceContributesNothingAndConsultsNoForecast() {
        weather.returning(WeatherContext.hourly(allDay(WeatherSeverity.HIGH)));

        interactorFor(oneOutdoorDay()).preview(input(false));
        AutoSchedulePreviewOutputData notConsidered = presenter.getPreview();

        assertNotNull(notConsidered);
        assertFalse(notConsidered.getActivePolicies().contains(PolicyId.WEATHER),
                "weather that was never consulted must not be listed as applied");
        for (String warning : notConsidered.getWarnings()) {
            assertFalse(warning.toLowerCase().contains("forecast"),
                    "declining weather is a choice, not a degradation to warn about: "
                            + warning);
        }

        // The same day with the same forecast, this time asked for: the score is the
        // comparison that proves the unticked run really was weather-free.
        RecordingPresenter second = new RecordingPresenter();
        new AutoScheduleInteractor(oneOutdoorDay(), estimator, weather, second, REGISTERED,
                new ScheduleEngine()).preview(input(true));

        assertTrue(second.getPreview().getPracticalCostMinutes()
                        > notConsidered.getPracticalCostMinutes(),
                "bad weather the traveller asked to consider must cost something, which is "
                        + "exactly what declining it avoids paying");
    }

    @Test
    void aTickedPreferenceAgainstACoarseForecastContributesZeroAndSaysSo() {
        weather.returning(WeatherContext.tripLevel(WeatherSeverity.HIGH));

        interactorFor(oneOutdoorDay()).preview(input(true));
        AutoSchedulePreviewOutputData preview = presenter.getPreview();

        assertNotNull(preview, "a useless forecast must not cost the traveller their day");
        assertFalse(preview.getActivePolicies().contains(PolicyId.WEATHER),
                "the use case, not the dialog, decides whether weather was applied");
        assertTrue(preview.getWarnings().stream()
                        .anyMatch(warning -> warning.contains("covers the whole day")),
                "the traveller asked for weather and did not get it, so say so plainly");
    }

    /**
     * A tight day on purpose. Two hour-long activities and one ten-minute walk exactly
     * fill 09:00-11:10, so neither order wastes a minute of travel or leaves any idle
     * time: the two schedules are identical in every respect except which of them puts
     * the outdoor activity in the storm. That isolates weather as the only thing that can
     * decide the order, which is what makes the assertion meaningful rather than lucky.
     */
    @Test
    void selectedHourlyWeatherMovesAnOutdoorActivityOutOfTheWorstHours() {
        Map<Integer, WeatherSeverity> byHour = allDay(WeatherSeverity.LOW);
        byHour.put(9, WeatherSeverity.HIGH);
        weather.returning(WeatherContext.hourly(byHour));

        assertEquals(at(10, 10), parkStartWithWeather(true, byHour),
                "the clear slot is free and costs nothing extra, so a real forecast is "
                        + "worth more than the small charge for reordering");
        assertEquals(at(9, 0), parkStartWithWeather(false, byHour),
                "with weather declined the storm is invisible, so the traveller's own order "
                        + "is the only thing left to decide, and it stands");
    }

    private LocalTime parkStartWithWeather(boolean considerWeather,
                                           Map<Integer, WeatherSeverity> byHour) {
        RecordingPresenter recorder = new RecordingPresenter();
        FakeTripRepository trips = new FakeTripRepository(tripWith(
                outdoorEvent("park", 9, 60), indoorEvent("museum", 10, 60)));
        AutoScheduleInputData request = new AutoScheduleInputData("trip-1", at(9, 0),
                at(11, 10), TransportationMode.WALKING, Collections.emptySet(),
                Collections.emptyList(), true, considerWeather);

        new AutoScheduleInteractor(trips, estimator,
                new FakeWeatherContextGateway().returning(WeatherContext.hourly(byHour)),
                recorder, REGISTERED, new ScheduleEngine()).preview(request);

        assertNotNull(recorder.getPreview(), "the day fits exactly, so it must schedule");
        return startOf(recorder.getPreview(), "park");
    }

    @Test
    void weatherCannotOverrideAHardConstraint() {
        // The whole day is foul, so weather would rather the park were anywhere else --
        // but it is pinned, and a pin is not negotiable.
        weather.returning(WeatherContext.hourly(allDay(WeatherSeverity.HIGH)));
        FakeTripRepository trips = new FakeTripRepository(tripWith(
                outdoorEvent("park", 10, 60), indoorEvent("museum", 14, 60)));

        AutoScheduleInputData pinned = new AutoScheduleInputData("trip-1", at(9, 0), at(21, 0),
                TransportationMode.WALKING, Collections.singleton("park"),
                Collections.emptyList(), false, true);
        new AutoScheduleInteractor(trips, estimator, weather, presenter, REGISTERED,
                new ScheduleEngine()).preview(pinned);

        assertNotNull(presenter.getPreview());
        assertEquals(at(10, 0), startOf(presenter.getPreview(), "park"),
                "a locked time is a hard constraint; a soft preference cannot move it");
    }

    @Test
    void aSmallWeatherGainCannotJustifyAnExtremeDetour() {
        WeatherSuitabilityPolicy policy = new WeatherSuitabilityPolicy();
        Map<Integer, WeatherSeverity> byHour = allDay(WeatherSeverity.HIGH);
        PolicyContext worst = new PolicyContext(WeatherContext.hourly(byHour));
        ScheduleTask park = ScheduleTask.movable("park", ProblemFixtures.activity("park",
                ActivityCategory.PARKS_NATURE, IndoorOutdoorType.OUTDOOR, at(0, 0), at(23, 59)),
                480, 0);

        int penalty = policy.penaltyMinutes(PlacedActivity.first(park, at(9, 0), at(17, 0), 0, 0),
                worst);

        assertTrue(penalty <= WeatherSuitabilityPolicy.MAX_PENALTY_MINUTES,
                "an eight-hour soaking still charges at most "
                        + WeatherSuitabilityPolicy.MAX_PENALTY_MINUTES + " equivalent minutes, "
                        + "so avoiding it can never buy an hours-long detour");
    }

    // --- fixtures --------------------------------------------------------------------

    private AutoScheduleInteractor interactorFor(FakeTripRepository trips) {
        return new AutoScheduleInteractor(trips, estimator, weather, presenter, REGISTERED,
                new ScheduleEngine());
    }

    private static AutoScheduleInputData input(boolean considerWeather) {
        return new AutoScheduleInputData("trip-1", at(9, 0), at(21, 0),
                TransportationMode.WALKING, Collections.emptySet(), Collections.emptyList(),
                false, considerWeather);
    }

    private static Map<Integer, WeatherSeverity> allDay(WeatherSeverity severity) {
        Map<Integer, WeatherSeverity> byHour = new HashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            byHour.put(hour, severity);
        }
        return byHour;
    }

    private static LocalTime startOf(AutoSchedulePreviewOutputData preview, String eventId) {
        for (ProposedEventData row : preview.getRows()) {
            if (row.getEventId().equals(eventId)) {
                return row.getStart();
            }
        }
        throw new AssertionError("no row for " + eventId);
    }

    private static FakeTripRepository oneOutdoorDay() {
        return new FakeTripRepository(tripWith(outdoorEvent("park", 10, 60)));
    }

    private static ScheduledEvent outdoorEvent(String id, int startHour, int minutes) {
        return event(id, ActivityCategory.PARKS_NATURE, IndoorOutdoorType.OUTDOOR, startHour, minutes);
    }

    private static ScheduledEvent indoorEvent(String id, int startHour, int minutes) {
        return event(id, ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR, startHour, minutes);
    }

    private static ScheduledEvent event(String id, ActivityCategory category,
                                        IndoorOutdoorType exposure, int startHour, int minutes) {
        Activity activity = new Activity(id, id, category, new Location(43.65, -79.38, id),
                4.5, minutes, at(0, 0), at(23, 59), exposure, "none");
        LocalTime start = at(startHour, 0);
        return new ScheduledEvent(id, activity, start, start.plusMinutes(minutes),
                EventType.ACTIVITY, "");
    }

    private static Trip tripWith(ScheduledEvent... events) {
        return tripOn(ProblemFixtures.TRIP_DATE, events);
    }

    private static Trip tripOn(java.time.LocalDate date, ScheduledEvent... events) {
        List<ScheduledEvent> schedule = new ArrayList<>(Arrays.asList(events));
        if (schedule.isEmpty()) {
            schedule.add(outdoorEvent("park", 10, 60));
        }
        Trip trip = new Trip("trip-1", "Toronto", date, at(9, 0), at(21, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(schedule);
        return trip;
    }
}
