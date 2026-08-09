package use_case.ports;

import entity.entities.Activity;
import java.util.List;

/** Discovers activity sets by destination or visible map bounds. */
public interface NearbyActivityDiscovery {
    List<Activity> around(String destination, int limit);
    List<Activity> inBounds(double south, double west, double north, double east, int limit);

    /**
     * Returns cached whole-city results for the destination filtered to the given box, but only
     * when that box lies fully inside an already-completed around query's coverage circle.
     * Returns {@code null} when there is no usable coverage, so callers fall back to a live
     * {@link #inBounds} lookup. The default implementation knows nothing about coverage and
     * always asks for the live lookup.
     */
    default List<Activity> cachedInBounds(String destination,
                                          double south, double west, double north, double east) {
        return null;
    }
}
