package closeai.application.autoschedule.engine;

import closeai.application.autoschedule.PlacedActivity;
import closeai.application.autoschedule.ScheduleConflict;
import closeai.application.autoschedule.SchedulePlan;
import closeai.application.autoschedule.ScheduleProblem;
import closeai.application.autoschedule.ScheduleScore;
import closeai.application.autoschedule.ScheduleTask;
import closeai.application.autoschedule.TimeWindow;
import closeai.application.autoschedule.TravelMatrix;
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
 * <p>Each candidate is placed at its earliest feasible time, which makes the search
 * a search over <em>orders</em> rather than over times. Four rules cut the tree:
 * an infeasible placement, a remaining-work lower bound, an incumbent bound, and the
 * node budget. There is deliberately no dominance cache — with time-dependent costs
 * two partial schedules are frequently incomparable, and the brute-force cross-check
 * test is a safer way to buy the same confidence.</p>
 */
public final class ScheduleEngine {

    private final List<PlacementRule> placementRules;

    public ScheduleEngine() {
        this(Collections.<PlacementRule>emptyList());
    }

    public ScheduleEngine(List<PlacementRule> placementRules) {
        this.placementRules = Collections.unmodifiableList(new ArrayList<>(
                placementRules == null ? Collections.<PlacementRule>emptyList() : placementRules));
    }

    public ScheduleSearchResult search(ScheduleProblem problem, SearchBudget budget) {
        if (problem == null || budget == null) {
            throw new IllegalArgumentException("Problem and budget are required");
        }

        List<ScheduleTask> locked = sortedByLockStart(problem.getLockedTasks());
        List<ScheduleTask> movable = sortedById(problem.getMovableTasks());

        SearchState state = new SearchState(problem, locked, budget);
        SchedulePlan greedy = new GreedyPlanner().plan(problem, locked, placementRules);
        state.best = greedy;

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
            SchedulePlan candidate = new SchedulePlan(placements, score(placements));
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

        if (!allLockedPlaced) {
            ScheduleTask lockedTask = state.locked.get(lockedIndex);
            PlacedActivity placed = placeLocked(state.problem, lockedTask, cursor, previous);
            if (placed != null) {
                List<PlacedActivity> extended = append(placements, placed);
                explore(state, placed.getEnd(), lockedTask, remaining, lockedIndex + 1, extended);
            }
        }

        for (int i = 0; i < remaining.size(); i++) {
            ScheduleTask candidate = remaining.get(i);
            PlacedActivity placed = placeMovable(state, candidate, cursor, previous, lockedIndex);
            if (placed == null) {
                continue;
            }
            List<ScheduleTask> rest = withoutIndex(remaining, i);
            List<PlacedActivity> extended = append(placements, placed);
            explore(state, placed.getEnd(), candidate, rest, lockedIndex, extended);
        }
    }

