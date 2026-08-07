package closeai.domain.valueobjects;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * When a venue is actually open, normalised away from whatever the provider said.
 *
 * <p>Three states, and the difference between the last two matters more than it looks:</p>
 *
 * <ul>
 *   <li><b>Known</b> — intervals per weekday. A day with no intervals is closed.</li>
 *   <li><b>Unknown</b> — the provider said nothing, or said something unparseable.</li>
 *   <li><b>Always open</b> — the {@code 24/7} case, a venue with no closing time.</li>
 * </ul>
 *
 * <p><b>Unknown is not closed.</b> Most of the world's places have no opening hours recorded
 * in OpenStreetMap, and treating silence as "shut" would make the scheduler refuse to plan
 * most real days. Unknown therefore means "no constraint from this source", and the caller
 * says so out loud rather than quietly guessing.</p>
 *
 * <p>Overnight spans such as {@code Fr 20:00-02:00} are split at midnight when normalised,
 * so every interval returned here is within a single day and start is always before end.
 * The engine schedules one day at a time, so the portion that lands after midnight belongs
 * to the following day and is recorded there.</p>
 */
public final class OpeningHours {

    private static final OpeningHours UNKNOWN = new OpeningHours(null, false);
    private static final OpeningHours ALWAYS = new OpeningHours(null, true);

    private final Map<DayOfWeek, List<TimeInterval>> byDay;
    private final boolean alwaysOpen;

    private OpeningHours(Map<DayOfWeek, List<TimeInterval>> byDay, boolean alwaysOpen) {
        this.byDay = byDay;
        this.alwaysOpen = alwaysOpen;
    }

    /** Nothing is known about this venue's hours; scheduling stays permissive. */
    public static OpeningHours unknown() {
        return UNKNOWN;
    }

    /** The venue never closes. */
    public static OpeningHours alwaysOpen() {
        return ALWAYS;
    }

    /**
     * Known hours. A weekday absent from the map, or present with no intervals, is closed.
     * Intervals are sorted and must not cross midnight — split them first.
     */
    public static OpeningHours of(Map<DayOfWeek, List<TimeInterval>> byDay) {
        if (byDay == null) {
            return UNKNOWN;
        }
        Map<DayOfWeek, List<TimeInterval>> copy = new EnumMap<>(DayOfWeek.class);
        for (Map.Entry<DayOfWeek, List<TimeInterval>> entry : byDay.entrySet()) {
            List<TimeInterval> intervals = new ArrayList<>(entry.getValue());
            Collections.sort(intervals,
                    (left, right) -> left.getStart().compareTo(right.getStart()));
            copy.put(entry.getKey(), Collections.unmodifiableList(intervals));
        }
        return new OpeningHours(Collections.unmodifiableMap(copy), false);
    }

    public boolean isKnown() {
        return alwaysOpen || byDay != null;
    }

    /** True when the venue is known to be shut for the whole of this date. */
    public boolean isClosedOn(LocalDate date) {
        return isKnown() && !alwaysOpen && intervalsOn(date).isEmpty();
    }

    /**
     * The intervals this venue is open on the given date, earliest first.
     *
     * <p>Empty when unknown as well as when closed, so callers must ask {@link #isKnown()}
     * before reading an empty list as a refusal.</p>
     */
    public List<TimeInterval> intervalsOn(LocalDate date) {
        if (alwaysOpen) {
            return Collections.singletonList(
                    new TimeInterval(LocalTime.MIN, LocalTime.of(23, 59)));
        }
        if (byDay == null || date == null) {
            return Collections.emptyList();
        }
        List<TimeInterval> intervals = byDay.get(date.getDayOfWeek());
        return intervals == null ? Collections.<TimeInterval>emptyList() : intervals;
    }

    /** Half-open span within a single day; {@code start} is always before {@code end}. */
    public static final class TimeInterval {
        private final LocalTime start;
        private final LocalTime end;

        public TimeInterval(LocalTime start, LocalTime end) {
            if (start == null || end == null || !end.isAfter(start)) {
                throw new IllegalArgumentException(
                        "An opening interval must end after it starts: " + start + " to " + end);
            }
            this.start = start;
            this.end = end;
        }

        public LocalTime getStart() {
            return start;
        }

        public LocalTime getEnd() {
            return end;
        }

        @Override
        public String toString() {
            return start + "-" + end;
        }
    }
}
