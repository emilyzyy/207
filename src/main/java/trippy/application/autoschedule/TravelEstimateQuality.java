package trippy.application.autoschedule;

/**
 * How much confidence a travel estimate carries, so the Preview can disclose
 * estimates honestly instead of presenting every number as routed truth.
 */
public enum TravelEstimateQuality {
    /** A live routing provider answered for this leg. */
    ROUTED,
    /** The provider failed and a distance-based approximation was substituted. */
    ESTIMATED,
    /**
     * The provider cannot say. The shared {@code DistanceService} returns a bare
     * {@code int} and falls back to a haversine approximation internally, so an
     * adapter delegating to it genuinely cannot distinguish the two cases.
     */
    UNKNOWN
}
