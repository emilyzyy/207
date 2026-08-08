package trippy.infrastructure.places;

import trippy.application.ports.PlacesService;
import trippy.application.ports.PlacesWriter;
import trippy.application.ports.ActivitySearchGateway;
import trippy.application.ports.DestinationGeocoder;
import trippy.application.search.GeoPoint;
import trippy.application.search.ActivitySearchRequest;
import trippy.application.search.ActivitySearchResult;
import trippy.domain.entities.Activity;
import java.util.List;

/** Decorates discovered-place lookup by copying successful results into a repository. */
public final class CachingPlacesService
        implements PlacesService, ActivitySearchGateway, DestinationGeocoder {
    private final PlacesService delegate;
    private final PlacesWriter cache;

    public CachingPlacesService(PlacesService delegate, PlacesWriter cache) {
        if (delegate == null || cache == null) {
            throw new IllegalArgumentException("Caching places dependencies are required");
        }
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public List<Activity> search(String destination, String query) {
        List<Activity> results = delegate.search(destination, query);
        cache.addAll(results);
        return results;
    }

    @Override
    public ActivitySearchResult search(ActivitySearchRequest request) {
        if (delegate instanceof ActivitySearchGateway) {
            ActivitySearchResult result = ((ActivitySearchGateway) delegate).search(request);
            cache.addAll(result.getActivities());
            return result;
        }
        List<Activity> activities = search(request.getDestination(), request.getQuery());
        return new ActivitySearchResult(activities,
                trippy.application.search.SearchSource.LOCAL, false,
                activities.isEmpty() ? trippy.application.search.SearchFailure.NO_MATCH
                        : trippy.application.search.SearchFailure.NONE);
    }

    @Override
    public List<Activity> searchInBounds(double south, double west, double north, double east,
                                         int maxResults) {
        List<Activity> results = delegate.searchInBounds(south, west, north, east, maxResults);
        cache.addAll(results);
        return results;
    }

    @Override
    public GeoPoint geocode(String destination) {
        if (!(delegate instanceof DestinationGeocoder)) {
            throw new IllegalStateException("Destination geocoding is unavailable");
        }
        return ((DestinationGeocoder) delegate).geocode(destination);
    }
}
