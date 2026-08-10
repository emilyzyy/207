package interface_adapter.mock;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import use_case.ports.ActivityRepository;
import use_case.ports.PlacesService;

public final class MockPlacesService implements PlacesService, ActivityRepository {
    private final List<Activity> activities = Arrays.asList(
        activity("rom", "Royal Ontario Museum", ActivityCategory.MUSEUM, 43.6677, -79.3948,
                 "100 Queens Park", 4.7, 120, IndoorOutdoorType.INDOOR, "Low"),
        activity("cn-tower", "CN Tower", ActivityCategory.ATTRACTION, 43.6426, -79.3871,
                 "290 Bremner Blvd", 4.6, 90, IndoorOutdoorType.MIXED, "Low"),
        activity("islands", "Toronto Islands", ActivityCategory.PARKS_NATURE, 43.6214, -79.3789,
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

    /**
     * Performs the s ea rc h operation.
     * @param query the q ue ry value
     * @param destination the d es ti na ti on value
     * @return the result of the operation
     */
    public List<Activity> search(String destination, String query) {
        final String needle = query == null ? "" : query.trim().toLowerCase();
        final List<Activity> result = new ArrayList<Activity>();
        for (Activity activity : activities) {
            if (needle.isEmpty() || activity.getName().toLowerCase().contains(needle)
                    || activity.getCategory().name().toLowerCase().contains(needle)) {
                result.add(activity);
            }
        }
        return result;
    }

    /**
     * Performs the s ea rc hi nb ou nd s operation.
     * @param south the s ou th value
     * @param west the w es t value
     * @param destination the d es ti na ti on value
     * @return the result of the operation
     */
    public List<Activity> searchInBounds(String destination, double south, double west,
                                         double north, double east, int maxResults) {
        return searchInBounds(south, west, north, east, maxResults);
    }

    /**
     * Performs the s ea rc hi nb ou nd s operation.
     * @param east the e as t value
     * @param north the n or th value
     * @param west the w es t value
     * @param south the s ou th value
     * @return the result of the operation
     */
    public List<Activity> searchInBounds(double south, double west, double north, double east,
                                         int maxResults) {
        final List<Activity> result = new ArrayList<Activity>();
        for (Activity activity : activities) {
            final Location loc = activity.getLocation();
            if (loc.getLatitude() >= south && loc.getLatitude() <= north
                    && loc.getLongitude() >= west && loc.getLongitude() <= east) {
                result.add(activity);
            }
            if (result.size() >= maxResults) {
                break;
            }
        }
        return result;
    }

    /**
     * Performs the f in da ll operation.
     * @return the result of the operation
     */
    public List<Activity> findAll() {
        return new ArrayList<Activity>(activities);
    }

    /**
     * Performs the f in db yi d operation.
     * @param id the i d value
     * @return the result of the operation
     */
    public Optional<Activity> findById(String id) {
        for (Activity activity : activities) {
            if (activity.getId().equals(id)) {
                return Optional.of(activity);
            }
        }
        return Optional.empty();
    }
}
