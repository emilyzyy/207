package closeai.application.autoschedule.engine;

import closeai.application.autoschedule.BlockedPeriods;
import closeai.application.autoschedule.PlacedActivity;
import closeai.application.autoschedule.ScheduleConflict;
import closeai.application.autoschedule.SchedulePlan;
import closeai.application.autoschedule.ScheduleProblem;
import closeai.application.autoschedule.ScheduleScore;
import closeai.application.autoschedule.ScheduleTask;
import closeai.application.autoschedule.SchedulingPreferences;
import closeai.application.autoschedule.TimeWindow;
import closeai.application.autoschedule.policy.SoftPolicy;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic bounded branch-and-bound over the orders in which the movable
 * activities can be visited.
 *
 * <p>The engine is a pure function of its {@link ScheduleProblem}: it performs no
 * repository, network or UI work, and travel times come from a matrix that was
 * prefetched before the search began. That is what makes it exhaustively testable
 * and what keeps every external call outside the recursion.</p>
 *
 * <p>Each candidate is placed at its earliest feasible time, which makes the search a
 * search over <em>orders</em> rather than over times. Four rules cut the tree: an
 * infeasible placement, a remaining-work lower bound, an incumbent bound, and the node
 * budget. There is deliberately no dominance cache — with time-dependent costs two
 * partial schedules are frequently incomparable, and the brute-force cross-check test
 * is a safer way to buy the same confidence.</p>
 *
 * <p>Soft preferences reach the engine as a list of policy objects chosen by the
 * Interactor. The engine scores whatever list it is given and never asks which policy
 * is which, so switching a preference off is a change of input rather than a branch in
 * the search.</p>
 */
public final class ScheduleEngine {

    private final ActivityPlacer placer;

    public ScheduleEngine() {
        this(Collections.<PlacementRule>emptyList());
    }

    public ScheduleEngine(List<PlacementRule> placementRules) {
        List<PlacementRule> rules = Collections.unmodifiableList(new ArrayList<>(
                placementRules == null ? Collections.<PlacementRule>emptyList() : placementRules));
        this.placer = new ActivityPlacer(rules);
    }

    /** The placer this engine uses, so re-timing obeys exactly the same rules. */
    public ActivityPlacer placer() {
        return placer;
    }

    public ScheduleSearchResult search(ScheduleProblem problem, SearchBudget budget) {
        if (problem == null || budget == null) {
            throw new IllegalArgumentException("Problem and budget are required");
        }

        List<ScheduleTask> locked = sortedByLockStart(problem.getLockedTasks());
        List<ScheduleTask> movable = sortedById(problem.getMovableTasks());

        SearchState state = new SearchState(problem, locked, budget);
        state.best = new GreedyPlanner().plan(problem, locked, placer);

        explore(state, problem.getAvailability().getStart(), null, movable, 0,
                new ArrayList<PlacedActivity>());

        boolean withinLimit = !budget.isExhausted();
        if (state.best == null) {
            return ScheduleSearchResult.conflict(diagnose(problem), withinLimit, budget.getUsedNodes());
        }
        return ScheduleSearchResult.found(state.best, withinLimit, budget.getUsedNodes());
    }

