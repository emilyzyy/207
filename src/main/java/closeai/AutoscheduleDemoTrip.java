package closeai;

import closeai.domain.entities.Activity;
import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.EventType;
import closeai.domain.valueobjects.IndoorOutdoorType;
import closeai.domain.valueobjects.Location;
import closeai.domain.valueobjects.TransportationMode;
import closeai.domain.valueobjects.WeatherSeverity;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A deterministic day built to exercise what Autoschedule can actually do.
 *
 * <p>Separate from {@code DemoSeeding}, which is Raashid's and drives the running
 * application from discovered places. This one exists because a demonstration needs a day
 * whose outcome is known in advance: it uses fixed coordinates and a fixed hourly forecast,
 * so the schedule it produces is the same on every machine and does not depend on a public
 * API being reachable.</p>
 *
 * <p>Every activity is here for a reason, and the improvements are produced by the real
 * Interactor from this data — nothing is staged in the Presenter:</p>
 *
 * <ul>
 *   <li><b>Royal Ontario Museum, 11:00</b> — pinned. Stays exactly where it is, which is the
 *       only improvement the traveller asked for by hand.</li>
 *   <li><b>St Lawrence Market, 3:30 pm</b> — a meal at a poor hour. Moving it toward the
 *       lunch window is a meal improvement the {@code MealWindowPolicy} can prove.</li>
 *   <li><b>High Park, 7:30 pm</b> — outdoors, after dark and in the worst of the forecast.
 *       Moving it into daylight and milder weather is provable by two separate policies.</li>
 *   <li><b>Distillery District, 1:00 pm</b> — outdoors and close to the market, so a sensible
 *       order genuinely saves travel rather than merely reshuffling.</li>
 *   <li><b>Casa Loma, 5:00 pm</b> — far north, giving the day a real journey to plan around
 *       and something for the unavailable period to collide with.</li>
 * </ul>
 *
 * <p>Coordinates are the real Toronto ones, so distances between them are meaningful rather
 * than arbitrary.</p>
 */
public final class AutoscheduleDemoTrip {

    /** Fixed so the demo tells the same story every time it is run. */
    public static final LocalDate DEMO_DATE = LocalDate.of(2026, 8, 12);

    /** An afternoon the traveller is busy, for demonstrating an inviolable window. */
    public static final LocalTime UNAVAILABLE_FROM = LocalTime.of(14, 0);
    public static final LocalTime UNAVAILABLE_TO = LocalTime.of(15, 0);

    private AutoscheduleDemoTrip() {
    }

    /** The day as the traveller carelessly arranged it, before any scheduling. */
    public static Trip inefficientDay() {
        Trip trip = new Trip("demo-trip", "Toronto", DEMO_DATE,
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.WALKING);
        List<ScheduledEvent> events = new ArrayList<>();

        // Chronological because the Trip entity insists on it. The day is careless in its
        // *times* and its geography, not in the order the list happens to be built: it runs
        // north, then south-east, then back north, then south-west again.
        events.add(event("event-museum", activity("museum", "Royal Ontario Museum",
                ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR,
                43.6677, -79.3948, 10, 17), LocalTime.of(11, 0), 60));

        events.add(event("event-distillery", activity("distillery", "Distillery District",
                ActivityCategory.ATTRACTION, IndoorOutdoorType.OUTDOOR,
                43.6503, -79.3597, 8, 22), LocalTime.of(13, 0), 60));

        // A meal at half past three: outside any customary window, and the clearest
        // improvement the meal policy can demonstrate.
        events.add(event("event-lunch", activity("lunch", "St Lawrence Market",
                ActivityCategory.FOOD, IndoorOutdoorType.INDOOR,
                43.6487, -79.3716, 9, 19), LocalTime.of(15, 30), 60));

        events.add(event("event-casaloma", activity("casaloma", "Casa Loma",
                ActivityCategory.ATTRACTION, IndoorOutdoorType.INDOOR,
                43.6780, -79.4094, 9, 20), LocalTime.of(17, 0), 60));

        // Outdoors at half past seven: after dark and in the worst of the forecast below.
        events.add(event("event-park", activity("park", "High Park",
                ActivityCategory.PARKS_NATURE, IndoorOutdoorType.OUTDOOR,
                43.6465, -79.4637, 6, 22), LocalTime.of(19, 30), 60));

        trip.replaceSchedule(events);
        return trip;
    }

    /**
     * An hourly forecast with a genuinely bad evening.
     *
     * <p>Deliberately shaped so that moving an outdoor activity earlier is a real
     * improvement rather than a coincidence: mild through the middle of the day, worsening
     * from 6 p.m. and severe after 7 p.m.</p>
     */
    public static List<WeatherWarning> hourlyForecast() {
        Location toronto = new Location(43.6532, -79.3832, "Toronto");
        List<WeatherWarning> hourly = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            WeatherSeverity severity;
            String condition;
            if (hour >= 19) {
                severity = WeatherSeverity.HIGH;
                condition = "Heavy rain";
            } else if (hour >= 18) {
                severity = WeatherSeverity.MEDIUM;
                condition = "Showers";
            } else {
                severity = WeatherSeverity.LOW;
                condition = "Sunny intervals";
            }
            hourly.add(new WeatherWarning(toronto, LocalTime.of(hour, 0), condition, severity,
                    describe(severity)));
        }
        return Collections.unmodifiableList(hourly);
    }

    private static String describe(WeatherSeverity severity) {
        if (severity == WeatherSeverity.HIGH) {
            return "22°C · 85% precipitation · heavy conditions.";
        }
        if (severity == WeatherSeverity.MEDIUM) {
            return "23°C · 55% precipitation · moderate conditions.";
        }
        return "24°C · 10% precipitation · low conditions.";
    }

    private static Activity activity(String id, String name, ActivityCategory category,
                                     IndoorOutdoorType exposure, double latitude,
                                     double longitude, int opensHour, int closesHour) {
        return new Activity(id, name, category, new Location(latitude, longitude, name), 4.5, 60,
                LocalTime.of(opensHour, 0), LocalTime.of(closesHour, 0), exposure, "none");
    }

    private static ScheduledEvent event(String id, Activity activity, LocalTime start,
                                        int durationMinutes) {
        return new ScheduledEvent(id, activity, start, start.plusMinutes(durationMinutes),
                EventType.ACTIVITY, "");
    }
}
