package closeai.adapters.gateways;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.application.autoschedule.TravelEstimate;
import closeai.application.autoschedule.TravelEstimateQuality;
import closeai.application.autoschedule.WeatherContext;
import closeai.application.ports.DistanceService;
import closeai.application.ports.WeatherService;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import closeai.domain.valueobjects.WeatherSeverity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * The two adapters that connect scheduling to the team's shared services. Both exist to
 * answer questions the shared contracts cannot, and both have to degrade honestly.
 */
class GatewayAdapterTest {

    private static final Location FROM = new Location(43.6453, -79.3806, "Union");
    private static final Location TO = new Location(43.6780, -79.4094, "Casa Loma");
    private static final LocalDateTime DEPARTURE =
            LocalDateTime.of(2026, 8, 12, 17, 30);

    private static Trip trip() {
        return new Trip("trip-1", "Toronto", LocalDate.of(2026, 8, 12),
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
    }

    @Test
    void travelQualityIsReportedAsUnknownBecauseTheSharedPortCannotSay() {
        DistanceService distances = (from, to, mode, departure) -> 25;

        TravelEstimate estimate = new DistanceServiceTravelTimeEstimator(distances)
                .estimate(FROM, TO, TransportationMode.WALKING, DEPARTURE);

        assertEquals(25, estimate.getMinutes());
        assertEquals(TravelEstimateQuality.UNKNOWN, estimate.getQuality(),
                "the shared service cannot distinguish a real route from its own fallback, "
                        + "so claiming ROUTED would be inventing confidence");
    }

    @Test
    void walkingIsNeverTreatedAsTimeSensitive() {
        DistanceServiceTravelTimeEstimator estimator = new DistanceServiceTravelTimeEstimator(
                (from, to, mode, departure) -> 10, true);

        assertFalse(estimator.isTimeSensitive(TransportationMode.WALKING),
                "the walking provider takes no departure time, so buckets would be waste");
    }

    @Test
    void transitIsAlwaysTreatedAsTimeSensitive() {
        DistanceServiceTravelTimeEstimator withKey = new DistanceServiceTravelTimeEstimator(
                (from, to, mode, departure) -> 10, true);
        DistanceServiceTravelTimeEstimator withoutKey = new DistanceServiceTravelTimeEstimator(
                (from, to, mode, departure) -> 10, false);

        assertTrue(withKey.isTimeSensitive(TransportationMode.TRANSIT));
        assertTrue(withoutKey.isTimeSensitive(TransportationMode.TRANSIT),
                "timetables vary regardless of any driving provider");
    }

    @Test
    void drivingIsTimeSensitiveOnlyWhenATrafficAwareProviderIsConfigured() {
        DistanceServiceTravelTimeEstimator withTraffic = new DistanceServiceTravelTimeEstimator(
                (from, to, mode, departure) -> 10, true);
        DistanceServiceTravelTimeEstimator withoutTraffic = new DistanceServiceTravelTimeEstimator(
                (from, to, mode, departure) -> 10, false);

        assertTrue(withTraffic.isTimeSensitive(TransportationMode.DRIVING));
        assertFalse(withoutTraffic.isTimeSensitive(TransportationMode.DRIVING),
                "without a traffic provider every bucket would fetch the same number");
    }

    @Test
    void theDepartureTimeIsPassedThroughToTheSharedService() {
        LocalDateTime[] seen = new LocalDateTime[1];
        DistanceService recording = (from, to, mode, departure) -> {
            seen[0] = departure;
            return 12;
        };

        new DistanceServiceTravelTimeEstimator(recording)
                .estimate(FROM, TO, TransportationMode.TRANSIT, DEPARTURE);

        assertEquals(DEPARTURE, seen[0]);
    }

    @Test
    void aTripWideForecastBecomesAContextThatCannotInfluenceTiming() {
        WeatherService service = requested -> new WeatherWarning(FROM, LocalTime.of(12, 0),
                "Rain", WeatherSeverity.HIGH, "Heavy rain expected");

        WeatherContext context = new WeatherServiceContextGateway(service).contextFor(trip());

        assertTrue(context.isAvailable());
        assertFalse(context.canDistinguishTimes(),
                "one severity for the whole trip says nothing about when to go out");
        assertEquals(WeatherSeverity.HIGH, context.severityAt(LocalTime.of(9, 0)));
    }

    @Test
    void aFailingWeatherServiceCostsTheTravellerNothing() {
        WeatherService failing = requested -> {
            throw new IllegalStateException("forecast service unavailable");
        };

        WeatherContext context = new WeatherServiceContextGateway(failing).contextFor(trip());

        assertFalse(context.isAvailable(),
                "weather is a preference; losing it must never cost the schedule");
    }

    @Test
    void aMissingOrSeverityLessWarningIsTreatedAsNoForecast() {
        WeatherService none = requested -> null;
        WeatherService blank = requested -> new WeatherWarning(FROM, LocalTime.of(12, 0),
                "Unknown", null, "");

        assertFalse(new WeatherServiceContextGateway(none).contextFor(trip()).isAvailable());
        assertFalse(new WeatherServiceContextGateway(blank).contextFor(trip()).isAvailable());
    }
}
