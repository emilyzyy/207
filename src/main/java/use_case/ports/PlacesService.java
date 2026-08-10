package use_case.ports;

import java.util.List;

import entity.entities.Activity;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import use_case.search.SearchFailure;
import use_case.search.SearchSource;

public interface PlacesService extends ActivitySearchGateway {
    /**
     * Searches for places matching the query around a destination.
     * @param query the q ue ry value
     * @param destination the d es ti na ti on value
     * @return the result of the operation
     */
    List<Activity> search(String destination, String query);

    /**
     * Compatibility implementation for simple/offline place providers. Rich search adapters
     * override this method to report their actual source and failure state.
     */
    @Override
    default ActivitySearchResult search(ActivitySearchRequest request) {
        final List<Activity> activities = search(request.getDestination(), request.getQuery());
        return new ActivitySearchResult(activities, SearchSource.LOCAL, false,
                activities.isEmpty() ? SearchFailure.NO_MATCH : SearchFailure.NONE);
    }

    /**
     * Searches for places inside a geographic bounding box.
     *
     * @param north the northern latitude of the window
     * @param east the eastern longitude of the window
     * @param maxResults the maximum number of places to return for this window
     * @param destination the trip destination whose viewport is being loaded
     * @param south the southern latitude of the window
     * @param west the western longitude of the window
      * @return the result of the operation
     */
    default List<Activity> searchInBounds(String destination, double south, double west,
                                          double north, double east, int maxResults) {
        return java.util.Collections.emptyList();
    }

    /**
     * Compatibility view of {@link #searchInBounds(String, double, double, double, double, int)}
     * for providers that do not track a destination for bounding-box searches.
      * @param south the s ou th value
      * @param west the w es t value
      * @param east the e as t value
      * @param north the n or th value
      * @return the result of the operation
     */
    default List<Activity> searchInBounds(double south, double west, double north, double east,
                                          int maxResults) {
        return searchInBounds("", south, west, north, east, maxResults);
    }
}
