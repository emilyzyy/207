package use_case.autoschedule.engine;

import use_case.autoschedule.BlockedPeriods;
import use_case.autoschedule.PlacedActivity;
import use_case.autoschedule.SchedulePlan;
import use_case.autoschedule.ScheduleProblem;
import use_case.autoschedule.ScheduleTask;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Re-times an already-chosen visiting order against a different travel matrix.
 *
 * <p>Used after the search, when each leg has been re-estimated for the time it will
 * actually be travelled. The order is kept and only the clock moves, so the user sees
 * the schedule they were promised with the travel times they will really experience.
 * Returns null when the order no longer works at those times, which is the signal to
 * search again rather than to show something untrue.</p>
 */
public final class SchedulePlanRebuilder {

    private final ActivityPlacer placer;

    public SchedulePlanRebuilder(ActivityPlacer placer) {
        this.placer = placer;
    }

    /**
     * @param orderedEventIds the visiting order to preserve
     * @return the re-timed plan, or null when that order is no longer feasible
     */
    public SchedulePlan rebuild(ScheduleProblem problem, List<String> orderedEventIds) {
        List<ScheduleTask> locks = new ArrayList<>(problem.getLockedTasks());
        Collections.sort(locks, Comparator
                .comparing((ScheduleTask task) -> task.getLockedAt().getStart())
                .thenComparing(ScheduleTask::getEventId));

        List<PlacedActivity> placements = new ArrayList<>();
        LocalTime cursor = problem.getAvailability().getStart();
        ScheduleTask previous = null;
        int lockedIndex = 0;

        for (String eventId : orderedEventIds) {
            ScheduleTask task = findTask(problem, eventId);
            if (task == null) {
                return null;
            }
            PlacedActivity placed;
            if (task.isLocked()) {
                BlockedPeriods withoutThisLock = problem.blockedPeriodsFrom(locks, lockedIndex + 1);
                placed = placer.placeLocked(problem, task, cursor, previous, withoutThisLock);
                lockedIndex++;
            } else {
                BlockedPeriods blocked = problem.blockedPeriodsFrom(locks, lockedIndex);
                placed = placer.placeMovable(problem, task, cursor, previous, blocked);
            }
            if (placed == null) {
                return null;
            }
            placements.add(placed);
            cursor = placed.getEnd();
            previous = task;
        }

        if (placements.isEmpty()) {
            return null;
        }
        return new SchedulePlan(placements,
                ScheduleEngine.score(placements, problem.getPreferences()));
    }

    private ScheduleTask findTask(ScheduleProblem problem, String eventId) {
        for (ScheduleTask task : problem.allTasks()) {
            if (task.getEventId().equals(eventId)) {
                return task;
            }
        }
        return null;
    }
}
