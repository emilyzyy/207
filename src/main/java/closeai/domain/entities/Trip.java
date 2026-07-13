package closeai.domain.entities;

import closeai.domain.valueobjects.TransportationMode;
import closeai.domain.valueobjects.EventType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Trip {
    private final String id;
    private String destination;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private TransportationMode transportationMode;
    private final List<Activity> bookmarkedActivities = new ArrayList<Activity>();
    private final List<ScheduledEvent> scheduledEvents = new ArrayList<ScheduledEvent>();

    public Trip(String id, String destination, LocalDate date, LocalTime startTime,
                LocalTime endTime, TransportationMode transportationMode) {
        if (!endTime.isAfter(startTime)) throw new IllegalArgumentException("Trip end must follow start");
        this.id = id;
        this.destination = destination;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.transportationMode = transportationMode;
    }

    public String getId() { return id; }
    public String getDestination() { return destination; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public TransportationMode getTransportationMode() { return transportationMode; }
    public List<Activity> getBookmarkedActivities() { return Collections.unmodifiableList(bookmarkedActivities); }
    public List<ScheduledEvent> getScheduledEvents() { return Collections.unmodifiableList(scheduledEvents); }

    public void updateOptions(String destination, LocalDate date, LocalTime start, LocalTime end,
                              TransportationMode mode) {
        if (!end.isAfter(start)) throw new IllegalArgumentException("Trip end must follow start");
        this.destination = destination;
        this.date = date;
        this.startTime = start;
        this.endTime = end;
        this.transportationMode = mode;
    }

    public void bookmark(Activity activity) {
        for (Activity item : bookmarkedActivities) if (item.getId().equals(activity.getId())) return;
        bookmarkedActivities.add(activity);
    }

    public void removeBookmark(String activityId) {
        bookmarkedActivities.removeIf(activity -> activity.getId().equals(activityId));
    }

    public void addEvent(ScheduledEvent event) { scheduledEvents.add(event); }
    public void replaceSchedule(List<ScheduledEvent> events) {
        if (events == null) throw new IllegalArgumentException("Schedule is required");
        ScheduledEvent previous = null;
        for (ScheduledEvent event : events) {
            if (event == null) throw new IllegalArgumentException("Schedule cannot contain null events");
            if (event.getStartTime().isBefore(startTime) || event.getEndTime().isAfter(endTime))
                throw new IllegalArgumentException("Scheduled events must stay inside the trip window");
            if (previous != null && event.getStartTime().isBefore(previous.getEndTime()))
                throw new IllegalArgumentException("Scheduled events must be sorted and cannot overlap");
            if (event.getEventType() == EventType.ACTIVITY) {
                Activity activity = event.getActivity();
                if (activity == null) throw new IllegalArgumentException("Activity event requires an activity");
                if (event.getStartTime().isBefore(activity.getOpeningTime())
                        || event.getEndTime().isAfter(activity.getClosingTime()))
                    throw new IllegalArgumentException("Activity must stay inside its opening hours");
            }
            previous = event;
        }
        scheduledEvents.clear();
        scheduledEvents.addAll(events);
    }

    /** Returns a separate aggregate so scheduling failures never partially mutate this trip. */
    public Trip copyWithSchedule(List<ScheduledEvent> events) {
        Trip copy = new Trip(id, destination, date, startTime, endTime, transportationMode);
        for (Activity activity : bookmarkedActivities) copy.bookmark(activity);
        copy.replaceSchedule(events);
        return copy;
    }
    public void removeEvent(String eventId) { scheduledEvents.removeIf(event -> event.getId().equals(eventId)); }
    public ScheduledEvent findEvent(String eventId) {
        for (ScheduledEvent event : scheduledEvents) if (event.getId().equals(eventId)) return event;
        return null;
    }
}
