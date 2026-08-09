package use_case.autoschedule;

import java.util.List;

/**
 * Rejects impossible requests before any searching happens.
 *
 * <p>Two reasons this runs first. The user gets a specific sentence naming the lock or
 * window at fault instead of a generic "no schedule found" after a fruitless search.
 * And several of these rules mirror invariants the {@code Trip} entity enforces on save,
 * so catching them here turns what would surface as an exception at Apply into an
 * explanation at Preview.</p>
 */
public final class ProblemValidator {

    /**
     * @return the first conflict found, or null when the request is worth searching
     */
    public ScheduleConflict validate(TimeWindow availability, List<ScheduleTask> tasks,
                                     List<TimeWindow> unavailableWindows) {
        ScheduleConflict windowProblem = validateUnavailableWindows(availability, unavailableWindows);
        if (windowProblem != null) {
            return windowProblem;
        }
        return validateLocks(availability, tasks, unavailableWindows);
    }

    private ScheduleConflict validateUnavailableWindows(TimeWindow availability,
                                                        List<TimeWindow> windows) {
        if (windows == null) {
            return null;
        }
        for (int i = 0; i < windows.size(); i++) {
            TimeWindow window = windows.get(i);
            if (!availability.encloses(window)) {
                return ScheduleConflict.of(ScheduleConflict.Kind.LOCK_OUTSIDE_AVAILABILITY,
                        "", window.toString());
            }
            for (int j = i + 1; j < windows.size(); j++) {
                if (window.overlaps(windows.get(j))) {
                    return ScheduleConflict.of(ScheduleConflict.Kind.LOCKS_OVERLAP,
                            "", window + " and " + windows.get(j));
                }
            }
        }
        return null;
    }

    private ScheduleConflict validateLocks(TimeWindow availability, List<ScheduleTask> tasks,
                                           List<TimeWindow> unavailableWindows) {
        for (int i = 0; i < tasks.size(); i++) {
            ScheduleTask task = tasks.get(i);
            if (!task.isLocked()) {
                continue;
            }
            TimeWindow lock = task.getLockedAt();
            String name = task.getActivity().getName();

            if (!availability.encloses(lock)) {
                return ScheduleConflict.of(ScheduleConflict.Kind.LOCK_OUTSIDE_AVAILABILITY,
                        task.getEventId(), name);
            }
            // Must sit inside one window: a lock spanning a venue's lunchtime closure is
            // outside its opening hours even though it starts and ends while open.
            if (!task.isOpenThroughout(lock.getStart(), lock.getEnd())) {
                return ScheduleConflict.of(ScheduleConflict.Kind.LOCK_OUTSIDE_OPENING_HOURS,
                        task.getEventId(), name);
            }
            if (unavailableWindows != null) {
                for (TimeWindow blocked : unavailableWindows) {
                    if (blocked.overlaps(lock)) {
                        return ScheduleConflict.of(
                                ScheduleConflict.Kind.LOCK_INSIDE_UNAVAILABLE_PERIOD,
                                task.getEventId(), name);
                    }
                }
            }
            for (int j = i + 1; j < tasks.size(); j++) {
                ScheduleTask other = tasks.get(j);
                if (other.isLocked() && lock.overlaps(other.getLockedAt())) {
                    return ScheduleConflict.of(ScheduleConflict.Kind.LOCKS_OVERLAP,
                            task.getEventId(), name + " and " + other.getActivity().getName());
                }
            }
        }
        return null;
    }
}
