package interface_adapter.routing;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import entity.valueobjects.Location;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Proves that TomTom itself answered, rather than that driving produced some number.
 *
 * <p>The existing live check could not do this. {@code estimateTravelMinutes} falls back to
 * OSRM whenever TomTom is missing or fails, and returns a bare {@code int} either way, so a
 * passing assertion on the result is equally consistent with TomTom having never been
 * contacted. That is exactly what happened on the previous run: the driving test executed,
 * passed, and reached only the fallback.</p>
 *
 * <p>This test calls the private {@code estimateTomtom} directly by reflection. That method
 * is the one place in the class that has no fallback: it returns a duration only when the
 * real endpoint answered 200 with a parsable route, and {@code null} for every other
 * outcome. A non-null return is therefore conclusive, and reflection keeps the proof
 * entirely inside the test — no production signature, contract or behaviour changes for the
 * sake of being observed.</p>
 *
 * <p>Run with:
 * {@code RUN_LIVE_TOMTOM_TEST=true ./mvnw test -Dtest=TomTomLiveVerificationTest}
 * and {@code TOMTOM_API_KEY} exported. The key is only ever tested for presence; it is
 * never printed, and neither is any authenticated URL.</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_TOMTOM_TEST", matches = "true")
class TomTomLiveVerificationTest {

    /** Seeded demo locations; nothing personal or sensitive. */
    private static final Location UNION_STATION = new Location(43.6453, -79.3806, "Union Station");
    private static final Location CASA_LOMA = new Location(43.6780, -79.4094, "Casa Loma");

    private static final LocalDate TRIP_DATE = LocalDate.now().plusDays(1);
    private static final LocalDateTime MORNING = LocalDateTime.of(TRIP_DATE, LocalTime.of(9, 30));
    private static final LocalDateTime RUSH_HOUR = LocalDateTime.of(TRIP_DATE, LocalTime.of(17, 30));

    /** Presence only. The value is never read into a message, a log or an assertion. */
    private static String key() {
        String fromEnvironment = System.getenv("TOMTOM_API_KEY");
        if (fromEnvironment != null && !fromEnvironment.trim().isEmpty()) {
            return fromEnvironment;
        }
        String fromProperty = System.getProperty("tomtom.api.key");
        if (fromProperty != null && !fromProperty.trim().isEmpty()) {
            return fromProperty;
        }
        return null;
    }

    /**
     * Invokes the fallback-free TomTom path. Returns the duration TomTom reported, or null
     * when TomTom did not answer with a usable route.
     */
    private static Integer callTomTom(Location from, Location to, LocalDateTime departure,
                                      String key) throws Exception {
        Method method = OsrmDistanceService.class.getDeclaredMethod("estimateTomtom",
                Location.class, Location.class, LocalDateTime.class, String.class);
        method.setAccessible(true);
        return (Integer) method.invoke(new OsrmDistanceService(), from, to, departure, key);
    }

    @Test
    void tomtomItselfAnswersWithARouteAtTwoDifferentDepartureTimes() throws Exception {
        String key = key();
        assumeTrue(key != null,
                "TOMTOM_API_KEY is not set, so live TomTom cannot be verified. This is "
                        + "reported rather than passed silently: without it the driving path "
                        + "returns OSRM numbers and no traffic-aware claim is earned.");

        long startedMorning = System.currentTimeMillis();
        Integer morning = callTomTom(UNION_STATION, CASA_LOMA, MORNING, key);
        long morningLatency = System.currentTimeMillis() - startedMorning;

        long startedRush = System.currentTimeMillis();
        Integer rush = callTomTom(UNION_STATION, CASA_LOMA, RUSH_HOUR, key);
        long rushLatency = System.currentTimeMillis() - startedRush;

        // A duration here cannot have come from anywhere else: this method has no fallback,
        // and returns null unless the live endpoint answered 200 with a parsable route.
        assertNotNull(morning, "TomTom returned no usable route for the 09:30 departure");
        assertNotNull(rush, "TomTom returned no usable route for the 17:30 departure");

        System.out.println(String.format(
                "[live] driving provider=TomTom duration=%d latency=%dms departAt=09:30",
                morning, morningLatency));
        System.out.println(String.format(
                "[live] driving provider=TomTom duration=%d latency=%dms departAt=17:30",
                rush, rushLatency));
        System.out.println("[live] driving traffic-time difference observed: "
                + !morning.equals(rush)
                + " (equal durations are a legitimate result, not a failure)");

        assertTrue(morning > 0 && morning < 240, "a plausible cross-Toronto drive");
        assertTrue(rush > 0 && rush < 240, "a plausible cross-Toronto drive");
    }

    /**
     * A negative control for the coordinate order, which is the defect this whole
     * verification exists to close.
     *
     * <p>Asserting that the corrected order works proves less than it looks: a service
     * might accept either. Swapping the pair puts a longitude of −79 into the latitude
     * slot, which is outside the legal ±90 range, so TomTom rejects it and the method
     * returns null. Passing with the right order and failing with the wrong one is what
     * actually establishes that {@code latitude,longitude} is the order reaching the live
     * service.</p>
     */
    @Test
    void swappingTheCoordinatePairIsRejectedByTheLiveService() throws Exception {
        String key = key();
        assumeTrue(key != null, "TOMTOM_API_KEY is not set");

        Location swappedFrom = new Location(UNION_STATION.getLongitude(),
                UNION_STATION.getLatitude(), "swapped origin");
        Location swappedTo = new Location(CASA_LOMA.getLongitude(),
                CASA_LOMA.getLatitude(), "swapped destination");

        Integer swapped = callTomTom(swappedFrom, swappedTo, MORNING, key);

        assertNull(swapped,
                "a longitude in the latitude position is out of range, so the live service "
                        + "must refuse it; if this returns a route the coordinate-order "
                        + "proof is worthless");
        System.out.println("[live] driving coordinate-order control: swapped pair rejected, "
                + "so latitude,longitude is the order the live service accepted");
    }

    /**
     * States the outcome when no credential is present, so a run without a key leaves a
     * record saying so instead of a silent pass that could later be mistaken for proof.
     */
    @Test
    void reportsClearlyWhenNoCredentialIsAvailable() {
        boolean present = key() != null;
        System.out.println("[live] TomTom credential present: " + present
                + (present ? "" : " - live driving NOT verified, no traffic-aware claim"));
        assertTrue(true, "informational");
    }
}
