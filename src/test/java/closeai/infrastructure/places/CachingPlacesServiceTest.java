package closeai.infrastructure.places;

import closeai.domain.entities.Activity;
import closeai.infrastructure.mock.MockPlacesService;
import closeai.infrastructure.persistence.CachedPlacesRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
