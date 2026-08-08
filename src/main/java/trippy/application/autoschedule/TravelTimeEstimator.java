package trippy.application.autoschedule;

import trippy.domain.valueobjects.Location;
import trippy.domain.valueobjects.TransportationMode;
import java.time.LocalDateTime;

/**
 * Inward-facing travel contract owned by the Autoschedule use case.
 *
 * <p>The shared {@code DistanceService} already accepts a departure time, but it
 * returns a bare {@code int}. This port adds exactly the two things scheduling
 * needs on top of that and nothing else:</p>
 *
 * <ul>
 *   <li>{@link TravelEstimateQuality} so the Preview can disclose which legs are
 *       approximations rather than routed answers;</li>
 *   <li>{@link #isTimeSensitive(TransportationMode)} so the bucket prefetch is
 *       skipped for modes whose provider has no time input at all, where every
 *       bucket would return an identical number.</li>
 * </ul>
 *
 * <p>Implementations delegate to the existing routing adapter; no routing logic
 * is reimplemented here.</p>
 */
public interface TravelTimeEstimator {

    /** Estimated travel time for one directed leg leaving at {@code departure}. */
    TravelEstimate estimate(Location from, Location to,
                            TransportationMode mode, LocalDateTime departure);

    /**
     * Whether estimates for {@code mode} actually vary with departure time.
     * When false the caller collapses all departure periods into a single bucket.
     */
    boolean isTimeSensitive(TransportationMode mode);
}
