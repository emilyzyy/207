package closeai.domain.entities;

import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import java.time.LocalTime;

public final class Activity {
    private final String id;
    private final String name;
    private final ActivityCategory category;
    private final Location location;
    private final double rating;
    private final int estimatedDurationMinutes;
    private final LocalTime openingTime;
    private final LocalTime closingTime;
    private final IndoorOutdoorType indoorOutdoorType;
    private final String weatherRisk;
    private final String openingHoursText;

    public Activity(String id, String name, ActivityCategory category, Location location, double rating,
                    int estimatedDurationMinutes, LocalTime openingTime, LocalTime closingTime,
                    IndoorOutdoorType indoorOutdoorType, String weatherRisk) {
        this(id, name, category, location, rating, estimatedDurationMinutes, openingTime, closingTime,
                indoorOutdoorType, weatherRisk, null);
    }

    public Activity(String id, String name, ActivityCategory category, Location location, double rating,
                    int estimatedDurationMinutes, LocalTime openingTime, LocalTime closingTime,
                    IndoorOutdoorType indoorOutdoorType, String weatherRisk, String openingHoursText) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.location = location;
        this.rating = rating;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.indoorOutdoorType = indoorOutdoorType;
        this.weatherRisk = weatherRisk;
        this.openingHoursText = openingHoursText;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public ActivityCategory getCategory() { return category; }
    public Location getLocation() { return location; }
    public double getRating() { return rating; }
    public int getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public LocalTime getOpeningTime() { return openingTime; }
    public LocalTime getClosingTime() { return closingTime; }
    public IndoorOutdoorType getIndoorOutdoorType() { return indoorOutdoorType; }
    public String getWeatherRisk() { return weatherRisk; }
    public String getOpeningHoursText() { return openingHoursText; }
}