    /** Places a movable activity at the earliest time that satisfies every hard rule. */
    private PlacedActivity placeMovable(SearchState state, ScheduleTask task, LocalTime cursor,
                                        ScheduleTask previous, int lockedIndex) {
        ScheduleProblem problem = state.problem;
        TimeWindow availability = problem.getAvailability();

        int travel = travelMinutes(problem.getTravel(), previous, task, cursor);
        LocalTime arrival = plusMinutes(cursor, travel);
        if (arrival == null) {
            return null;
        }

        LocalTime start = latest(arrival, task.getOpeningTime(), availability.getStart());
        LocalTime end = plusMinutes(start, task.getDurationMinutes());
        if (end == null) {
            return null;
        }

        // Slide past any blocked period or locked activity the placement would collide with.
        for (int guard = 0; guard < problem.getUnavailableWindows().size() + state.locked.size() + 2; guard++) {
            LocalTime pushed = start;
            for (TimeWindow blocked : problem.getUnavailableWindows()) {
                if (blocked.overlaps(new TimeWindow(start, end))) {
                    pushed = later(pushed, blocked.getEnd());
                }
            }
            for (int i = lockedIndex; i < state.locked.size(); i++) {
                TimeWindow lockedWindow = state.locked.get(i).getLockedAt();
                if (lockedWindow.overlaps(new TimeWindow(start, end))) {
                    // The locked activity must happen first; this branch will try that instead.
                    return null;
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

        if (end.isAfter(task.getClosingTime()) || end.isAfter(availability.getEnd())) {
            return null;
        }
        if (start.isBefore(task.getOpeningTime()) || start.isBefore(availability.getStart())) {
            return null;
        }
        if (!allowedByRules(problem, task, start, end, travel)) {
            return null;
        }

        return placement(task, start, end, travel, arrival);
    }

    /** Confirms the next locked activity can still be reached in time from the cursor. */
    private PlacedActivity placeLocked(ScheduleProblem problem, ScheduleTask task,
                                       LocalTime cursor, ScheduleTask previous) {
        TimeWindow window = task.getLockedAt();
        int travel = travelMinutes(problem.getTravel(), previous, task, cursor);
        LocalTime arrival = plusMinutes(cursor, travel);
        if (arrival == null || arrival.isAfter(window.getStart())) {
            return null;
        }
        if (!allowedByRules(problem, task, window.getStart(), window.getEnd(), travel)) {
            return null;
        }
        return placement(task, window.getStart(), window.getEnd(), travel, arrival);
    }

    private PlacedActivity placement(ScheduleTask task, LocalTime start, LocalTime end,
                                     int travel, LocalTime arrival) {
        int idle = minutesBetween(arrival, start);
        int unavoidable = arrival.isBefore(task.getOpeningTime())
                ? minutesBetween(arrival, earlier(task.getOpeningTime(), start))
                : 0;
        return new PlacedActivity(task, start, end, travel, idle, unavoidable);
    }

    private boolean allowedByRules(ScheduleProblem problem, ScheduleTask task,
                                   LocalTime start, LocalTime end, int travel) {
        for (PlacementRule rule : placementRules) {
            if (!rule.allows(problem, task, start, end, travel)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Travel for the leg into {@code task}, read from the bucket containing the moment
     * the traveller leaves the previous activity.
     */
    private int travelMinutes(TravelMatrix travel, ScheduleTask previous,
                              ScheduleTask task, LocalTime departure) {
        if (previous == null) {
            return 0;
        }
        return travel.estimateAt(previous.getEventId(), task.getEventId(), departure).getMinutes();
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

        int available = minutesBetween(cursor, state.problem.getAvailability().getEnd());
        return required > available;
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
     * <p>Comparison is on the numeric tiers only, and strictly: a branch that could tie
     * is explored, because the final identifier tie-break is not knowable from a partial
     * schedule and discarding ties would make the result depend on search order.</p>
     */
    private boolean cannotBeatIncumbent(SearchState state, List<PlacedActivity> placements,
                                        ScheduleTask previous, List<ScheduleTask> remaining) {
        if (state.best == null) {
            return false;
        }
        int travelSoFar = 0;
        int idleSoFar = 0;
        for (PlacedActivity placed : placements) {
            travelSoFar += placed.getTravelMinutesBefore();
            idleSoFar += placed.getAvoidableIdleMinutes();
        }
        ScheduleScore optimistic = new ScheduleScore(0,
                travelSoFar + minimumRemainingTravel(state, previous, remaining),
                idleSoFar, 0, "");
        return compareNumeric(optimistic, state.best.getScore()) > 0;
    }

    private static int compareNumeric(ScheduleScore left, ScheduleScore right) {
        int result = Integer.compare(left.getPolicyPenalty(), right.getPolicyPenalty());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(left.getTravelMinutes(), right.getTravelMinutes());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(left.getAvoidableIdleMinutes(), right.getAvoidableIdleMinutes());
        if (result != 0) {
            return result;
        }
        return Integer.compare(left.getOrderDisruption(), right.getOrderDisruption());
    }

    /** Scores a complete schedule. Policy penalties arrive with the soft policies later. */
    static ScheduleScore score(List<PlacedActivity> placements) {
        int travel = 0;
        int avoidableIdle = 0;
        int disruption = 0;
        StringBuilder tieBreak = new StringBuilder();
        List<PlacedActivity> ordered = new ArrayList<>(placements);
        Collections.sort(ordered, (left, right) -> left.getStart().compareTo(right.getStart()));
        for (int position = 0; position < ordered.size(); position++) {
            PlacedActivity placed = ordered.get(position);
            travel += placed.getTravelMinutesBefore();
            avoidableIdle += placed.getAvoidableIdleMinutes();
            disruption += Math.abs(position - placed.getTask().getOriginalIndex());
            tieBreak.append(placed.getTask().getEventId()).append('|');
        }
        return new ScheduleScore(0, travel, avoidableIdle, disruption, tieBreak.toString());
    }

    private ScheduleConflict diagnose(ScheduleProblem problem) {
        TimeWindow availability = problem.getAvailability();
        for (ScheduleTask task : problem.allTasks()) {
            LocalTime windowStart = latest(task.getOpeningTime(), availability.getStart(), null);
            LocalTime windowEnd = earlier(task.getClosingTime(), availability.getEnd());
            int usable = windowEnd.isAfter(windowStart) ? minutesBetween(windowStart, windowEnd) : 0;
            if (usable < task.getDurationMinutes()) {
                return new ScheduleConflict(task.getEventId(),
                        task.getActivity().getName() + " needs " + task.getDurationMinutes()
                                + " minutes but only " + usable
                                + " fit between its opening hours and your available time.");
            }
        }
        return new ScheduleConflict("",
                "No complete schedule fits your available hours once travel between these "
                        + "activities is included. Your original Day Plan was not changed.");
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

    private static List<PlacedActivity> append(List<PlacedActivity> placements, PlacedActivity placed) {
        List<PlacedActivity> copy = new ArrayList<>(placements);
        copy.add(placed);
        return copy;
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

    private static LocalTime latest(LocalTime first, LocalTime second, LocalTime third) {
        LocalTime result = later(first, second);
        return third == null ? result : later(result, third);
    }
}
