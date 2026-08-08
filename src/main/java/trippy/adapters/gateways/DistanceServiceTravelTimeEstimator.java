package trippy.adapters.gateways;

import trippy.application.autoschedule.TravelEstimate;
import trippy.application.autoschedule.TravelEstimateQuality;
import trippy.application.autoschedule.TravelTimeEstimator;
import trippy.application.ports.DistanceService;
import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import trippy.infrastructure.config.DotEnv;
import java.time.LocalDateTime;

/**
 * Supplies Autoschedule's travel estimates from the team's shared {@code DistanceService}.
 *
 * <p>All routing stays where it already lives — OSRM for walking, Transitous for transit,
 * TomTom for driving. This class exists only to answer the two questions scheduling asks
 * that the shared port cannot: how much to trust a number, and whether a mode varies with
 * departure time at all.</p>
 */
public final class DistanceServiceTravelTimeEstimator implements TravelTimeEstimator {

    /** Property and variable the routing adapter reads its TomTom key from. */
    static final String TOMTOM_KEY_PROPERTY = "tomtom.api.key";
    static final String TOMTOM_KEY_ENVIRONMENT_VARIABLE = "TOMTOM_API_KEY";

    private final DistanceService distances;
    private final boolean trafficAwareDriving;

    public DistanceServiceTravelTimeEstimator(DistanceService distances) {
        this(distances, tomtomKeyPresent());
    }

    DistanceServiceTravelTimeEstimator(DistanceService distances, boolean trafficAwareDriving) {
        if (distances == null) {
            throw new IllegalArgumentException("Distance service is required");
        }
        this.distances = distances;
        this.trafficAwareDriving = trafficAwareDriving;
    }

    @Override
    public TravelEstimate estimate(Location from, Location to,
                                   TransportationMode mode, LocalDateTime departure) {
        int minutes = distances.estimateTravelMinutes(from, to, mode, departure);
        return new TravelEstimate(minutes, TravelEstimateQuality.UNKNOWN);
    }

    /**
     * Walking never varies: the walking provider takes no departure time. Transit does,
     * because timetables do. Driving varies only when a traffic-aware provider is
     * configured; without a key the driving numbers come from a static road network, so
     * fetching several departure buckets would issue identical requests for no benefit.
     */
    @Override
    public boolean isTimeSensitive(TransportationMode mode) {
        if (mode == TransportationMode.TRANSIT) {
            return true;
        }
        if (mode == TransportationMode.DRIVING) {
            return trafficAwareDriving;
        }
        return false;
    }

    /** Presence check only. The key itself is never read into scheduling code or logged. */
    private static boolean tomtomKeyPresent() {
        return DotEnv.get(TOMTOM_KEY_ENVIRONMENT_VARIABLE, TOMTOM_KEY_PROPERTY) != null;
    }
}
