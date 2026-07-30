package closeai.infrastructure.places;

import closeai.application.ports.PlacesService;
import closeai.application.ports.PlacesWriter;
import closeai.domain.entities.Activity;
import java.util.List;

/** Decorates discovered-place lookup by copying successful results into a repository. */
public final class CachingPlacesService implements PlacesService {
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
}
