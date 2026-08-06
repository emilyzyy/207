package closeai.adapters.viewmodels;

import closeai.domain.entities.ScheduledEvent;
import closeai.domain.entities.WeatherWarning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable display state shared by the Day Plan and Calendar views. */
public final class DayPlanState {
    private final String tripId;
    private final List<ScheduledEvent> events;
    private final String message;
    private final boolean error;
    private final List<WeatherWarning> hourlyWeather;

    public DayPlanState(
            String tripId, List<ScheduledEvent> events, String message, boolean error) {
        this(tripId, events, message, error, Collections.emptyList());
    }

    public DayPlanState(
            String tripId, List<ScheduledEvent> events, String message, boolean error,
            List<WeatherWarning> hourlyWeather) {
        this.tripId = tripId == null ? "" : tripId.trim();
        this.events = Collections.unmodifiableList(new ArrayList<ScheduledEvent>(
                events == null ? Collections.emptyList() : events));
        this.message = message == null ? "" : message;
        this.error = error;
        this.hourlyWeather = Collections.unmodifiableList(new ArrayList<WeatherWarning>(
                hourlyWeather == null ? Collections.emptyList() : hourlyWeather));
    }

    public String getTripId() {
        return tripId;
    }

    public List<ScheduledEvent> getEvents() {
        return events;
    }

    public String getMessage() {
        return message;
    }

    public boolean isError() {
        return error;
    }

    public List<WeatherWarning> getHourlyWeather() {
        return hourlyWeather;
    }

    /** Selects every forecast hour that overlaps the event's half-open time interval. */
    public List<WeatherWarning> getHourlyWeatherFor(ScheduledEvent event) {
        if (event == null) return Collections.emptyList();
        List<WeatherWarning> result = new ArrayList<WeatherWarning>();
        for (WeatherWarning warning : hourlyWeather) {
            if (warning == null || warning.getTime() == null) continue;
            java.time.LocalTime nextHour = warning.getTime().plusHours(1);
            boolean reachesAfterStart = nextHour.isAfter(event.getStartTime())
                    || nextHour.isBefore(warning.getTime());
            if (warning.getTime().isBefore(event.getEndTime()) && reachesAfterStart) {
                result.add(warning);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
