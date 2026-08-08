package trippy.application.autoschedule;

import trippy.domain.valueobjects.WeatherSeverity;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * What the scheduler knows about the weather for the trip day.
 *
 * <p>Two shapes are supported deliberately. Today's weather adapter reports one warning
 * for the whole trip, which mostly lets the schedule warn rather than genuinely move
 * things. The hourly shape is already accepted here so that when an hourly forecast
 * becomes available the policy can start relocating outdoor activities without any
 * change to the engine or the Interactor.</p>
 *
 * <p>Absent weather is a first-class case: the schedule still gets built, and the
 * Preview says weather was not considered. A forecast must never be able to make a
 * day unschedulable.</p>
 */
public final class WeatherContext {

    private static final WeatherContext UNAVAILABLE = new WeatherContext(false, null, null);

    private final boolean available;
    private final WeatherSeverity tripSeverity;
    private final Map<Integer, WeatherSeverity> severityByHour;

    private WeatherContext(boolean available, WeatherSeverity tripSeverity,
                           Map<Integer, WeatherSeverity> severityByHour) {
        this.available = available;
        this.tripSeverity = tripSeverity;
        this.severityByHour = severityByHour == null
                ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(severityByHour));
    }

    /** No forecast could be obtained; scheduling proceeds without weather. */
    public static WeatherContext unavailable() {
        return UNAVAILABLE;
    }

    /** One severity for the whole day, matching today's weather adapter. */
    public static WeatherContext tripLevel(WeatherSeverity severity) {
        if (severity == null) {
            return UNAVAILABLE;
        }
        return new WeatherContext(true, severity, null);
    }

    /** Severity per hour of the day, for when an hourly forecast is available. */
    public static WeatherContext hourly(Map<Integer, WeatherSeverity> byHour) {
        if (byHour == null || byHour.isEmpty()) {
            return UNAVAILABLE;
        }
        return new WeatherContext(true, null, byHour);
    }

    public boolean isAvailable() {
        return available;
    }

    /** Severity at a given time, or null when nothing is known. */
    public WeatherSeverity severityAt(LocalTime time) {
        if (!available) {
            return null;
        }
        if (!severityByHour.isEmpty()) {
            return severityByHour.get(time.getHour());
        }
        return tripSeverity;
    }

    /** True when severity varies across the day rather than being one trip-wide value. */
    public boolean isHourly() {
        return !severityByHour.isEmpty();
    }

    /**
     * Whether this forecast can actually tell one time of day from another.
     *
     * <p>A single severity for the whole trip cannot: every candidate slot scores the
     * same, so weather has nothing to say about <em>when</em> to do things. Scheduling
     * treats that as weather being unable to contribute rather than pretending to have
     * optimised around it, and the Preview says so.</p>
     */
    public boolean canDistinguishTimes() {
        return available && isHourly();
    }
}