    /**
     * Depth-first exploration in time order. At every node the next thing to happen is
     * either the next locked activity or one of the remaining movable activities.
     */
    private void explore(SearchState state, LocalTime cursor, ScheduleTask previous,
                         List<ScheduleTask> remaining, int lockedIndex,
                         List<PlacedActivity> placements) {
        if (!state.budget.consume()) {
            return;
        }

        boolean allLockedPlaced = lockedIndex >= state.locked.size();
        if (remaining.isEmpty() && allLockedPlaced) {
            SchedulePlan candidate = new SchedulePlan(placements,
                    score(placements, state.problem.getPreferences()));
            if (state.best == null || candidate.getScore().compareTo(state.best.getScore()) < 0) {
                state.best = candidate;
            }
            return;
        }

        if (exceedsRemainingTimeBound(state, cursor, previous, remaining)) {
            return;
        }
        if (cannotBeatIncumbent(state, placements, previous, remaining)) {
            return;
        }

        BlockedPeriods blocked = state.problem.blockedPeriodsFrom(state.locked, lockedIndex);

        if (!allLockedPlaced) {
            ScheduleTask lockedTask = state.locked.get(lockedIndex);
            BlockedPeriods withoutThisLock =
                    state.problem.blockedPeriodsFrom(state.locked, lockedIndex + 1);
            PlacedActivity placed = placer.placeLocked(state.problem, lockedTask, cursor,
                    previous, withoutThisLock);
            if (placed != null) {
                explore(state, placed.getEnd(), lockedTask, remaining, lockedIndex + 1,
                        append(placements, placed));
            }
        }

        for (int i = 0; i < remaining.size(); i++) {
            ScheduleTask candidate = remaining.get(i);
            PlacedActivity placed = placer.placeMovable(state.problem, candidate, cursor,
                    previous, blocked);
            if (placed == null) {
                continue;
            }
            explore(state, placed.getEnd(), candidate, withoutIndex(remaining, i), lockedIndex,
                    append(placements, placed));
        }
    }

    /**
     * Prunes when the activities still to place cannot physically fit in the time left.
     *
     * <p>The travel term uses the smallest estimate across every prefetched period, so
     * it can never exceed the real bucketed cost. An admissible bound like this one is
     * what lets the search discard branches without risking the optimum.</p>
     */
    private boolean exceedsRemainingTimeBound(SearchState state, LocalTime cursor,
                                              ScheduleTask previous, List<ScheduleTask> remaining) {
        if (remaining.isEmpty()) {
            return false;
        }
        int required = 0;
        for (ScheduleTask task : remaining) {
            required += task.getDurationMinutes();
        }
        required += minimumRemainingTravel(state, previous, remaining);
        return required > ActivityPlacer.minutesBetween(cursor,
                state.problem.getAvailability().getEnd());
    }

    /**
     * Optimistic travel still to be paid: each remaining activity is charged the
     * cheapest leg that could possibly reach it, minimised over periods.
     *
     * <p>When nothing has been placed yet, one of the remaining activities will open
     * the day with no travel before it, so the largest of those charges is dropped.
     * Without that correction the bound could exceed the true remaining cost and prune
     * the optimal schedule.</p>
     */
    private int minimumRemainingTravel(SearchState state, ScheduleTask previous,
                                       List<ScheduleTask> remaining) {
        if (remaining.isEmpty()) {
            return 0;
        }
        List<String> sources = new ArrayList<>();
        if (previous != null) {
            sources.add(previous.getEventId());
        }
        for (ScheduleTask task : remaining) {
            sources.add(task.getEventId());
        }

        int total = 0;
        int largest = 0;
        for (ScheduleTask task : remaining) {
            int cheapest = state.problem.getTravel().minIncomingMinutes(task.getEventId(), sources);
            total += cheapest;
            largest = Math.max(largest, cheapest);
        }
        return previous == null ? total - largest : total;
    }

    /**
     * Prunes when even a perfect completion could not beat the incumbent.
     *
     * <p>Every part of the cost is non-negative and only accumulates, so the cost of what
     * has already been placed, plus the cheapest travel that could possibly link what
     * remains, is a genuine floor on the finished schedule. Comparison is strict, so a
     * branch that could merely tie is still explored: the final identifier tie-break is
     * not knowable from a partial schedule, and discarding ties would let the answer
     * depend on the order the tree happened to be walked.</p>
     */
    private boolean cannotBeatIncumbent(SearchState state, List<PlacedActivity> placements,
                                        ScheduleTask previous, List<ScheduleTask> remaining) {
        if (state.best == null) {
            return false;
        }
        SchedulingPreferences preferences = state.problem.getPreferences();
        int costSoFar = 0;
        for (PlacedActivity placed : placements) {
            costSoFar += placed.getTravelMinutesBefore();
            costSoFar += placed.getAvoidableIdleMinutes();
            costSoFar += policyPenalty(placed, preferences);
        }
        int floor = costSoFar + minimumRemainingTravel(state, previous, remaining);
        return floor > state.best.getScore().practicalCostMinutes();
    }

