package app;

import use_case.ports.ActivityRepository;
import use_case.ports.PlacesService;
import use_case.ports.PlacesWriter;
import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlaceHydratorTest {
    @Test
    void hydrateUsesCachedActivityWhenPresent() {
        Activity cached = activity("osm-1", "Museum");
        FakeActivities activities = new FakeActivities(cached);
        PlaceHydrator hydrator = new PlaceHydrator(activities, new EmptyPlaces(), activities);

        Activity result = hydrator.hydrate("osm-1", "Museum", 43.65, -79.38, "Toronto");

        assertEquals("Museum", result.getName());
        assertEquals(4.5, result.getRating());
    }

    @Test
    void hydrateFallsBackToStubWhenLookupMisses() {
        FakeActivities activities = new FakeActivities();
        PlaceHydrator hydrator = new PlaceHydrator(activities, new EmptyPlaces(), activities);

        Activity result = hydrator.hydrate("osm-missing", "Hidden Cafe", 43.7, -79.4, "Toronto");

        assertEquals("osm-missing", result.getId());
        assertEquals("Hidden Cafe", result.getName());
        assertEquals(43.7, result.getLocation().getLatitude());
        assertTrue(result.getRating() == 0.0);
    }

    private static Activity activity(String id, String name) {
        return new Activity(id, name, ActivityCategory.MUSEUM,
                new Location(43.65, -79.38, "Downtown"), 4.5, 90,
                LocalTime.of(10, 0), LocalTime.of(17, 0), IndoorOutdoorType.INDOOR, "low");
    }

    private static final class FakeActivities implements ActivityRepository, PlacesWriter {
        private final MapStore store = new MapStore();

        private FakeActivities(Activity... seeded) {
            for (Activity activity : seeded) {
                store.put(activity);
            }
        }

        @Override
        public List<Activity> findAll() {
            return store.all();
        }

        @Override
        public Optional<Activity> findById(String id) {
            return store.get(id);
        }

        @Override
        public void addAll(List<Activity> activities) {
            for (Activity activity : activities) {
                store.put(activity);
            }
        }
    }

    private static final class EmptyPlaces implements PlacesService {
        @Override
        public List<Activity> search(String destination, String query) {
            return Collections.emptyList();
        }
    }

    private static final class MapStore {
        private final java.util.Map<String, Activity> map = new java.util.HashMap<String, Activity>();

        void put(Activity activity) {
            map.put(activity.getId(), activity);
        }

        Optional<Activity> get(String id) {
            return Optional.ofNullable(map.get(id));
        }

        List<Activity> all() {
            return new java.util.ArrayList<Activity>(map.values());
        }
    }
}
