package interface_adapter.places;

import use_case.ports.PlacesService;
import use_case.ports.PlacesWriter;
import use_case.ports.DestinationGeocoder;
import entity.valueobjects.GeoPoint;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import entity.entities.Activity;
import java.util.List;

/** Decorates discovered-place lookup by copying successful results into a repository. */
public final class CachingPlacesService
        implements PlacesService, DestinationGeocoder {
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
        ActivitySearchResult result = delegate.search(request);
        cache.addAll(result.getActivities());
        return result;
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
