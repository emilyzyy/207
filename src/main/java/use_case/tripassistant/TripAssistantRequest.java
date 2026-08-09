package use_case.tripassistant;

import entity.valueobjects.TripAssistantMessage;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.WeatherWarning;
import entity.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Complete current-trip evidence passed to a chatbot gateway on every turn. */
public final class TripAssistantRequest {
    private final String destination;
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final TransportationMode transportationMode;
    private final List<Activity> activities;
    private final Set<String> bookmarkedActivityIds;
    private final List<ScheduledEvent> scheduledEvents;
    private final List<WeatherWarning> weather;
    private final List<TripAssistantMessage> history;
    private final String question;

    public TripAssistantRequest(
            String destination, LocalDate date, LocalTime startTime, LocalTime endTime,
            TransportationMode transportationMode, List<Activity> activities,
            Set<String> bookmarkedActivityIds, List<ScheduledEvent> scheduledEvents,
            List<WeatherWarning> weather, List<TripAssistantMessage> history,
            String question) {
        this.destination = destination;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.transportationMode = transportationMode;
        this.activities = immutableList(activities);
        this.bookmarkedActivityIds = Collections.unmodifiableSet(new LinkedHashSet<String>(
                bookmarkedActivityIds == null
                        ? Collections.<String>emptySet() : bookmarkedActivityIds));
        this.scheduledEvents = immutableList(scheduledEvents);
        this.weather = immutableList(weather);
        this.history = immutableList(history);
        this.question = question == null ? "" : question.trim();
    }

    private static <T> List<T> immutableList(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(
                values == null ? Collections.<T>emptyList() : values));
    }

    public String getDestination() { return destination; }

    public LocalDate getDate() { return date; }

    public LocalTime getStartTime() { return startTime; }

    public LocalTime getEndTime() { return endTime; }

    public TransportationMode getTransportationMode() { return transportationMode; }

    public List<Activity> getActivities() { return activities; }

    public Set<String> getBookmarkedActivityIds() { return bookmarkedActivityIds; }

    public List<ScheduledEvent> getScheduledEvents() { return scheduledEvents; }

    public List<WeatherWarning> getWeather() { return weather; }

    public List<TripAssistantMessage> getHistory() { return history; }

    public String getQuestion() { return question; }
}
