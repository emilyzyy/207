package use_case.autoschedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import entity.entities.Activity;
import entity.valueobjects.OpeningHours;

/**
 * One Day Plan activity as the scheduler sees it: an identity, a duration that must
 * be preserved, the venue's opening window, and whether the user pinned it.
 *
 * <p>The duration comes from the existing event (end minus start), never from the
 * activity's estimate, so a manual duration edit survives Autoschedule.</p>
 */
public final class ScheduleTask {
    private final LocalDate tripDate;
    private final String eventId;
    private final Activity activity;
    private final int durationMinutes;
    private final int originalIndex;
    private final TimeWindow lockedAt;

    /**
     * When the venue is open on the day being scheduled, earliest first.
     *
     * <p>A list rather than one window because real places shut for lunch, and an activity
     * has to fit entirely inside one of these — not merely between the first opening and the
     * last closing, which would happily schedule a museum visit through the siesta.</p>
     *
     * <p>Empty means known to be shut all day. When hours are unknown this holds the single
     * coarse window the activity has always carried, and {@link #hoursKnown} is false so
     * callers can tell the difference between "shut" and "nobody told us".</p>
     */
    private final List<TimeWindow> openingWindows;

    private final boolean hoursKnown;

    public ScheduleTask(String eventId, Activity activity, int durationMinutes,
                        int originalIndex, TimeWindow lockedAt) {
        this(eventId, activity, durationMinutes, originalIndex, lockedAt, null);
    }

    /**
      * @param durationMinutes the d ur at io nm in ut es value
     *                 leaves the activity on its coarse single window as before
      * @param eventId the e ve nt id value
     * @param tripDate the day being scheduled, used to pick the right weekday's hours; null
      * @param activity the a ct iv it y value
     */
    public ScheduleTask(String eventId, Activity activity, int durationMinutes,
                        int originalIndex, TimeWindow lockedAt, LocalDate tripDate) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new IllegalArgumentException("Task event id is required");
        }
        if (activity == null) {
            throw new IllegalArgumentException("Task activity is required");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Task duration must be positive");
        }
        if (originalIndex < 0) {
            throw new IllegalArgumentException("Original index cannot be negative");
        }
        if (lockedAt != null && lockedAt.durationMinutes() != durationMinutes) {
            throw new IllegalArgumentException(
                    "Locked window must match the activity duration for " + eventId);
        }
        this.tripDate = tripDate;
        this.eventId = eventId;
        this.activity = activity;
        this.durationMinutes = durationMinutes;
        this.originalIndex = originalIndex;
        this.lockedAt = lockedAt;

        final OpeningHours hours = activity.getOpeningHours();
        if (tripDate == null || hours == null || !hours.isKnown()) {
            this.hoursKnown = false;
            this.openingWindows = Collections.singletonList(
                    new TimeWindow(activity.getOpeningTime(), activity.getClosingTime()));
        }
        else {
            this.hoursKnown = true;
            final List<TimeWindow> windows = new ArrayList<>();
            for (OpeningHours.TimeInterval interval : hours.intervalsOn(tripDate)) {
                windows.add(new TimeWindow(interval.getStart(), interval.getEnd()));
            }
            this.openingWindows = Collections.unmodifiableList(windows);
        }
    }

    /**
     * Performs the m ov ab le operation.
     * @param activity the a ct iv it y value
     * @param eventId the e ve nt id value
     * @return the result of the operation
     */
    public static ScheduleTask movable(String eventId, Activity activity,
                                       int durationMinutes, int originalIndex) {
        return new ScheduleTask(eventId, activity, durationMinutes, originalIndex, null);
    }

    public String getEventId() {
        return eventId;
    }

    public Activity getActivity() {
        return activity;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getOriginalIndex() {
        return originalIndex;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    public TimeWindow getLockedAt() {
        return lockedAt;
    }
    /**
     * The venue's opening windows on the day being scheduled, earliest first.
     *
     * <p>Empty only when {@link #hasKnownHours()} is true and the venue is shut all day.</p>
      * @return the result of the operation
     */

    public List<TimeWindow> getOpeningWindows() {
        return openingWindows;
    }
    /**
     * The date being scheduled, or null when none was supplied.
     * @return the result of the operation
     */

    public LocalDate getTripDate() {
        return tripDate;
    }
    /**
     * False when no provider told us the hours, in which case they constrain nothing.
     * @return the result of the operation
     */

    public boolean hasKnownHours() {
        return hoursKnown;
    }
    /**
     * The venue is on record as shut for the whole of the day being scheduled.
     * @return the result of the operation
     */

    public boolean isClosedAllDay() {
        return hoursKnown && openingWindows.isEmpty();
    }
    /**
     * The first minute of the day the venue is open.
     *
     * <p>With several windows this is the start of the first one, so it remains the earliest
     * a visit could begin. Callers deciding whether a particular span is allowed must use
     * {@link #getOpeningWindows()} instead — the gap between windows is not open.</p>
      * @return the result of the operation
     */

    public LocalTime getOpeningTime() {
        return openingWindows.isEmpty()
                ? activity.getOpeningTime() : openingWindows.get(0).getStart();
    }

    /**
     * The last minute of the day the venue is open; see {@link #getOpeningTime()}.
     * @return the result of the operation
     */
    public LocalTime getClosingTime() {
        return openingWindows.isEmpty()
                ? activity.getClosingTime()
                : openingWindows.get(openingWindows.size() - 1).getEnd();
    }

    /**
     * Whether {@code [start, end]} sits entirely inside a single opening window.
     * @param end the e nd value
     * @param start the s ta rt value
     * @return the result of the operation
     */
    public boolean isOpenThroughout(LocalTime start, LocalTime end) {
        return openingWindowFor(start, end) != null;
    }
    /**
     * The single opening window that contains {@code [start, end]}, or null if none does.
     *
     * <p>Explaining a placement means naming the window it is actually in — telling a user
     * a cafe "closes at 22:00" when the visit sits in its 09:00-12:00 morning shift would be
     * true of the venue and useless about the visit.</p>
      * @param start the s ta rt value
      * @param end the e nd value
      * @return the result of the operation
     */

    public TimeWindow openingWindowFor(LocalTime start, LocalTime end) {
        for (TimeWindow window : openingWindows) {
            if (!start.isBefore(window.getStart()) && !end.isAfter(window.getEnd())) {
                return window;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return eventId;
    }
}
