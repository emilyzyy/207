package interface_adapter.places;

import entity.entities.Activity;
import interface_adapter.mock.MockPlacesService;
import database.persistence.CachedPlacesRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CachingPlacesServiceTest {

    @Test
    void copiesDiscoveredActivitiesIntoTheSharedRepository() {
        CachedPlacesRepository cache = new CachedPlacesRepository();
        CachingPlacesService service =
                new CachingPlacesService(new MockPlacesService(), cache);

        List<Activity> results = service.search("Toronto", "museum");

        assertEquals(results.size(), cache.findAll().size());
        assertEquals(results.get(0).getId(),
                cache.findById(results.get(0).getId()).orElseThrow().getId());
    }

@Test
    void delegatesInBoundsSearchAndCachesResults() {
        CachedPlacesRepository cache = new CachedPlacesRepository();
        CachingPlacesService service =
                new CachingPlacesService(new MockPlacesService(), cache);

        List<Activity> results = service.searchInBounds(43.0, -79.5, 44.0, -79.0, 100);

assertTrue(!results.isEmpty());
        assertEquals(results.size(), cache.findAll().size());
    }
}
