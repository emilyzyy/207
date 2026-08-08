package closeai.domain.entities;

import closeai.domain.valueobjects.EventType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One calendar day of a trip: its date, its window, and the events scheduled for it.
 *
 * <p>A {@link Trip} holds one of these per day. Each day validates its own schedule the
 * way the trip used to, so a multi-day trip never has to know which day an event belongs
 * to — the day's own window is the boundary.</p>
 */
public final class TripDay {
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private final List<ScheduledEvent> scheduledEvents = new ArrayList<>();

    public TripDay(LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (date == null) {
            throw new IllegalArgumentException("Day date is required");
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Day end must follow start");
        }
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public List<ScheduledEvent> getScheduledEvents() {
        return Collections.unmodifiableList(scheduledEvents);
    }

    /** Moves the day's date and/or window, keeping every existing event inside it. */
    public void updateWindow(LocalDate newDate, LocalTime newStart, LocalTime newEnd) {
        if (newStart == null || newEnd == null || !newEnd.isAfter(newStart)) {
            throw new IllegalArgumentException("Day end must follow start");
        }
        for (ScheduledEvent event : scheduledEvents) {
            if (event.getStartTime().isBefore(newStart) || event.getEndTime().isAfter(newEnd)) {
                throw new IllegalArgumentException(
                        "Cannot change day window: scheduled events would fall outside");
            }
        }
        this.date = newDate;
        this.startTime = newStart;
        this.endTime = newEnd;
    }

    public void addEvent(ScheduledEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event is required");
        }
        scheduledEvents.add(event);
    }

    public void replaceSchedule(List<ScheduledEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("Schedule is required");
        }
        ScheduledEvent previous = null;
        for (ScheduledEvent event : events) {
            if (event == null) {
                throw new IllegalArgumentException("Schedule cannot contain null events");
            }
            if (event.getStartTime().isBefore(startTime) || event.getEndTime().isAfter(endTime)) {
                throw new IllegalArgumentException("Scheduled events must stay inside the day window");
            }
            if (previous != null && event.getStartTime().isBefore(previous.getEndTime())) {
                throw new IllegalArgumentException("Scheduled events must be sorted and cannot overlap");
            }
            if (event.getEventType() == EventType.ACTIVITY) {
                Activity activity = event.getActivity();
                if (activity == null) {
                    throw new IllegalArgumentException("Activity event requires an activity");
                }
                if (event.getStartTime().isBefore(activity.getOpeningTime())
                        || event.getEndTime().isAfter(activity.getClosingTime())) {
                    throw new IllegalArgumentException("Activity must stay inside its opening hours");
                }
            }
            previous = event;
        }
        scheduledEvents.clear();
        scheduledEvents.addAll(events);
    }

    public void removeEvent(String eventId) {
        scheduledEvents.removeIf(event -> event.getId().equals(eventId));
    }

    public ScheduledEvent findEvent(String eventId) {
        for (ScheduledEvent event : scheduledEvents) {
            if (event.getId().equals(eventId)) {
                return event;
            }
        }
        return null;
    }
}
