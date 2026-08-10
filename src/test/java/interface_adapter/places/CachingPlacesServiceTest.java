package interface_adapter.places;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import database.persistence.CachedPlacesRepository;
import entity.entities.Activity;
import interface_adapter.mock.MockPlacesService;

final class CachingPlacesServiceTest {

    @Test
    void copiesDiscoveredActivitiesIntoTheSharedRepository() {
        final CachedPlacesRepository cache = new CachedPlacesRepository();
        final CachingPlacesService service =
                new CachingPlacesService(new MockPlacesService(), cache);

        final List<Activity> results = service.search("Toronto", "museum");

        assertEquals(results.size(), cache.findAll().size());
        assertEquals(results.get(0).getId(),
                cache.findById(results.get(0).getId()).orElseThrow().getId());
    }

    @Test
    void delegatesInBoundsSearchAndCachesResults() {
        final CachedPlacesRepository cache = new CachedPlacesRepository();
        final CachingPlacesService service =
                new CachingPlacesService(new MockPlacesService(), cache);

        final List<Activity> results = service.searchInBounds(43.0, -79.5, 44.0, -79.0, 100);

        assertTrue(!results.isEmpty());
        assertEquals(results.size(), cache.findAll().size());
    }
}