    private static int policyPenalty(PlacedActivity placement, SchedulingPreferences preferences) {
        int penalty = 0;
        for (SoftPolicy policy : preferences.getPolicies()) {
            penalty += policy.penaltyMinutes(placement, preferences.getContext());
        }
        return penalty;
    }

    /**
     * Scores a complete schedule as one practical cost in minutes.
     *
     * <p>Travel, wasted waiting and the capped soft penalties are simply added, so a
     * small improvement in one can never be worth a large sacrifice in another.</p>
     */
    public static ScheduleScore score(List<PlacedActivity> placements,
                                      SchedulingPreferences preferences) {
        SchedulingPreferences active = preferences == null
                ? SchedulingPreferences.none() : preferences;
        int penalty = 0;
        int travel = 0;
        int avoidableIdle = 0;
        int displacement = 0;
        StringBuilder tieBreak = new StringBuilder();

        List<PlacedActivity> ordered = new ArrayList<>(placements);
        Collections.sort(ordered, (left, right) -> left.getStart().compareTo(right.getStart()));
        for (int position = 0; position < ordered.size(); position++) {
            PlacedActivity placed = ordered.get(position);
            penalty += policyPenalty(placed, active);
            travel += placed.getTravelMinutesBefore();
            avoidableIdle += placed.getAvoidableIdleMinutes();
            displacement += Math.abs(position - placed.getTask().getOriginalIndex());
            tieBreak.append(placed.getTask().getEventId()).append('/');
        }
        return new ScheduleScore(travel, avoidableIdle, penalty,
                active.orderPenaltyFor(displacement), tieBreak.toString());
    }

    private ScheduleConflict diagnose(ScheduleProblem problem) {
        TimeWindow availability = problem.getAvailability();
        for (ScheduleTask task : problem.allTasks()) {
            LocalTime windowStart = ActivityPlacer.later(task.getOpeningTime(),
                    availability.getStart());
            LocalTime windowEnd = ActivityPlacer.earlier(task.getClosingTime(),
                    availability.getEnd());
            int usable = windowEnd.isAfter(windowStart)
                    ? ActivityPlacer.minutesBetween(windowStart, windowEnd) : 0;
            if (usable < task.getDurationMinutes()) {
                return ScheduleConflict.activityCannotFit(task.getEventId(),
                        task.getActivity().getName(), task.getDurationMinutes(), usable);
            }
        }
        return ScheduleConflict.noFeasibleOrder();
    }

    private static List<ScheduleTask> sortedByLockStart(List<ScheduleTask> tasks) {
        List<ScheduleTask> sorted = new ArrayList<>(tasks);
        Collections.sort(sorted, Comparator
                .comparing((ScheduleTask task) -> task.getLockedAt().getStart())
                .thenComparing(ScheduleTask::getEventId));
        return sorted;
    }

    private static List<ScheduleTask> sortedById(List<ScheduleTask> tasks) {
        List<ScheduleTask> sorted = new ArrayList<>(tasks);
        Collections.sort(sorted, Comparator.comparing(ScheduleTask::getEventId));
        return sorted;
    }

    private static List<ScheduleTask> withoutIndex(List<ScheduleTask> tasks, int index) {
        List<ScheduleTask> copy = new ArrayList<>(tasks);
        copy.remove(index);
        return copy;
    }

    private static List<PlacedActivity> append(List<PlacedActivity> placements,
                                               PlacedActivity placed) {
        List<PlacedActivity> copy = new ArrayList<>(placements);
        copy.add(placed);
        return copy;
    }
}
