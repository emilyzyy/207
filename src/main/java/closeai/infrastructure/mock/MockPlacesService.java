package closeai.infrastructure.mock;

import closeai.application.ports.ActivityRepository;
import closeai.application.ports.PlacesService;
import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class MockPlacesService implements PlacesService, ActivityRepository {
    private final List<Activity> activities = Arrays.asList(
        activity("rom", "Royal Ontario Museum", ActivityCategory.MUSEUM, 43.6677, -79.3948,
                 "100 Queens Park", 4.7, 120, IndoorOutdoorType.INDOOR, "Low"),
        activity("cn-tower", "CN Tower", ActivityCategory.ATTRACTION, 43.6426, -79.3871,
                 "290 Bremner Blvd", 4.6, 90, IndoorOutdoorType.MIXED, "Low"),
        activity("islands", "Toronto Islands", ActivityCategory.OUTDOOR, 43.6214, -79.3789,
                 "Toronto Islands", 4.8, 180, IndoorOutdoorType.OUTDOOR, "High"),
        activity("pai", "Pai Northern Thai Kitchen", ActivityCategory.FOOD, 43.6477, -79.3886,
                 "18 Duncan St", 4.5, 60, IndoorOutdoorType.INDOOR, "Low"),
        activity("kensington", "Kensington Market", ActivityCategory.SHOPPING, 43.6545, -79.4005,
                 "Kensington Market", 4.4, 90, IndoorOutdoorType.OUTDOOR, "Medium"),
        activity("ago", "Art Gallery of Ontario", ActivityCategory.MUSEUM, 43.6536, -79.3925,
                 "317 Dundas St W", 4.7, 120, IndoorOutdoorType.INDOOR, "Low"),
        activity("balzacs", "Balzac's Coffee", ActivityCategory.COFFEE, 43.6503, -79.3596,
                 "1 Trinity St", 4.3, 45, IndoorOutdoorType.INDOOR, "Low")
    );

    private static Activity activity(String id, String name, ActivityCategory category, double lat,
                                     double lng, String address, double rating, int duration,
                                     IndoorOutdoorType type, String risk) {
        return new Activity(id, name, category, new Location(lat, lng, address), rating, duration,
                            LocalTime.of(9, 0), LocalTime.of(21, 0), type, risk);
    }

    public List<Activity> search(String destination, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<Activity> result = new ArrayList<Activity>();
        for (Activity activity : activities) {
            if (needle.isEmpty() || activity.getName().toLowerCase().contains(needle)
                    || activity.getCategory().name().toLowerCase().contains(needle)) result.add(activity);
        }
        return result;
    }

    public List<Activity> findAll() { return new ArrayList<Activity>(activities); }
    public Optional<Activity> findById(String id) {
        for (Activity activity : activities) if (activity.getId().equals(id)) return Optional.of(activity);
        return Optional.empty();
    }
}
