package interface_adapter.gateways;

import java.time.LocalDateTime;

import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import use_case.autoschedule.TravelEstimate;
import use_case.autoschedule.TravelEstimateQuality;
import use_case.autoschedule.TravelTimeEstimator;
import use_case.ports.DistanceService;

/**
 * Supplies Autoschedule's travel estimates from the team's shared {@code DistanceService}.
 *
 * <p>All routing stays where it already lives — OSRM for walking, Transitous for transit,
 * TomTom for driving. This class exists only to answer the two questions scheduling asks
 * that the shared port cannot: how much to trust a number, and whether a mode varies with
 * departure time at all.</p>
 *
 * <p>Whether driving is traffic-aware is a configuration decision made by the composition
 * root; this adapter only ever hears the resulting boolean.</p>
 */
public final class DistanceServiceTravelTimeEstimator implements TravelTimeEstimator {

    private final DistanceService distances;
    private final boolean trafficAwareDriving;

    public DistanceServiceTravelTimeEstimator(DistanceService distances) {
        this(distances, false);
    }

    public DistanceServiceTravelTimeEstimator(DistanceService distances, boolean trafficAwareDriving) {
        if (distances == null) {
            throw new IllegalArgumentException("Distance service is required");
        }
        this.distances = distances;
        this.trafficAwareDriving = trafficAwareDriving;
    }

    @Override
    public TravelEstimate estimate(Location from, Location to,
                                   TransportationMode mode, LocalDateTime departure) {
        final int minutes = distances.estimateTravelMinutes(from, to, mode, departure);
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
}
