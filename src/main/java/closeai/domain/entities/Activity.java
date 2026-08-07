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
    private final String openingHoursText;

    /**
     * Real per-weekday hours when a provider supplied them, otherwise unknown.
     *
     * <p>Three descriptions of the same thing now sit on this entity, and they are not
     * redundant:</p>
     *
     * <ul>
     *   <li>{@link #openingHoursText} — what the provider literally said, for display.</li>
     *   <li>{@link #openingTime}/{@link #closingTime} — one coarse window spanning the whole
     *       week, which every hand-built activity and every older caller has always had.</li>
     *   <li>This — the same information resolved per weekday, which is what the scheduler
     *       needs to know that a venue shut for lunch offers two shifts rather than one long
     *       day, and that Sunday is not Monday.</li>
     * </ul>
     *
     * <p>When this is unknown the scheduler falls back to the coarse window exactly as
     * before, so adding it changed no existing behaviour.</p>
     */
    private final OpeningHours openingHours;

    /** An activity whose real hours are not known; the single window is all there is. */
    public Activity(String id, String name, ActivityCategory category, Location location, double rating,
                    int estimatedDurationMinutes, LocalTime openingTime, LocalTime closingTime,
                    IndoorOutdoorType indoorOutdoorType, String weatherRisk) {
        this(id, name, category, location, rating, estimatedDurationMinutes, openingTime,
                closingTime, indoorOutdoorType, weatherRisk, null, OpeningHours.unknown());
    }

    /**
     * An activity carrying the provider's raw hours text but no normalised reading of it.
     *
     * <p>Parsing that text is an infrastructure concern — the syntax belongs to one provider
     * — so an entity cannot do it, and this constructor deliberately does not pretend to.
     * Adapters that want the scheduler to honour real hours must parse the text and use the
     * constructor below.</p>
     */
    public Activity(String id, String name, ActivityCategory category, Location location, double rating,
                    int estimatedDurationMinutes, LocalTime openingTime, LocalTime closingTime,
                    IndoorOutdoorType indoorOutdoorType, String weatherRisk,
                    String openingHoursText) {
        this(id, name, category, location, rating, estimatedDurationMinutes, openingTime,
                closingTime, indoorOutdoorType, weatherRisk, openingHoursText,
                OpeningHours.unknown());
    }

    public Activity(String id, String name, ActivityCategory category, Location location, double rating,
                    int estimatedDurationMinutes, LocalTime openingTime, LocalTime closingTime,
                    IndoorOutdoorType indoorOutdoorType, String weatherRisk,
                    String openingHoursText, OpeningHours openingHours) {
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

    /** Exactly what the provider said, unmodified; null when it said nothing. */
    public String getOpeningHoursText() { return openingHoursText; }

    /** Never null; ask {@link OpeningHours#isKnown()} before reading anything into it. */
    public OpeningHours getOpeningHours() { return openingHours; }
}
