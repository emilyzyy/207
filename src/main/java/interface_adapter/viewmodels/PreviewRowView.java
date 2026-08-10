package interface_adapter.viewmodels;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One line of the proposed schedule, already worded for the screen.
 *
 * <p>The Presenter has turned reason codes into sentences by this point, so the view
 * only lays things out. Each row keeps one short reason for the main list and the full
 * set for the expandable explanation, which is how the Preview stays readable without
 * hiding its reasoning.</p>
 */
public final class PreviewRowView {

    /** Whether this line is something the traveller does, or the journey in between. */
    public enum Kind { ACTIVITY, TRAVEL }

    private final String eventId;
    private final String title;
    private final Kind kind;
    private final LocalTime start;
    private final LocalTime end;
    private final boolean locked;
    private final boolean moved;
    private final String reason;
    private final List<String> allReasons;

    public PreviewRowView(String eventId, String title, Kind kind, LocalTime start, LocalTime end,
                          boolean locked, boolean moved, String reason, List<String> allReasons) {
        this.eventId = eventId == null ? "" : eventId;
        this.title = title == null ? "" : title;
        this.kind = kind;
        this.start = start;
        this.end = end;
        this.locked = locked;
        this.moved = moved;
        this.reason = reason == null ? "" : reason;
        this.allReasons = Collections.unmodifiableList(new ArrayList<>(
                allReasons == null ? Collections.<String>emptyList() : allReasons));
    }

    public String getEventId() {
        return eventId;
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

    /** True when this activity ends up at a different time than it had before. */
    public boolean isMoved() {
        return moved;
    }

    /** The single short explanation shown beside the row, or empty for none. */
    public String getReason() {
        return reason;
    }

    /** Every explanation for this row, shown under "Why these times?". */
    public List<String> getAllReasons() {
        return allReasons;
    }

    /** The row's span as the traveller reads a clock, e.g. {@code "09:00 – 10:00"}. */
    public String getTimeLabel() {
        return TimeDisplay.range(start, end);
    }
}
