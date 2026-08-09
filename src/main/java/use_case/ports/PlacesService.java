package use_case.ports;

import entity.entities.Activity;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import use_case.search.SearchFailure;
import use_case.search.SearchSource;
import java.util.List;

public interface PlacesService extends ActivitySearchGateway {
    /** Searches for places matching the query around a destination. */
    List<Activity> search(String destination, String query);

    /**
     * Compatibility implementation for simple/offline place providers. Rich search adapters
     * override this method to report their actual source and failure state.
     */
    @Override
    default ActivitySearchResult search(ActivitySearchRequest request) {
        List<Activity> activities = search(request.getDestination(), request.getQuery());
        return new ActivitySearchResult(activities, SearchSource.LOCAL, false,
                activities.isEmpty() ? SearchFailure.NO_MATCH : SearchFailure.NONE);
    }

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
