package use_case.ports;

import java.util.List;

import entity.entities.Activity;

/** Discovers activity sets by destination or visible map bounds. */
public interface NearbyActivityDiscovery {
    /**
     * Performs the a ro un d operation.
     * @param limit the l im it value
     * @param destination the d es ti na ti on value
     * @return the result of the operation
     */
    List<Activity> around(String destination, int limit);

    /**
     * Performs the i nb ou nd s operation.
     * @param north the n or th value
     * @param east the e as t value
     * @param limit the l im it value
     * @param west the w es t value
     * @param south the s ou th value
     * @return the result of the operation
     */
    List<Activity> inBounds(double south, double west, double north, double east, int limit);

    /**
     * Returns cached whole-city results for the destination filtered to the given box, but only
     * when that box lies fully inside an already-completed around query's coverage circle.
     * Returns {@code null} when there is no usable coverage, so callers fall back to a live
     * {@link #inBounds} lookup. The default implementation knows nothing about coverage and
     * always asks for the live lookup.
      * @param destination the d es ti na ti on value
      * @return the result of the operation
     */
    default List<Activity> cachedInBounds(String destination,
                                          double south, double west, double north, double east) {
        return null;
    }
}
