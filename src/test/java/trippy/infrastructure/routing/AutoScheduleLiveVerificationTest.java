package trippy.infrastructure.routing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import trippy.adapters.gateways.DistanceServiceTravelTimeEstimator;
import trippy.adapters.gateways.WeatherServiceContextGateway;
import trippy.application.autoschedule.TravelEstimate;
import trippy.application.autoschedule.WeatherContext;
import trippy.domain.entities.Trip;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import trippy.infrastructure.weather.OpenMeteoWeatherService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Opt-in checks against the real providers, following the project's existing convention
 * for live tests so the ordinary suite never depends on the network.
 *
 * <p>Run with:
 * {@code RUN_LIVE_AUTOSCHEDULE_TEST=true ./mvnw test -Dtest=AutoScheduleLiveVerificationTest}
 * and, for traffic-aware driving, a {@code TOMTOM_API_KEY} in the environment.</p>
 *
 * <p>These deliberately assert only that a sane answer comes back, not particular
 * durations: real travel times change by the hour and pinning them would produce a test
 * that fails for reasons no one can act on. The numbers themselves are printed for the
 * verification record.</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AUTOSCHEDULE_TEST", matches = "true")
class AutoScheduleLiveVerificationTest {

    /** Seeded demo locations; nothing personal or sensitive. */
    private static final Location UNION_STATION = new Location(43.6453, -79.3806, "Union Station");
    private static final Location CASA_LOMA = new Location(43.6780, -79.4094, "Casa Loma");

    private static final LocalDate TRIP_DATE = LocalDate.now().plusDays(1);
    private static final LocalDateTime MORNING = LocalDateTime.of(TRIP_DATE, LocalTime.of(9, 30));
    private static final LocalDateTime RUSH_HOUR = LocalDateTime.of(TRIP_DATE, LocalTime.of(17, 30));

    private final DistanceServiceTravelTimeEstimator estimator =
            new DistanceServiceTravelTimeEstimator(new OsrmDistanceService());

    private static void recordResult(String label, int minutes, long millis) {
        System.out.println(String.format(
                "[live] %-34s %3d min   (%d ms)", label, minutes, millis));
    }

    private int timed(String label, TransportationMode mode, LocalDateTime departure) {
        long started = System.currentTimeMillis();
        TravelEstimate estimate = estimator.estimate(UNION_STATION, CASA_LOMA, mode, departure);
        long elapsed = System.currentTimeMillis() - started;
        recordResult(label, estimate.getMinutes(), elapsed);
        return estimate.getMinutes();
    }

    @Test
    void walkingReturnsAPlausibleRoute() {
        int minutes = timed("walking (OSRM)", TransportationMode.WALKING, MORNING);

        assertTrue(minutes > 0 && minutes < 240,
                "Union Station to Casa Loma on foot should be well under four hours");
    }

    @Test
    void walkingDoesNotChangeWithDepartureTime() {
        int morning = timed("walking @ 09:30", TransportationMode.WALKING, MORNING);
        int evening = timed("walking @ 17:30", TransportationMode.WALKING, RUSH_HOUR);

        assertTrue(Math.abs(morning - evening) <= 2,
                "the walking provider takes no departure time, so these should match");
    }

    @Test
    void transitIsTimetableAwareAcrossDifferentDepartureTimes() {
        int morning = timed("transit @ 09:30 (Transitous)", TransportationMode.TRANSIT, MORNING);
        int evening = timed("transit @ 17:30 (Transitous)", TransportationMode.TRANSIT, RUSH_HOUR);
        // Night service is thinner, so this is where the timetable shows itself. Two daytime
        // departures can legitimately match on a well-served route, which is why comparing
        // only those would prove nothing either way.
        int night = timed("transit @ 03:00 (Transitous)", TransportationMode.TRANSIT,
                LocalDateTime.of(TRIP_DATE, LocalTime.of(3, 0)));

        System.out.println("[live] transit day-vs-night differs: " + (morning != night));
        assertTrue(morning > 0 && evening > 0 && night > 0,
                "every departure should return an itinerary");
    }

    @Test
    void drivingReturnsARouteAndReportsWhichProviderAnswered() {
        boolean keyPresent = System.getenv("TOMTOM_API_KEY") != null
                || System.getProperty("tomtom.api.key") != null;
        System.out.println("[live] TomTom key present: " + keyPresent
                + (keyPresent ? " (traffic-aware path)" : " (OSRM fallback, not traffic-aware)"));

        int morning = timed("driving @ 09:30", TransportationMode.DRIVING, MORNING);
        int evening = timed("driving @ 17:30", TransportationMode.DRIVING, RUSH_HOUR);

        assertTrue(morning > 0 && evening > 0);
        if (keyPresent) {
            System.out.println("[live] driving differs by departure time: " + (morning != evening)
                    + " - a difference here is the traffic-aware claim being earned");
        }
    }

    @Test
    void theRealForecastCannotDistinguishOneTimeOfDayFromAnother() {
        Trip trip = new Trip("live-check", "Toronto", TRIP_DATE,
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);

        long started = System.currentTimeMillis();
        WeatherContext context =
                new WeatherServiceContextGateway(new OpenMeteoWeatherService()).contextFor(trip);
        long elapsed = System.currentTimeMillis() - started;

        System.out.println(String.format(
                "[live] weather available=%s canDistinguishTimes=%s (%d ms)",
                context.isAvailable(), context.canDistinguishTimes(), elapsed));

        if (context.isAvailable()) {
            assertTrue(!context.canDistinguishTimes(),
                    "today's gateway reports one severity for the whole trip, so the UI must "
                            + "not claim weather influenced the timing");
            assertTrue(context.severityAt(LocalTime.of(9, 0))
                            == context.severityAt(LocalTime.of(17, 0)),
                    "a day-wide forecast scores every hour identically");
        }
    }
}
