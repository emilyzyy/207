package use_case.autoschedule.engine;

import use_case.autoschedule.BlockedPeriods;
import use_case.autoschedule.PlacedActivity;
import use_case.autoschedule.ScheduleProblem;
import use_case.autoschedule.ScheduleTask;
import use_case.autoschedule.TimeWindow;
import use_case.autoschedule.TravelLeg;
import use_case.autoschedule.TravelLegPlanner;
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

        LocalTime start = intoOpeningHours(task,
                later(leg.getArrival(), availability.getStart()));
        if (start == null) {
            return null;
        }
        LocalTime end = plusMinutes(start, task.getDurationMinutes());
        if (end == null) {
            return null;
        }

        // Slide the activity past anything it would collide with, then back into an opening
        // window, then re-check. Both moves only ever go forwards, so this settles.
        for (int attempt = 0; attempt <= blocked.getWindows().size() + 1; attempt++) {
            LocalTime pushed = start;
            for (TimeWindow window : blocked.getWindows()) {
                if (window.overlaps(new TimeWindow(start, end))) {
                    pushed = later(pushed, window.getEnd());
                }
            }
            pushed = intoOpeningHours(task, pushed);
            if (pushed == null) {
                return null;
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
        if (end.isAfter(availability.getEnd()) || start.isBefore(availability.getStart())) {
            return null;
        }
        if (!task.isOpenThroughout(start, end)) {
            return null;
        }
        if (!allowedByRules(problem, task, start, end, leg.getMinutes())) {
            return null;
        }
        return build(problem, task, start, end, cursor, previous, leg, blocked);
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
        return build(problem, task, window.getStart(), window.getEnd(), cursor, previous,
                leg, blocked);
    }

    /**
     * The earliest time at or after {@code earliest} where the whole visit fits inside one
     * opening window.
     *
     * <p>Returns null when no window can hold it: the venue is shut all day, every window
     * is already past, or each is shorter than the visit. That is a hard refusal — opening
     * hours are not something the schedule may overrun to make the day work.</p>
     *
     * <p>When the venue's hours are unknown the task carries a single permissive window, so
     * this behaves exactly as the old single opening/closing pair did.</p>
     */
    private static LocalTime intoOpeningHours(ScheduleTask task, LocalTime earliest) {
        LocalTime best = null;
        for (TimeWindow window : task.getOpeningWindows()) {
            LocalTime candidate = later(earliest, window.getStart());
            LocalTime finish = plusMinutes(candidate, task.getDurationMinutes());
            if (finish == null || finish.isAfter(window.getEnd())) {
                continue;
            }
            if (best == null || candidate.isBefore(best)) {
                best = candidate;
            }
        }
        return best;
    }

    private PlacedActivity build(ScheduleProblem problem, ScheduleTask task, LocalTime start,
                                 LocalTime end, LocalTime cursor, ScheduleTask previous,
                                 TravelLeg leg, BlockedPeriods blocked) {
        // Waiting for the doors to open is not avoidable, so the opening time that matters
        // is the one for the window this visit is actually in.
        TimeWindow window = task.openingWindowFor(start, end);
        // Measured from the earliest possible arrival, and deliberately not from the journey
        // actually travelled. Setting out later does not reclaim dead time; it only moves it
        // to the near side of the journey. Scoring the just-in-time leg instead would report
        // no avoidable waiting anywhere and quietly delete "minimize gaps" from the
        // objective.
        int avoidable = legPlanner.avoidableIdleMinutes(leg.getArrival(), start,
                window == null ? task.getOpeningTime() : window.getStart(), blocked);

        String fromId = previous == null ? null : previous.getEventId();
        TravelLeg travelled = legPlanner.latestArrivingBy(problem.getTravel(), fromId,
                task.getEventId(), cursor, blocked, start, leg);
        int idle = minutesBetween(cursor, start) - travelled.getMinutes();
        return new PlacedActivity(task, start, end, travelled.getDeparture(),
                travelled.getMinutes(), Math.max(0, idle), avoidable);
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
}
