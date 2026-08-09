package use_case.autoschedule.engine;

import use_case.autoschedule.BlockedPeriods;
import use_case.autoschedule.PlacedActivity;
import use_case.autoschedule.SchedulePlan;
import use_case.autoschedule.ScheduleConflict;
import use_case.autoschedule.ScheduleProblem;
import use_case.autoschedule.ScheduleTask;
import use_case.autoschedule.TimeWindow;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks a finished schedule against every hard rule, independently of whatever built it.
 *
 * <p>This exists because the times shown to the user are not the times the search
 * reasoned about: the search compares orders using bucketed travel estimates, and the
 * chosen plan is then re-timed with estimates for its actual departure times. Re-checking
 * the finished article means a schedule can never reach the user with an overlap, a
 * missed opening time, or too little time to travel, whatever happened upstream.</p>
 */
public final class PlanValidator {

    /**
     * @return null when the plan satisfies every hard rule, otherwise the violation
     */
    public ScheduleConflict validate(ScheduleProblem problem, SchedulePlan plan) {
        if (plan == null) {
            return ScheduleConflict.noFeasibleOrder();
        }

        List<PlacedActivity> placements = plan.getPlacements();
        ScheduleConflict completeness = checkEveryActivityPlacedOnce(problem, placements);
        if (completeness != null) {
            return completeness;
        }

        TimeWindow availability = problem.getAvailability();
        BlockedPeriods unavailable = BlockedPeriods.of(problem.getUnavailableWindows());

        PlacedActivity previous = null;
        for (PlacedActivity placed : placements) {
            ScheduleTask task = placed.getTask();
            TimeWindow window = placed.window();

            if (!availability.encloses(window)) {
                return ScheduleConflict.of(ScheduleConflict.Kind.NO_FEASIBLE_ORDER,
                        task.getEventId(), task.getActivity().getName());
            }
            if (window.getStart().isBefore(task.getOpeningTime())
                    || window.getEnd().isAfter(task.getClosingTime())) {
                return ScheduleConflict.of(ScheduleConflict.Kind.NO_FEASIBLE_ORDER,
                        task.getEventId(), task.getActivity().getName());
            }
            if (unavailable.blocks(window)) {
                return ScheduleConflict.of(ScheduleConflict.Kind.LOCK_INSIDE_UNAVAILABLE_PERIOD,
                        task.getEventId(), task.getActivity().getName());
            }
            if (task.isLocked() && !window.equals(task.getLockedAt())) {
                return ScheduleConflict.of(ScheduleConflict.Kind.LOCKS_OVERLAP,
                        task.getEventId(), task.getActivity().getName());
            }

            TimeWindow travel = placed.travelWindow();
            if (travel != null) {
                if (unavailable.blocks(travel)) {
                    return ScheduleConflict.of(
                            ScheduleConflict.Kind.LOCK_INSIDE_UNAVAILABLE_PERIOD,
                            task.getEventId(), task.getActivity().getName());
                }
                if (travel.getEnd().isAfter(window.getStart())) {
                    return ScheduleConflict.refinedTravelInfeasible();
                }
                if (previous != null && travel.getStart().isBefore(previous.getEnd())) {
                    return ScheduleConflict.refinedTravelInfeasible();
                }
            }

            if (previous != null) {
                if (window.getStart().isBefore(previous.getEnd())) {
                    return ScheduleConflict.refinedTravelInfeasible();
                }
                boolean needsTravel = placed.getTravelMinutesBefore() > 0;
                LocalTime earliestArrival = previous.getEnd()
                        .plusMinutes(placed.getTravelMinutesBefore());
                if (needsTravel && window.getStart().isBefore(earliestArrival)) {
                    return ScheduleConflict.refinedTravelInfeasible();
                }
            }
            previous = placed;
        }
        return null;
    }

    private ScheduleConflict checkEveryActivityPlacedOnce(ScheduleProblem problem,
                                                          List<PlacedActivity> placements) {
        Set<String> placed = new HashSet<>();
        for (PlacedActivity placement : placements) {
            if (!placed.add(placement.getTask().getEventId())) {
                return ScheduleConflict.of(ScheduleConflict.Kind.NO_FEASIBLE_ORDER,
                        placement.getTask().getEventId(), "scheduled more than once");
            }
        }
        for (ScheduleTask task : problem.allTasks()) {
            if (!placed.contains(task.getEventId())) {
                return ScheduleConflict.of(ScheduleConflict.Kind.NO_FEASIBLE_ORDER,
                        task.getEventId(), task.getActivity().getName());
            }
        }
        return null;
    }
}
