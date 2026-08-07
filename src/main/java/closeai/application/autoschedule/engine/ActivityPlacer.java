package closeai.application.autoschedule.engine;

import closeai.application.autoschedule.BlockedPeriods;
import closeai.application.autoschedule.PlacedActivity;
import closeai.application.autoschedule.ScheduleProblem;
import closeai.application.autoschedule.ScheduleTask;
import closeai.application.autoschedule.TimeWindow;
import closeai.application.autoschedule.TravelLeg;
import closeai.application.autoschedule.TravelLegPlanner;
import java.time.LocalTime;
import java.util.List;

/**
 * Places a single activity at the earliest time that satisfies every hard rule.
 *
 * <p>Keeping this in one class means the search explores <em>orders</em> while the
 * question "when can this activity actually happen?" is answered in exactly one place,
 * so the greedy planner, the search and the exact-time rebuild all obey identical rules.</p>
 */
public final class ActivityPlacer {

    private final TravelLegPlanner legPlanner = new TravelLegPlanner();
    private final List<PlacementRule> placementRules;

    public ActivityPlacer(List<PlacementRule> placementRules) {
        this.placementRules = placementRules;
    }

    /**
     * @param blocked  unavailable windows plus every lock still ahead
     * @return the placement, or null when this activity cannot follow the cursor
     */
    public PlacedActivity placeMovable(ScheduleProblem problem, ScheduleTask task,
                                       LocalTime cursor, ScheduleTask previous,
                                       BlockedPeriods blocked) {
        TimeWindow availability = problem.getAvailability();
        String fromId = previous == null ? null : previous.getEventId();

        TravelLeg leg = legPlanner.plan(problem.getTravel(), fromId, task.getEventId(),
                cursor, blocked, availability.getEnd());
        if (leg == null) {
            return null;
        }

        LocalTime start = max(leg.getArrival(), task.getOpeningTime(), availability.getStart());
        LocalTime end = plusMinutes(start, task.getDurationMinutes());
        if (end == null) {
            return null;
        }

        // Slide the activity past anything it would collide with, then re-check.
        for (int attempt = 0; attempt <= blocked.getWindows().size() + 1; attempt++) {
            LocalTime pushed = start;
            for (TimeWindow window : blocked.getWindows()) {
                if (window.overlaps(new TimeWindow(start, end))) {
                    pushed = later(pushed, window.getEnd());
                }
            }
            if (pushed.equals(start)) {
                break;
            }
            start = pushed;
            end = plusMinutes(start, task.getDurationMinutes());
            if (end == null) {
                return null;
            }
        }

        if (blocked.blocks(new TimeWindow(start, end))) {
            return null;
        }
        if (end.isAfter(task.getClosingTime()) || end.isAfter(availability.getEnd())) {
            return null;
        }
        if (start.isBefore(task.getOpeningTime()) || start.isBefore(availability.getStart())) {
            return null;
        }
        if (!allowedByRules(problem, task, start, end, leg.getMinutes())) {
            return null;
        }
        return build(task, start, end, cursor, leg, blocked);
    }

    /** Confirms the traveller can still reach a locked activity by its fixed start. */
    public PlacedActivity placeLocked(ScheduleProblem problem, ScheduleTask task,
                                      LocalTime cursor, ScheduleTask previous,
                                      BlockedPeriods blocked) {
        TimeWindow window = task.getLockedAt();
        String fromId = previous == null ? null : previous.getEventId();

        TravelLeg leg = legPlanner.plan(problem.getTravel(), fromId, task.getEventId(),
                cursor, blocked, window.getStart());
        if (leg == null || leg.getArrival().isAfter(window.getStart())) {
            return null;
        }
        if (!allowedByRules(problem, task, window.getStart(), window.getEnd(), leg.getMinutes())) {
            return null;
        }
        return build(task, window.getStart(), window.getEnd(), cursor, leg, blocked);
    }

    private PlacedActivity build(ScheduleTask task, LocalTime start, LocalTime end,
                                 LocalTime cursor, TravelLeg leg, BlockedPeriods blocked) {
        int idle = minutesBetween(cursor, start) - leg.getMinutes();
        int avoidable = legPlanner.avoidableIdleMinutes(leg.getArrival(), start,
                task.getOpeningTime(), blocked);
        return new PlacedActivity(task, start, end, leg.getDeparture(), leg.getMinutes(),
                Math.max(0, idle), avoidable);
    }

    private boolean allowedByRules(ScheduleProblem problem, ScheduleTask task,
                                   LocalTime start, LocalTime end, int travelMinutes) {
        for (PlacementRule rule : placementRules) {
            if (!rule.allows(problem, task, start, end, travelMinutes)) {
                return false;
            }
        }
        return true;
    }

    static LocalTime plusMinutes(LocalTime time, int minutes) {
        LocalTime result = time.plusMinutes(minutes);
        if (minutes > 0 && !result.isAfter(time)) {
            return null;
        }
        return result;
    }

    static int minutesBetween(LocalTime from, LocalTime to) {
        return (to.toSecondOfDay() - from.toSecondOfDay()) / 60;
    }

    static LocalTime later(LocalTime left, LocalTime right) {
        return left.isAfter(right) ? left : right;
    }

    static LocalTime earlier(LocalTime left, LocalTime right) {
        return left.isBefore(right) ? left : right;
    }

    private static LocalTime max(LocalTime first, LocalTime second, LocalTime third) {
        return later(later(first, second), third);
    }
}
