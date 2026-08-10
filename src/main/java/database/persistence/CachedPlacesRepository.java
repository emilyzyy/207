package database.persistence;

import use_case.ports.ActivityRepository;
import use_case.ports.PlacesWriter;
import entity.entities.Activity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory ActivityRepository that caches place results sent from the JavaScript frontend. */
public final class CachedPlacesRepository implements ActivityRepository, PlacesWriter {
    private final Map<String, Activity> places = new LinkedHashMap<>();

    public synchronized void addAll(List<Activity> activities) {
        for (Activity activity : activities) {
            places.put(activity.getId(), activity);
        }
    }

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
