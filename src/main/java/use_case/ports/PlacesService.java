package use_case.ports;

import entity.entities.Activity;
import java.util.List;

public interface PlacesService {
    /** Searches for places matching the query around a destination. */
    List<Activity> search(String destination, String query);

    /**
     * Searches for places inside a geographic bounding box.
     *
     * @param south the southern latitude of the window
     * @param west the western longitude of the window
     * @param north the northern latitude of the window
     * @param east the eastern longitude of the window
     * @param maxResults the maximum number of places to return for this window
     */
    default List<Activity> searchInBounds(double south, double west, double north, double east, int maxResults) {
        return java.util.Collections.emptyList();
    }
}