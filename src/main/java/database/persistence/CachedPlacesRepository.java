package database.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import entity.entities.Activity;
import use_case.ports.ActivityRepository;
import use_case.ports.PlacesWriter;

/** In-memory ActivityRepository that caches place results sent from the JavaScript frontend. */
public final class CachedPlacesRepository implements ActivityRepository, PlacesWriter {
    private final Map<String, Activity> places = new LinkedHashMap<>();

    /**
     * Performs the a dd al l operation.
     * @param activities the a ct iv it ie s value
     */
    public synchronized void addAll(List<Activity> activities) {
        for (Activity activity : activities) {
            places.put(activity.getId(), activity);
        }
    }

    /** Performs the c le ar operation. */
    public synchronized void clear() {
        places.clear();
    }

    @Override
    public synchronized List<Activity> findAll() {
        return new ArrayList<>(places.values());
    }

    @Override
    public synchronized Optional<Activity> findById(String id) {
        return Optional.ofNullable(places.get(id));
    }
}
