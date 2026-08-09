package use_case.usecases;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import use_case.ports.ActivityRepository;
import use_case.ports.PlacesService;
import use_case.ports.PlacesWriter;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Rebuilds a full {@link Activity} from a lean place ref using cache/search, or a stub on miss.
 */
public final class PlaceHydrator {
    private final ActivityRepository activities;
    private final PlacesService places;
    private final PlacesWriter placesWriter;

    public PlaceHydrator(ActivityRepository activities, PlacesService places, PlacesWriter placesWriter) {
        if (activities == null || places == null || placesWriter == null) {
            throw new IllegalArgumentException("Place hydrator dependencies are required");
        }
        this.activities = activities;
        this.places = places;
        this.placesWriter = placesWriter;
    }

    public Activity hydrate(String placeId, String name, double latitude, double longitude,
                            String destinationHint) {
        if (placeId == null || placeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Place id is required");
        }
        String id = placeId.trim();
        Optional<Activity> cached = activities.findById(id);
        if (cached.isPresent()) {
            return cached.get();
        }

        String query = name == null ? "" : name.trim();
        String destination = destinationHint == null || destinationHint.trim().isEmpty()
                ? query : destinationHint.trim();
        try {
            List<Activity> found = places.search(destination, query);
            for (Activity candidate : found) {
                if (id.equals(candidate.getId())) {
                    placesWriter.addAll(java.util.Collections.singletonList(candidate));
                    return candidate;
                }
            }
            for (Activity candidate : found) {
                if (near(candidate.getLocation(), latitude, longitude)
                        && namesMatch(candidate.getName(), name)) {
                    placesWriter.addAll(java.util.Collections.singletonList(candidate));
                    return candidate;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to stub so reopen still works offline / on lookup failure.
        }
        return stub(id, name, latitude, longitude);
    }

    public static Activity stub(String placeId, String name, double latitude, double longitude) {
        String label = name == null || name.trim().isEmpty() ? "Saved place" : name.trim();
        return new Activity(
                placeId,
                label,
                ActivityCategory.ATTRACTION,
                new Location(latitude, longitude, label),
                0.0,
                60,
                LocalTime.of(9, 0),
                LocalTime.of(21, 0),
                IndoorOutdoorType.MIXED,
                "unknown");
    }

    private static boolean near(Location location, double latitude, double longitude) {
        if (location == null) {
            return false;
        }
        double dLat = Math.abs(location.getLatitude() - latitude);
        double dLon = Math.abs(location.getLongitude() - longitude);
        return dLat < 0.01 && dLon < 0.01;
    }

    private static boolean namesMatch(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }
}
