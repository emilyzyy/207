package closeai.domain.entities;

import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.OpeningHours;
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

    /**
     * Real per-weekday hours when a provider supplied them, otherwise unknown.
     *
     * <p>Kept alongside {@link #openingTime}/{@link #closingTime} rather than replacing them:
     * those two are a single window with no notion of which day it is, and most of the code
     * base — and every activity built by hand or by a test — has only ever had that. When
     * hours are unknown the scheduler falls back to the single window exactly as before, so
     * adding this field changes no existing behaviour.</p>
     */
    private final OpeningHours openingHours;

    /** An activity whose real hours are not known; the single window is all there is. */
    public Activity(String id, String name, ActivityCategory category, Location location, double rating,
                    int estimatedDurationMinutes, LocalTime openingTime, LocalTime closingTime,
                    IndoorOutdoorType indoorOutdoorType, String weatherRisk) {
        this(id, name, category, location, rating, estimatedDurationMinutes, openingTime,
                closingTime, indoorOutdoorType, weatherRisk, OpeningHours.unknown());
    }

    public Activity(String id, String name, ActivityCategory category, Location location, double rating,
                    int estimatedDurationMinutes, LocalTime openingTime, LocalTime closingTime,
                    IndoorOutdoorType indoorOutdoorType, String weatherRisk,
                    OpeningHours openingHours) {
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
        this.openingHours = openingHours == null ? OpeningHours.unknown() : openingHours;
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

    /** Never null; ask {@link OpeningHours#isKnown()} before reading anything into it. */
    public OpeningHours getOpeningHours() { return openingHours; }
}
