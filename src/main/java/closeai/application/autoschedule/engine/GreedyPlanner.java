package closeai.application.autoschedule.engine;

import closeai.application.autoschedule.BlockedPeriods;
import closeai.application.autoschedule.PlacedActivity;
import closeai.application.autoschedule.SchedulePlan;
import closeai.application.autoschedule.ScheduleProblem;
import closeai.application.autoschedule.ScheduleTask;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Builds one feasible schedule quickly, used as the search's starting incumbent.
 *
 * <p>Having a feasible answer before the search begins means the incumbent bound can
 * prune from the very first node, and it means an exhausted node budget still returns
 * a usable schedule rather than nothing.</p>
 *
 * <p>The heuristic is closing-time urgency first (venues that shut soonest are hardest
 * to fit), then the shortest travel leg, then event id so the result is deterministic.
 * It is intentionally shortsighted; the search exists to beat it.</p>
 */
public final class GreedyPlanner {

    public SchedulePlan plan(ScheduleProblem problem, List<ScheduleTask> lockedSortedByStart,
                             ActivityPlacer placer) {
        List<ScheduleTask> remaining = new ArrayList<>(problem.getMovableTasks());
        List<PlacedActivity> placements = new ArrayList<>();
        LocalTime cursor = problem.getAvailability().getStart();
        ScheduleTask previous = null;
        int lockedIndex = 0;

        while (!remaining.isEmpty() || lockedIndex < lockedSortedByStart.size()) {
            ScheduleTask nextLocked = lockedIndex < lockedSortedByStart.size()
                    ? lockedSortedByStart.get(lockedIndex) : null;
            BlockedPeriods blocked = problem.blockedPeriodsFrom(lockedSortedByStart, lockedIndex);

            ScheduleTask chosen = null;
            PlacedActivity chosenPlacement = null;
            for (ScheduleTask candidate : byUrgency(remaining, problem, previous, cursor)) {
                PlacedActivity placed = placer.placeMovable(problem, candidate, cursor,
                        previous, blocked);
                if (placed == null) {
                    continue;
                }
                chosen = candidate;
                chosenPlacement = placed;
                break;
            }

            if (chosen != null) {
                placements.add(chosenPlacement);
                cursor = chosenPlacement.getEnd();
                previous = chosen;
                remaining.remove(chosen);
                continue;
            }

            if (nextLocked == null) {
                return null;
            }
            BlockedPeriods withoutThisLock =
                    problem.blockedPeriodsFrom(lockedSortedByStart, lockedIndex + 1);
            PlacedActivity lockedPlacement = placer.placeLocked(problem, nextLocked, cursor,
                    previous, withoutThisLock);
            if (lockedPlacement == null) {
                return null;
            }
            placements.add(lockedPlacement);
            cursor = lockedPlacement.getEnd();
            previous = nextLocked;
            lockedIndex++;
        }

        if (placements.isEmpty()) {
            return null;
        }
        return new SchedulePlan(placements,
                ScheduleEngine.score(placements, problem.getPreferences()));
    }

    private List<ScheduleTask> byUrgency(List<ScheduleTask> remaining, ScheduleProblem problem,
                                         ScheduleTask previous, LocalTime cursor) {
        List<ScheduleTask> sorted = new ArrayList<>(remaining);
        Collections.sort(sorted, Comparator
                .comparing(ScheduleTask::getClosingTime)
                .thenComparingInt((ScheduleTask task) -> previous == null ? 0
                        : problem.getTravel().estimateAt(previous.getEventId(),
                                task.getEventId(), cursor).getMinutes())
                .thenComparing(ScheduleTask::getEventId));
        return sorted;
    }
}
