package closeai.application.autoschedule.engine;

import closeai.application.autoschedule.PlacedActivity;
import closeai.application.autoschedule.SchedulePlan;
import closeai.application.autoschedule.ScheduleProblem;
import closeai.application.autoschedule.ScheduleTask;
import closeai.application.autoschedule.TimeWindow;
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
 * to fit), then the shortest travel leg, then event id so the result is deterministic.</p>
 */
public final class GreedyPlanner {

    public SchedulePlan plan(ScheduleProblem problem, List<ScheduleTask> lockedSortedByStart,
                             List<PlacementRule> placementRules) {
        List<ScheduleTask> remaining = new ArrayList<>(problem.getMovableTasks());
        List<PlacedActivity> placements = new ArrayList<>();
        LocalTime cursor = problem.getAvailability().getStart();
        ScheduleTask previous = null;
        int lockedIndex = 0;

        while (!remaining.isEmpty() || lockedIndex < lockedSortedByStart.size()) {
            ScheduleTask nextLocked = lockedIndex < lockedSortedByStart.size()
                    ? lockedSortedByStart.get(lockedIndex) : null;

            ScheduleTask chosen = null;
            PlacedActivity chosenPlacement = null;
            for (ScheduleTask candidate : ordered(remaining, problem, previous, cursor)) {
                PlacedActivity placed = attempt(problem, candidate, cursor, previous,
                        lockedSortedByStart, lockedIndex, placementRules);
                if (placed == null) {
                    continue;
                }
                if (nextLocked != null && placed.getEnd().isAfter(nextLocked.getLockedAt().getStart())) {
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
            PlacedActivity lockedPlacement = attemptLocked(problem, nextLocked, cursor, previous,
                    placementRules);
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
        return new SchedulePlan(placements, ScheduleEngine.score(placements));
    }

    private List<ScheduleTask> ordered(List<ScheduleTask> remaining, ScheduleProblem problem,
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

    private PlacedActivity attempt(ScheduleProblem problem, ScheduleTask task, LocalTime cursor,
                                   ScheduleTask previous, List<ScheduleTask> locked,
                                   int lockedIndex, List<PlacementRule> placementRules) {
        int travel = previous == null ? 0
                : problem.getTravel().estimateAt(previous.getEventId(), task.getEventId(), cursor)
                        .getMinutes();
        LocalTime arrival = ScheduleEngine.plusMinutes(cursor, travel);
        if (arrival == null) {
            return null;
        }
        LocalTime start = ScheduleEngine.later(
                ScheduleEngine.later(arrival, task.getOpeningTime()),
                problem.getAvailability().getStart());
        LocalTime end = ScheduleEngine.plusMinutes(start, task.getDurationMinutes());
        if (end == null) {
            return null;
        }

        for (int guard = 0; guard < problem.getUnavailableWindows().size() + 2; guard++) {
            LocalTime pushed = start;
            for (TimeWindow blocked : problem.getUnavailableWindows()) {
                if (blocked.overlaps(new TimeWindow(start, end))) {
                    pushed = ScheduleEngine.later(pushed, blocked.getEnd());
                }
            }
            if (pushed.equals(start)) {
                break;
            }
            start = pushed;
            end = ScheduleEngine.plusMinutes(start, task.getDurationMinutes());
            if (end == null) {
                return null;
            }
        }

        for (int i = lockedIndex; i < locked.size(); i++) {
            if (locked.get(i).getLockedAt().overlaps(new TimeWindow(start, end))) {
                return null;
            }
        }
        if (end.isAfter(task.getClosingTime()) || end.isAfter(problem.getAvailability().getEnd())) {
            return null;
        }
        for (PlacementRule rule : placementRules) {
            if (!rule.allows(problem, task, start, end, travel)) {
                return null;
            }
        }

        int idle = ScheduleEngine.minutesBetween(arrival, start);
        int unavoidable = arrival.isBefore(task.getOpeningTime())
                ? ScheduleEngine.minutesBetween(arrival,
                        ScheduleEngine.earlier(task.getOpeningTime(), start))
                : 0;
        return new PlacedActivity(task, start, end, travel, idle, unavoidable);
    }

    private PlacedActivity attemptLocked(ScheduleProblem problem, ScheduleTask task, LocalTime cursor,
                                         ScheduleTask previous, List<PlacementRule> placementRules) {
        TimeWindow window = task.getLockedAt();
        int travel = previous == null ? 0
                : problem.getTravel().estimateAt(previous.getEventId(), task.getEventId(), cursor)
                        .getMinutes();
        LocalTime arrival = ScheduleEngine.plusMinutes(cursor, travel);
        if (arrival == null || arrival.isAfter(window.getStart())) {
            return null;
        }
        for (PlacementRule rule : placementRules) {
            if (!rule.allows(problem, task, window.getStart(), window.getEnd(), travel)) {
                return null;
            }
        }
        int idle = ScheduleEngine.minutesBetween(arrival, window.getStart());
        int unavoidable = arrival.isBefore(task.getOpeningTime())
                ? ScheduleEngine.minutesBetween(arrival,
                        ScheduleEngine.earlier(task.getOpeningTime(), window.getStart()))
                : 0;
        return new PlacedActivity(task, window.getStart(), window.getEnd(), travel, idle, unavoidable);
    }
}
