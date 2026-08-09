package interface_adapter.places;

import use_case.ports.PlacesService;
import use_case.ports.PlacesWriter;
import use_case.ports.DestinationGeocoder;
import use_case.ports.ActivityRepository;
import entity.valueobjects.GeoPoint;
import use_case.search.ActivitySearchRequest;
import use_case.search.ActivitySearchResult;
import use_case.search.PlaceSearchException;
import use_case.search.SearchFailure;
import use_case.search.SearchSource;
import entity.entities.Activity;
import java.util.List;

/**
 * Decorates discovered-place lookup by copying successful results into a repository. When the
 * underlying live service is unavailable or returns nothing, previously cached places are served
 * so discovery never leaves the user with an empty screen.
 */
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
        List<Activity> results;
        try {
            results = delegate.search(destination, query);
        } catch (PlaceSearchException exception) {
            return cachedFallback(exception);
        }
        if (results.isEmpty()) {
            return cachedFallback(null);
        }
        cache.addAll(results);
        return results;
    }

    @Override
    public ActivitySearchResult search(ActivitySearchRequest request) {
        ActivitySearchResult result;
        try {
            result = delegate.search(request);
        } catch (PlaceSearchException exception) {
            return fallbackResult(exception.getFailure());
        }
        cache.addAll(result.getActivities());
        if (result.getActivities().isEmpty() && result.getFailure() != SearchFailure.NONE) {
            return fallbackResult(result.getFailure());
        }
        return result;
    }

    @Override
    public List<Activity> searchInBounds(double south, double west, double north, double east,
                                         int maxResults) {
        List<Activity> results;
        try {
            results = delegate.searchInBounds(south, west, north, east, maxResults);
        } catch (PlaceSearchException exception) {
            return cachedFallback(exception);
        }
        if (results.isEmpty()) {
            return cachedFallback(null);
        }
        cache.addAll(results);
        return results;
    }

    /** Serves cached places when the live call failed; rethrows if nothing is cached either. */
    private List<Activity> cachedFallback(PlaceSearchException exception) {
        List<Activity> cached = cachedActivities();
        if (!cached.isEmpty()) {
            return cached;
        }
        if (exception != null) {
            throw exception;
        }
        return List.of();
    }

    private ActivitySearchResult fallbackResult(SearchFailure failure) {
        List<Activity> cached = cachedActivities();
        return new ActivitySearchResult(cached, SearchSource.LOCAL, !cached.isEmpty(), failure);
    }

    private List<Activity> cachedActivities() {
        if (!(cache instanceof ActivityRepository)) {
            return List.of();
        }
        return ((ActivityRepository) cache).findAll();
    }

    @Override
    public GeoPoint geocode(String destination) {
        if (!(delegate instanceof DestinationGeocoder)) {
            throw new IllegalStateException("Destination geocoding is unavailable");
        }
        return ((DestinationGeocoder) delegate).geocode(destination);
    }
}
