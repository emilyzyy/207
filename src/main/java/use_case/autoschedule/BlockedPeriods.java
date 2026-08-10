package use_case.autoschedule;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The periods during a run in which nothing may be scheduled: the user's unavailable
 * windows plus any locked activities that have not happened yet.
 *
 * <p>Unavailable windows are inviolable. The user has declared they are unavailable for
 * any itinerary event, so neither an activity nor a generated travel block may overlap
 * one; the scheduler must wait until the window ends before setting out.</p>
 */
public final class BlockedPeriods {

    private static final BlockedPeriods NONE = new BlockedPeriods(Collections.emptyList());

    private final List<TimeWindow> windows;

    private BlockedPeriods(List<TimeWindow> windows) {
        final List<TimeWindow> sorted = new ArrayList<>(windows);
        Collections.sort(sorted, (left, right) -> left.getStart().compareTo(right.getStart()));
        this.windows = Collections.unmodifiableList(sorted);
    }

    /**
     * Performs the n on e operation.
     * @return the result of the operation
     */
    public static BlockedPeriods none() {
        return NONE;
    }

    /**
     * Performs the o f operation.
     * @param windows the w in do ws value
     * @return the result of the operation
     */
    public static BlockedPeriods of(List<TimeWindow> windows) {
        return windows == null || windows.isEmpty() ? NONE : new BlockedPeriods(windows);
    }
    /**
     * This set plus one more window, used to add locks that are still ahead.
     * @param extra the e xt ra value
     * @return the result of the operation
     */

    public BlockedPeriods plus(TimeWindow extra) {
        if (extra == null) {
            return this;
        }
        final List<TimeWindow> combined = new ArrayList<>(windows);
        combined.add(extra);
        return new BlockedPeriods(combined);
    }

    public List<TimeWindow> getWindows() {
        return windows;
    }

    public boolean isEmpty() {
        return windows.isEmpty();
    }
    /**
     * True when any blocked period shares a minute with {@code candidate}.
     * @param candidate the c an di da te value
     * @return the result of the operation
     */

    public boolean blocks(TimeWindow candidate) {
        for (TimeWindow window : windows) {
            if (window.overlaps(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Performs the b lo ck s operation.
     * @param end the e nd value
     * @param start the s ta rt value
     * @return the result of the operation
     */
    public boolean blocks(LocalTime start, LocalTime end) {
        return end.isAfter(start) && blocks(new TimeWindow(start, end));
    }
    /**
     * Times worth considering as a departure, in increasing order: the moment the
     * traveller becomes free, plus the end of each blocked period after it. Setting out
     * part-way through a blocked period is never useful, so those are the only choices.
      * @param cursor the c ur so r value
      * @return the result of the operation
     */

    public List<LocalTime> departureOptionsFrom(LocalTime cursor) {
        final List<LocalTime> options = new ArrayList<>();
        options.add(cursor);
        for (TimeWindow window : windows) {
            if (window.getEnd().isAfter(cursor) && !options.contains(window.getEnd())) {
                options.add(window.getEnd());
            }
        }
        Collections.sort(options);
        return options;
    }

    /**
     * Total minutes of {@code [from, to)} that fall inside a blocked period.
     * @param to the t o value
     * @param from the f ro m value
     * @return the result of the operation
     */
    public int minutesWithin(LocalTime from, LocalTime to) {
        if (!to.isAfter(from)) {
            return 0;
        }
        int total = 0;
        LocalTime coveredUpTo = from;
        for (TimeWindow window : windows) {
            final LocalTime overlapStart = window.getStart().isAfter(coveredUpTo)
                    ? window.getStart() : coveredUpTo;
            final LocalTime overlapEnd = window.getEnd().isBefore(to) ? window.getEnd() : to;
            if (overlapEnd.isAfter(overlapStart)) {
                total += minutes(overlapStart, overlapEnd);
                coveredUpTo = overlapEnd;
            }
        }
        return total;
    }

    private static int minutes(LocalTime from, LocalTime to) {
        return (to.toSecondOfDay() - from.toSecondOfDay()) / 60;
    }
}
