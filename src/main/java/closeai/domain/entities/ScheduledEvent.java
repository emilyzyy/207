package closeai.domain.entities;

import closeai.domain.valueobjects.EventType;
import java.time.LocalTime;

public final class ScheduledEvent {
    private final String id;
    private final Activity activity;
    private LocalTime startTime;
    private LocalTime endTime;
    private final EventType eventType;
    private String notes;

    public ScheduledEvent(String id, Activity activity, LocalTime startTime, LocalTime endTime,
                          EventType eventType, String notes) {
        this.id = id;
        this.activity = activity;
        this.startTime = startTime;
        this.endTime = endTime;
        this.eventType = eventType;
        this.notes = notes;
    }

    public String getId() { return id; }
    public Activity getActivity() { return activity; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public EventType getEventType() { return eventType; }
    public String getNotes() { return notes; }
    public void reschedule(LocalTime start, LocalTime end, String updatedNotes) {
        if (!end.isAfter(start)) throw new IllegalArgumentException("End time must follow start time");
        this.startTime = start;
        this.endTime = end;
        this.notes = updatedNotes == null ? "" : updatedNotes;
    }
}
