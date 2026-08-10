package use_case.ports;

import java.util.List;

import entity.entities.Activity;

/**
 * Supplies places for a visible map window. Implementations should perform blocking
 * lookups off the Swing event-dispatch thread (the loader is invoked on a worker thread).
 */
public interface ViewportPlacesLoader {
    /**
     * Loads up to {@code maxResults} places inside a geographic window.
     *
     * @param south the southern latitude of the window
     * @param west the western longitude of the window
     * @param north the northern latitude of the window
     * @param east the eastern longitude of the window
     * @param maxResults the maximum number of places to return
     * @return the places found inside the window
     */
    List<Activity> load(double south, double west, double north, double east, int maxResults);
}
