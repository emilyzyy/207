package use_case.autoschedule;

import java.time.LocalTime;
import java.util.Objects;

/**
 * A half-open local-time interval {@code [start, end)} used for availability,
 * unavailable periods, opening hours and locked placements.
 *
 * <p>Half-open semantics keep adjacency unambiguous: an event ending at 12:00
 * does not overlap a window starting at 12:00.</p>
 */
public final class TimeWindow {
    private final LocalTime start;
    private final LocalTime end;

    public TimeWindow(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Time window bounds are required");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Time window end must follow start");
        }
        this.start = start;
        this.end = end;
    }

    public static TimeWindow of(LocalTime start, LocalTime end) {
        return new TimeWindow(start, end);
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    public int durationMinutes() {
        return (end.toSecondOfDay() - start.toSecondOfDay()) / 60;
    }

    /** True when {@code time} lies in {@code [start, end)}. */
    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && time.isBefore(end);
    }

    /** True when this window and {@code other} share at least one minute. */
    public boolean overlaps(TimeWindow other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    /** True when {@code [start, end)} of {@code other} lies entirely inside this window. */
    public boolean encloses(TimeWindow other) {
        return !other.start.isBefore(start) && !other.end.isAfter(end);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TimeWindow)) {
            return false;
        }
        TimeWindow that = (TimeWindow) other;
        return start.equals(that.start) && end.equals(that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return start + "-" + end;
    }
}
