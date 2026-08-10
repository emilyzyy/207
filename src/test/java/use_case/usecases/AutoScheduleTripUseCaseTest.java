package use_case.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.entities.WeatherWarning;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import entity.valueobjects.WeatherSeverity;
import use_case.ports.DistanceService;
import use_case.ports.TripRepository;
import use_case.ports.WeatherService;
import use_case.scheduling.DefaultActivityScoringPolicy;

final class AutoScheduleTripUseCaseTest {
    private static final Location ORIGIN = new Location(45.5019, -73.5674, "Resolved destination");

    @Test
    void emptyBookmarksHaveClearErrorAndDoNotCallWeather() {
        final FakeTripRepository trips = new FakeTripRepository(trip("empty", TransportationMode.WALKING));
        final CountingWeatherService weather = new CountingWeatherService(WeatherSeverity.LOW);
        final AutoScheduleTripUseCase scheduler = scheduler(trips, new ConfigurableDistanceService(), weather);

        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> scheduler.execute("empty"));

        assertTrue(error.getMessage().contains("no bookmarked activities"));
        assertEquals(0, weather.calls);
        assertTrue(trips.findById("empty").get().getScheduledEvents().isEmpty());
    }

    @Test
    void firstActivityIncludesTravelFromResolvedDestination() {
        final Trip trip = trip("first-travel", TransportationMode.TRANSIT);
        final Activity museum = activity("museum", 4.5, 60, time(9, 0), time(18, 0),
                IndoorOutdoorType.INDOOR);
        trip.bookmark(museum);
        final FakeTripRepository trips = new FakeTripRepository(trip);
        final ConfigurableDistanceService distances = new ConfigurableDistanceService();
        distances.defaultMinutes = 17;

        final Trip result = scheduler(trips, distances, new CountingWeatherService(WeatherSeverity.LOW))
                .execute(trip.getId());

        assertEquals(2, result.getScheduledEvents().size());
        assertEvent(result.getScheduledEvents().get(0), EventType.TRAVEL, time(9, 0), time(9, 17));
        assertEvent(result.getScheduledEvents().get(1), EventType.ACTIVITY, time(9, 17), time(10, 17));
        assertSame(ORIGIN, distances.calls.get(0).from);
        assertEquals(TransportationMode.TRANSIT, distances.calls.get(0).mode);
        assertEquals(LocalDateTime.of(trip.getDate(), time(9, 0)), distances.calls.get(0).departure);
    }

    @Test
    void transportationModeControlsInsertedTravelDuration() {
        final Map<TransportationMode, Integer> minutes = new HashMap<TransportationMode, Integer>();
        minutes.put(TransportationMode.WALKING, 30);
        minutes.put(TransportationMode.TRANSIT, 20);
        minutes.put(TransportationMode.DRIVING, 10);

        // Only the real modes: FASTEST is a request the router resolves, not a rate.
        for (TransportationMode mode : TransportationMode.specificModes()) {
            final Trip trip = trip("mode-" + mode, mode);
            trip.bookmark(activity("stop", 4.5, 30, time(9, 0), time(18, 0),
                    IndoorOutdoorType.INDOOR));
            final ConfigurableDistanceService distances = new ConfigurableDistanceService();
            distances.byMode.putAll(minutes);
            final Trip result = scheduler(new FakeTripRepository(trip), distances,
                    new CountingWeatherService(WeatherSeverity.LOW)).execute(trip.getId());
            assertEquals(time(9, minutes.get(mode)), result.getScheduledEvents().get(0).getEndTime());
        }
    }

    @Test
    void arrivalBeforeOpeningWaitsWithoutOverlap() {
        final Trip trip = trip("waiting", TransportationMode.WALKING);
        trip.bookmark(activity("late-opening", 4.5, 45, time(10, 0), time(17, 0),
                IndoorOutdoorType.INDOOR));
        final ConfigurableDistanceService distances = new ConfigurableDistanceService();
        distances.defaultMinutes = 10;

        final Trip result = scheduler(new FakeTripRepository(trip), distances,
                new CountingWeatherService(WeatherSeverity.LOW)).execute(trip.getId());

        assertEvent(result.getScheduledEvents().get(0), EventType.TRAVEL, time(9, 0), time(9, 10));
        assertEvent(result.getScheduledEvents().get(1), EventType.ACTIVITY, time(10, 0), time(10, 45));
        assertFalse(result.getScheduledEvents().get(1).getStartTime()
                .isBefore(result.getScheduledEvents().get(0).getEndTime()));
    }

    @Test
    void severeWeatherPrefersIndoorActivity() {
        final Trip trip = trip("weather", TransportationMode.WALKING);
        trip.bookmark(activity("outdoor", 5.0, 30, time(9, 0), time(18, 0),
                IndoorOutdoorType.OUTDOOR));
        trip.bookmark(activity("indoor", 4.5, 30, time(9, 0), time(18, 0),
                IndoorOutdoorType.INDOOR));
        final ConfigurableDistanceService distances = new ConfigurableDistanceService();
        distances.defaultMinutes = 5;

        final Trip result = scheduler(new FakeTripRepository(trip), distances,
                new CountingWeatherService(WeatherSeverity.HIGH)).execute(trip.getId());

        assertEquals("indoor", firstActivity(result).getActivity().getId());
    }

    @Test
    void candidateUsesWeatherFromEveryHourItsActivityOverlaps() {
        final Trip trip = trip("hourly-weather", TransportationMode.WALKING);
        trip.bookmark(activity("outdoor-spans-storm", 5.0, 90, time(9, 30), time(18, 0),
                IndoorOutdoorType.OUTDOOR));
        trip.bookmark(activity("indoor-at-nine", 4.0, 30, time(9, 0), time(18, 0),
                IndoorOutdoorType.INDOOR));
        final ConfigurableDistanceService distances = new ConfigurableDistanceService();
        distances.defaultMinutes = 0;
        final WeatherService changingWeather = changingWeatherAt(10, WeatherSeverity.HIGH);

        final Trip result = scheduler(new FakeTripRepository(trip), distances, changingWeather)
                .execute(trip.getId());

        assertEquals("indoor-at-nine", firstActivity(result).getActivity().getId());
    }

    @Test
    void injectedScoringPolicyControlsSelection() {
        final Trip trip = trip("injected-policy", TransportationMode.WALKING);
        trip.bookmark(activity("a", 5.0, 30, time(9, 0), time(18, 0), IndoorOutdoorType.INDOOR));
        trip.bookmark(activity("b", 1.0, 30, time(9, 0), time(18, 0), IndoorOutdoorType.INDOOR));
        final ConfigurableDistanceService distances = new ConfigurableDistanceService();
        final FakeTripRepository trips = new FakeTripRepository(trip);
        final AutoScheduleTripUseCase scheduler = new AutoScheduleTripUseCase(trips, distances,
                new CountingWeatherService(WeatherSeverity.LOW),
                (activity, travelMinutes, severity) -> "b".equals(activity.getId()) ? 100.0 : 0.0);

        final Trip result = scheduler.execute(trip.getId());

        assertEquals("b", firstActivity(result).getActivity().getId());
    }

    @Test
    void scheduleStaysSortedInsideOpeningHoursAndTripWindow() {
        final Trip trip = trip("valid", TransportationMode.WALKING);
        trip.bookmark(activity("b", 4.8, 50, time(9, 40), time(12, 0), IndoorOutdoorType.MIXED));
        trip.bookmark(activity("a", 4.9, 40, time(9, 0), time(11, 0), IndoorOutdoorType.INDOOR));
        trip.bookmark(activity("too-late", 5.0, 180, time(16, 0), time(20, 0),
                IndoorOutdoorType.INDOOR));
        final ConfigurableDistanceService distances = new ConfigurableDistanceService();
        distances.defaultMinutes = 10;

        final Trip result = scheduler(new FakeTripRepository(trip), distances,
                new CountingWeatherService(WeatherSeverity.LOW)).execute(trip.getId());

        assertEquals(4, result.getScheduledEvents().size());
        assertLegalSchedule(result);
        assertTrue(result.getScheduledEvents().stream()
                .noneMatch(event -> event.getActivity() != null
                        && "too-late".equals(event.getActivity().getId())));
    }

    @Test
    void noFeasibleActivityLeavesExistingPlanUntouched() {
        final Trip trip = trip("none", TransportationMode.WALKING);
        final Activity existingActivity = activity("existing", 4.0, 30, time(9, 0), time(18, 0),
                IndoorOutdoorType.INDOOR);
        final ScheduledEvent existing = new ScheduledEvent("existing-event", existingActivity,
                time(9, 0), time(9, 30), EventType.ACTIVITY, "existing");
        trip.addEvent(existing);
        trip.bookmark(activity("closed", 5.0, 61, time(17, 0), time(18, 0),
                IndoorOutdoorType.INDOOR));
        final FakeTripRepository trips = new FakeTripRepository(trip);
        final ConfigurableDistanceService distances = new ConfigurableDistanceService();
        distances.defaultMinutes = 20;

        final IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> scheduler(trips, distances, new CountingWeatherService(WeatherSeverity.LOW))
                        .execute(trip.getId()));

        assertTrue(error.getMessage().contains("No bookmarked activity"));
        assertEquals(1, trips.findById(trip.getId()).get().getScheduledEvents().size());
        assertSame(existing, trips.findById(trip.getId()).get().getScheduledEvents().get(0));
    }

    @Test
    void dependencyFailureCannotLeavePartialSchedule() {
        final Trip trip = trip("atomic", TransportationMode.WALKING);
        final Activity existingActivity = activity("existing", 4.0, 30, time(9, 0), time(18, 0),
                IndoorOutdoorType.INDOOR);
        trip.addEvent(new ScheduledEvent("existing-event", existingActivity,
                time(9, 0), time(9, 30), EventType.ACTIVITY, "existing"));
        trip.bookmark(activity("a", 4.8, 30, time(9, 0), time(18, 0), IndoorOutdoorType.INDOOR));
        trip.bookmark(activity("b", 4.7, 30, time(9, 0), time(18, 0), IndoorOutdoorType.INDOOR));
        final FakeTripRepository trips = new FakeTripRepository(trip);
        final DistanceService failing = new DistanceService() {
            private int calls;
            public int estimateTravelMinutes(Location from, Location to, TransportationMode mode,
                                             LocalDateTime departure) {
                if (++calls == 2) {
                    throw new IllegalStateException("distance unavailable");
                }
                return 5;
            }
        };

        assertThrows(IllegalStateException.class,
                () -> scheduler(trips, failing, new CountingWeatherService(WeatherSeverity.LOW))
                        .execute(trip.getId()));
        assertEquals("existing-event", trips.findById(trip.getId()).get()
                .getScheduledEvents().get(0).getId());
    }

    @Test
    void equalInputsProduceIdenticalOrderTimesAndIds() {
        final Trip first = trip("deterministic", TransportationMode.WALKING);
        final Trip second = trip("deterministic", TransportationMode.WALKING);
        final Activity a = activity("a", 4.5, 30, time(9, 0), time(18, 0), IndoorOutdoorType.INDOOR);
        final Activity b = activity("b", 4.5, 30, time(9, 0), time(18, 0), IndoorOutdoorType.INDOOR);
        first.bookmark(b);
        first.bookmark(a);
        second.bookmark(a);
        second.bookmark(b);
        final ConfigurableDistanceService firstDistances = new ConfigurableDistanceService();
        final ConfigurableDistanceService secondDistances = new ConfigurableDistanceService();
        firstDistances.defaultMinutes = 5;
        secondDistances.defaultMinutes = 5;

        final Trip firstResult = scheduler(new FakeTripRepository(first), firstDistances,
                new CountingWeatherService(WeatherSeverity.LOW)).execute(first.getId());
        final Trip secondResult = scheduler(new FakeTripRepository(second), secondDistances,
                new CountingWeatherService(WeatherSeverity.LOW)).execute(second.getId());

        assertEquals(signatures(firstResult), signatures(secondResult));
        assertEquals("a", firstActivity(firstResult).getActivity().getId());
    }

    private AutoScheduleTripUseCase scheduler(TripRepository trips, DistanceService distances,
                                              WeatherService weather) {
        return new AutoScheduleTripUseCase(trips, distances, weather,
                new DefaultActivityScoringPolicy());
    }

    private Trip trip(String id, TransportationMode mode) {
        return new Trip(id, "Montreal", LocalDate.of(2026, 7, 14),
                time(9, 0), time(18, 0), mode);
    }

    private Activity activity(String id, double rating, int duration, LocalTime opening,
                              LocalTime closing, IndoorOutdoorType type) {
        final int offset = Math.abs(id.hashCode() % 1000);
        return new Activity(id, id, ActivityCategory.ATTRACTION,
                new Location(45.0 + offset / 10000.0, -73.0, id), rating, duration,
                opening, closing, type, "test");
    }

    private LocalTime time(int hour, int minute) {
        return LocalTime.of(hour, minute);
    }

    private ScheduledEvent firstActivity(Trip trip) {
        return trip.getScheduledEvents().stream()
                .filter(event -> event.getEventType() == EventType.ACTIVITY)
                .findFirst().orElseThrow(AssertionError::new);
    }

    private WeatherService changingWeatherAt(int severeHour, WeatherSeverity severe) {
        return trip -> {
            final List<WeatherWarning> hourly = new ArrayList<WeatherWarning>();
            for (int hour = 0; hour < 24; hour++) {
                final WeatherSeverity severity = hour == severeHour ? severe : WeatherSeverity.LOW;
                hourly.add(new WeatherWarning(
                        ORIGIN, time(hour, 0), "test", severity, "test"));
            }
            return hourly;
        };
    }

    private void assertEvent(ScheduledEvent event, EventType type, LocalTime start, LocalTime end) {
        assertEquals(type, event.getEventType());
        assertEquals(start, event.getStartTime());
        assertEquals(end, event.getEndTime());
    }

    private void assertLegalSchedule(Trip trip) {
        ScheduledEvent previous = null;
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            assertFalse(event.getStartTime().isBefore(trip.getStartTime()));
            assertFalse(event.getEndTime().isAfter(trip.getEndTime()));
            if (previous != null) {
                assertFalse(event.getStartTime().isBefore(previous.getEndTime()));
            }
            if (event.getEventType() == EventType.ACTIVITY) {
                assertFalse(event.getStartTime().isBefore(event.getActivity().getOpeningTime()));
                assertFalse(event.getEndTime().isAfter(event.getActivity().getClosingTime()));
            }
            previous = event;
        }
    }

    private List<String> signatures(Trip trip) {
        final List<String> result = new ArrayList<String>();
        for (ScheduledEvent event : trip.getScheduledEvents()) {
            result.add(event.getId() + '|' + event.getEventType() + '|' + event.getStartTime()
                    + '|' + event.getEndTime() + '|'
                    + (event.getActivity() == null ? "travel" : event.getActivity().getId()));
        }
        return result;
    }

    private static final class FakeTripRepository implements TripRepository {
        private final Map<String, Trip> trips = new HashMap<String, Trip>();

        private FakeTripRepository(Trip trip) {
            trips.put(trip.getId(), trip);
        }

        public Trip save(Trip trip) {
            trips.put(trip.getId(), trip);
            return trip;
        }

        public Optional<Trip> findById(String id) {
            return Optional.ofNullable(trips.get(id));
        }

        public List<Trip> findAll() {
            return new ArrayList<Trip>(trips.values());
        }
    }

    private static final class CountingWeatherService implements WeatherService {
        private final WeatherSeverity severity;
        private int calls;

        private CountingWeatherService(WeatherSeverity severity) {
            this.severity = severity;
        }

        public List<WeatherWarning> getHourlyWarnings(Trip trip) {
            calls++;
            final List<WeatherWarning> hourly = new ArrayList<WeatherWarning>();
            for (int hour = 0; hour < 24; hour++) {
                hourly.add(new WeatherWarning(
                        ORIGIN, LocalTime.of(hour, 0), "test", severity, "test"));
            }
            return hourly;
        }
    }

    private static final class ConfigurableDistanceService implements DistanceService {
        private final Map<TransportationMode, Integer> byMode =
                new HashMap<TransportationMode, Integer>();
        private final List<DistanceCall> calls = new ArrayList<DistanceCall>();
        private int defaultMinutes;

        public int estimateTravelMinutes(Location from, Location to, TransportationMode mode,
                                         LocalDateTime departure) {
            calls.add(new DistanceCall(from, mode, departure));
            return byMode.containsKey(mode) ? byMode.get(mode) : defaultMinutes;
        }
    }

    private static final class DistanceCall {
        private final Location from;
        private final TransportationMode mode;
        private final LocalDateTime departure;

        private DistanceCall(Location from, TransportationMode mode, LocalDateTime departure) {
            this.from = from;
            this.mode = mode;
            this.departure = departure;
        }
    }
}
