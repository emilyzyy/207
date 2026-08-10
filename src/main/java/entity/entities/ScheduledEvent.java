package entity.entities;

import java.time.LocalTime;

import entity.valueobjects.EventType;

public final class ScheduledEvent {
    private final String id;
    private final Activity activity;
    private LocalTime startTime;
    private LocalTime endTime;
    private final EventType eventType;
    private String notes;

    public ScheduledEvent(String id, Activity activity, LocalTime startTime, LocalTime endTime,
                          EventType eventType, String notes) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Event id is required");
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must follow start time");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("Event type is required");
        }
        this.id = id;
        this.activity = activity;
        this.startTime = startTime;
        this.endTime = endTime;
        this.eventType = eventType;
        this.notes = notes == null ? "" : notes;
    }

    public String getId() {
        return id;
    }

    public Activity getActivity() {
        return activity;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getNotes() {
        return notes;
    }

    /**
     * Performs the r es ch ed ul e operation.
     * @param updatedNotes the u pd at ed no te s value
     * @param end the e nd value
     * @param start the s ta rt value
     */
    public void reschedule(LocalTime start, LocalTime end, String updatedNotes) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must follow start time");
        }
        this.startTime = start;
        this.endTime = end;
        this.notes = updatedNotes == null ? "" : updatedNotes;
    }
}
