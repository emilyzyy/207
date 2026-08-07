package closeai.application.autoschedule;

import java.time.LocalTime;

/** One row of a proposed schedule, ready to display and free of entities. */
public final class ProposedEventData {

    /** Whether this row is something the user does, or the journey in between. */
    public enum Kind { ACTIVITY, TRAVEL }

    private final String eventId;
    private final String activityId;
    private final String title;
    private final Kind kind;
    private final LocalTime start;
    private final LocalTime end;
    private final boolean locked;
    private final boolean moved;

    public ProposedEventData(String eventId, String activityId, String title, Kind kind,
                             LocalTime start, LocalTime end, boolean locked, boolean moved) {
        this.eventId = eventId;
        this.activityId = activityId == null ? "" : activityId;
        this.title = title == null ? "" : title;
        this.kind = kind;
        this.start = start;
        this.end = end;
        this.locked = locked;
        this.moved = moved;
    }

    public String getEventId() {
        return eventId;
    }

    /** Empty for travel rows, which have no activity behind them. */
    public String getActivityId() {
        return activityId;
    }

    public String getTitle() {
        return title;
    }

    public Kind getKind() {
        return kind;
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    public boolean isLocked() {
        return locked;
    }

    /** True when this activity ended up at a different time than before. */
    public boolean isMoved() {
        return moved;
    }
}
