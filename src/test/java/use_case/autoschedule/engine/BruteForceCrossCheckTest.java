package use_case.autoschedule.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static use_case.autoschedule.ProblemFixtures.at;
import static use_case.autoschedule.ProblemFixtures.noBlockedWindows;
import static use_case.autoschedule.ProblemFixtures.task;
import static use_case.autoschedule.ProblemFixtures.window;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import use_case.autoschedule.DeparturePeriod;
import use_case.autoschedule.PeriodPlan;
import use_case.autoschedule.PlacedActivity;
import use_case.autoschedule.ScheduleProblem;
import use_case.autoschedule.ScheduleScore;
import use_case.autoschedule.ScheduleTask;
import use_case.autoschedule.SchedulingPreferences;
import use_case.autoschedule.TimeWindow;
import use_case.autoschedule.TravelEstimate;
import use_case.autoschedule.TravelMatrix;

/**
 * The safety net for the pruning rules.
 *
 * <p>Branch-and-bound is only trustworthy if its bounds never discard a branch that
 * could have produced the best schedule. Rather than reasoning about that by hand,
 * these tests enumerate every possible order exhaustively and require the pruned
 * search to return an equally good schedule — including on problems where the same
 * route costs different amounts at different times of day, which is exactly where an
 * unsound bound would show up.</p>
 */
class BruteForceCrossCheckTest {

    private final ScheduleEngine engine = new ScheduleEngine();

    @Test
    void prunedSearchMatchesExhaustiveSearchOnRandomBucketedProblems() {
        final Random random = new Random(20260805L);
        int compared = 0;

        for (int trial = 0; trial < 60; trial++) {
            final int taskCount = 3 + random.nextInt(3);
            final TimeWindow availability = window(9, 21);
            final List<ScheduleTask> items = randomTasks(taskCount, random);
            final TravelMatrix matrix = randomBucketedMatrix(items, availability, random);
            final ScheduleProblem problem = new ScheduleProblem(availability, items,
                    noBlockedWindows(), matrix);

            final ScheduleSearchResult searched = engine.search(problem, SearchBudget.defaultBudget());
            final ScheduleScore exhaustive = bestByExhaustiveEnumeration(problem);

            if (exhaustive == null) {
                assertTrue(!searched.isFound(),
                        "search found a schedule where exhaustive enumeration found none");
                continue;
            }
            assertTrue(searched.isFound(),
                    "exhaustive enumeration found a schedule the pruned search missed");
            assertEquals(exhaustive, searched.getPlan().getScore(),
                    "pruning changed the answer on trial " + trial
                            + describe(problem, searched));
            compared++;
        }
        assertTrue(compared > 20, "expected a meaningful number of feasible trials, got " + compared);
    }

    @Test
    void prunedSearchMatchesExhaustiveSearchWithUnavailablePeriods() {
        final Random random = new Random(90210L);
        int feasible = 0;

        for (int trial = 0; trial < 50; trial++) {
            final TimeWindow availability = window(9, 21);
            final List<ScheduleTask> items = randomTasks(3 + random.nextInt(2), random);
            final TravelMatrix matrix = randomBucketedMatrix(items, availability, random);
            final int blockStart = 11 + random.nextInt(5);
            final List<TimeWindow> blocked = java.util.Arrays.asList(
                    new TimeWindow(at(blockStart, 0), at(blockStart + 1, 0)));
            final ScheduleProblem problem = new ScheduleProblem(availability, items, blocked, matrix);

            final ScheduleSearchResult searched = engine.search(problem, SearchBudget.defaultBudget());
            final ScheduleScore exhaustive = bestByExhaustiveEnumeration(problem);

            if (exhaustive == null) {
                assertTrue(!searched.isFound(),
                        "search found a schedule where exhaustive enumeration found none");
                continue;
            }
            assertTrue(searched.isFound(),
                    "exhaustive enumeration found a schedule the pruned search missed");
            assertEquals(exhaustive, searched.getPlan().getScore(),
                    "pruning changed the answer on trial " + trial
                            + describe(problem, searched));

            for (PlacedActivity placed : searched.getPlan().getPlacements()) {
                assertTrue(!placed.window().overlaps(blocked.get(0)),
                        "activity " + placed + " overlaps the unavailable period");
                if (placed.hasTravel()) {
                    assertTrue(!placed.travelWindow().overlaps(blocked.get(0)),
                            "travel into " + placed + " runs through the unavailable period");
                }
            }
            feasible++;
        }
        assertTrue(feasible > 15, "expected a meaningful number of feasible trials");
    }

    @Test
    void prunedSearchMatchesExhaustiveSearchWithTightWindows() {
        final Random random = new Random(4242L);
        for (int trial = 0; trial < 40; trial++) {
            final TimeWindow availability = window(9, 15);
            final List<ScheduleTask> items = randomTightTasks(4, random);
            final TravelMatrix matrix = randomBucketedMatrix(items, availability, random);
            final ScheduleProblem problem = new ScheduleProblem(availability, items,
                    noBlockedWindows(), matrix);

            final ScheduleSearchResult searched = engine.search(problem, SearchBudget.defaultBudget());
            final ScheduleScore exhaustive = bestByExhaustiveEnumeration(problem);

            if (exhaustive == null) {
                assertTrue(!searched.isFound());
            } else {
                assertTrue(searched.isFound());
                assertEquals(exhaustive, searched.getPlan().getScore());
            }
        }
    }

    /**
     * The same cross-check with travel switched out of the ranking.
     *
     * <p>This is the one that could have gone silently wrong. The incumbent bound adds the
     * cheapest travel still to come, and that is only a valid floor while the score charges
     * travel at all. If the bound had kept counting travel the score no longer counts, it
     * would exceed the true cost and prune the optimum — and nothing else in the suite
     * would have noticed, because the answer would still be a perfectly valid schedule,
     * just not the best one.</p>
     */
    @Test
    void prunedSearchStillMatchesExhaustiveSearchWhenTravelIsNotBeingMinimised() {
        final Random random = new Random(20260808L);
        int compared = 0;

        for (int trial = 0; trial < 60; trial++) {
            final TimeWindow availability = window(9, 21);
            final List<ScheduleTask> items = randomTasks(3 + random.nextInt(3), random);
            final TravelMatrix matrix = randomBucketedMatrix(items, availability, random);
            // Travel and gaps both switched off: only the tie-break separates the days.
            final SchedulingPreferences ignoringTravel = new SchedulingPreferences(
                    java.util.Collections.emptyList(), false,
                    use_case.autoschedule.PolicyContext.empty(), false, false);
            final ScheduleProblem problem = new ScheduleProblem(availability, items,
                    noBlockedWindows(), matrix, ignoringTravel);

            final ScheduleSearchResult searched = engine.search(problem, SearchBudget.defaultBudget());
            final ScheduleScore exhaustive = bestByExhaustiveEnumeration(problem);

            if (exhaustive == null) {
                assertTrue(!searched.isFound());
                continue;
            }
            assertTrue(searched.isFound(),
                    "exhaustive enumeration found a schedule the pruned search missed");
            assertEquals(exhaustive, searched.getPlan().getScore(),
                    "pruning changed the answer on trial " + trial);
            assertEquals(0, searched.getPlan().getScore().practicalCostMinutes(),
                    "with travel and gaps ignored there is nothing left to charge");
            compared++;
        }
        assertTrue(compared > 20, "expected feasible trials, got " + compared);
    }

    private List<ScheduleTask> randomTasks(int count, Random random) {
        final List<ScheduleTask> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int duration = 30 + random.nextInt(4) * 30;
            final int openHour = 9 + random.nextInt(3);
            final int closeHour = 17 + random.nextInt(5);
            items.add(task(String.valueOf((char) ('a' + i)), duration, i,
                    at(openHour, 0), at(closeHour, 0)));
        }
        return items;
    }

    private List<ScheduleTask> randomTightTasks(int count, Random random) {
        final List<ScheduleTask> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int duration = 45 + random.nextInt(3) * 15;
            final int openHour = 9 + random.nextInt(2);
            final int closeHour = 13 + random.nextInt(3);
            items.add(task(String.valueOf((char) ('a' + i)), duration, i,
                    at(openHour, 0), at(closeHour, 0)));
        }
        return items;
    }

    /** A matrix where each route genuinely differs between departure periods. */
    private TravelMatrix randomBucketedMatrix(List<ScheduleTask> items, TimeWindow availability,
                                              Random random) {
        final PeriodPlan plan = PeriodPlan.forRun(availability, true,
                items.size() * (items.size() - 1));
        final TravelMatrix.Builder builder = TravelMatrix.builder(plan);
        for (ScheduleTask from : items) {
            for (ScheduleTask to : items) {
                if (from.getEventId().equals(to.getEventId())) {
                    continue;
                }
                for (DeparturePeriod period : plan.activePeriods()) {
                    final int minutes = 5 + random.nextInt(40);
                    builder.put(from.getEventId(), to.getEventId(), period,
                            TravelEstimate.routed(minutes));
                }
            }
        }
        return builder.build();
    }

    /**
     * Enumerates every permutation, placing each activity at its earliest feasible time
     * with the same rules the engine uses, and returns the best score found.
     */
    private ScheduleScore bestByExhaustiveEnumeration(ScheduleProblem problem) {
        final List<ScheduleTask> items = new ArrayList<>(problem.getMovableTasks());
        final List<List<ScheduleTask>> permutations = new ArrayList<>();
        permute(items, new ArrayList<>(), permutations);

        ScheduleScore best = null;
        for (List<ScheduleTask> order : permutations) {
            final List<PlacedActivity> placements = placeInOrder(problem, order);
            if (placements == null) {
                continue;
            }
            final ScheduleScore score = ScheduleEngine.score(placements, problem.getPreferences());
            if (best == null || score.compareTo(best) < 0) {
                best = score;
            }
        }
        return best;
    }

    private void permute(List<ScheduleTask> remaining, List<ScheduleTask> prefix,
                         List<List<ScheduleTask>> out) {
        if (remaining.isEmpty()) {
            out.add(new ArrayList<>(prefix));
            return;
        }
        for (int i = 0; i < remaining.size(); i++) {
            final List<ScheduleTask> rest = new ArrayList<>(remaining);
            final ScheduleTask chosen = rest.remove(i);
            prefix.add(chosen);
            permute(rest, prefix, out);
            prefix.remove(prefix.size() - 1);
        }
    }

    /**
     * An independent re-implementation of the placement rules, written straight through
     * rather than reusing the production placer, so that a mistake in the placer cannot
     * hide by being made identically on both sides of the comparison.
     */
    private List<PlacedActivity> placeInOrder(ScheduleProblem problem, List<ScheduleTask> order) {
        final List<PlacedActivity> placements = new ArrayList<>();
        LocalTime cursor = problem.getAvailability().getStart();
        ScheduleTask previous = null;
        final List<TimeWindow> blocked = problem.getUnavailableWindows();

        for (ScheduleTask task : order) {
            IndependentLeg leg = previous == null
                    ? new IndependentLeg(null, cursor, 0)
                    : earliestLeg(problem, previous, task, cursor, blocked);
            if (leg == null) {
                return null;
            }
            LocalTime arrival = leg.arrival;
            LocalTime start = earliestStart(problem, task, arrival, blocked);
            if (start == null) {
                return null;
            }

            // Once a block pushes the activity to its far side, an earlier journey is not
            // retained. Re-plan from the end of the crossed block and move the unlocked
            // destination with that journey, independently mirroring the public rule.
            if (previous != null) {
                for (int guard = 0; guard <= blocked.size(); guard++) {
                    final LocalTime resume = blockedWaitEnd(arrival, start, blocked);
                    if (resume == null) {
                        break;
                    }
                    leg = earliestLeg(problem, previous, task, resume, blocked);
                    if (leg == null) {
                        return null;
                    }
                    arrival = leg.arrival;
                    start = earliestStart(problem, task, arrival, blocked);
                    if (start == null) {
                        return null;
                    }
                }
            }
            final LocalTime end = start.plusMinutes(task.getDurationMinutes());

            if (end.isAfter(task.getClosingTime()) || end.isAfter(problem.getAvailability().getEnd())
                    || !end.isAfter(start) || overlapsAny(start, end, blocked)) {
                return null;
            }

            int idle = Math.max(0,
                    (start.toSecondOfDay() - cursor.toSecondOfDay()) / 60 - leg.minutes);
            // avoidable waiting is still measured from the earliest arrival: departing later
            // relocates dead time, it does not remove it.
            final LocalTime avoidableFrom = arrival.isBefore(task.getOpeningTime())
                    ? task.getOpeningTime() : arrival;
            int avoidable = 0;
            if (start.isAfter(avoidableFrom)) {
                avoidable = (start.toSecondOfDay() - avoidableFrom.toSecondOfDay()) / 60
                        - blockedMinutes(avoidableFrom, start, blocked);
                avoidable = Math.max(0, avoidable);
            }

            // The journey is then slid as late as it will go while still arriving by the
            // start, worked out here from scratch rather than by calling the planner, so the
            // two sides of this comparison stay genuinely independent.
            if (previous != null) {
                for (DeparturePeriod period : DeparturePeriod.values()) {
                    final int minutes = problem.getTravel().estimateAt(previous.getEventId(),
                            task.getEventId(), period.getStart()).getMinutes();
                    final LocalTime candidate = start.minusMinutes(minutes);
                    final boolean sameCost = problem.getTravel().estimateAt(previous.getEventId(),
                            task.getEventId(), candidate).getMinutes() == minutes;
                    if (minutes > 0 && !candidate.isBefore(start)) {
                        continue;
                    }
                    if (candidate.isBefore(cursor) || !candidate.isAfter(leg.departure)
                            || !sameCost) {
                        continue;
                    }
                    if (minutes > 0 && overlapsAny(candidate, candidate.plusMinutes(minutes),
                            blocked)) {
                        continue;
                    }
                    leg = new IndependentLeg(candidate, start, minutes);
                }
                idle = Math.max(0,
                        (start.toSecondOfDay() - cursor.toSecondOfDay()) / 60 - leg.minutes);
            }

            placements.add(new PlacedActivity(task, start, end,
                    leg.departure, leg.minutes, idle, avoidable));
            cursor = end;
            previous = task;
        }
        return placements;
    }

    private IndependentLeg earliestLeg(ScheduleProblem problem, ScheduleTask previous,
                                       ScheduleTask task, LocalTime cursor,
                                       List<TimeWindow> blocked) {
        IndependentLeg best = null;
        for (LocalTime option : departureOptions(cursor, blocked)) {
            final int minutes = problem.getTravel()
                    .estimateAt(previous.getEventId(), task.getEventId(), option).getMinutes();
            final LocalTime landed = option.plusMinutes(minutes);
            if (minutes > 0 && overlapsAny(option, landed, blocked)) {
                continue;
            }
            if (landed.isAfter(problem.getAvailability().getEnd())) {
                continue;
            }
            if (best == null || landed.isBefore(best.arrival)) {
                best = new IndependentLeg(option, landed, minutes);
            }
        }
        return best;
    }

    private LocalTime earliestStart(ScheduleProblem problem, ScheduleTask task,
                                    LocalTime arrival, List<TimeWindow> blocked) {
        LocalTime start = arrival.isBefore(task.getOpeningTime())
                ? task.getOpeningTime() : arrival;
        if (start.isBefore(problem.getAvailability().getStart())) {
            start = problem.getAvailability().getStart();
        }
        LocalTime end = start.plusMinutes(task.getDurationMinutes());
        for (int guard = 0; guard <= blocked.size() + 1; guard++) {
            LocalTime pushed = start;
            for (TimeWindow window : blocked) {
                if (window.overlaps(new TimeWindow(start, end))) {
                    pushed = pushed.isAfter(window.getEnd()) ? pushed : window.getEnd();
                }
            }
            if (pushed.equals(start)) {
                return start;
            }
            start = pushed;
            end = start.plusMinutes(task.getDurationMinutes());
        }
        return null;
    }

    private LocalTime blockedWaitEnd(LocalTime arrival, LocalTime start,
                                     List<TimeWindow> blocked) {
        if (!start.isAfter(arrival)) {
            return null;
        }
        LocalTime resume = null;
        final TimeWindow wait = new TimeWindow(arrival, start);
        for (TimeWindow window : blocked) {
            if (window.overlaps(wait) && (resume == null || window.getEnd().isAfter(resume))) {
                resume = window.getEnd();
            }
        }
        return resume;
    }

    private static final class IndependentLeg {
        private final LocalTime departure;
        private final LocalTime arrival;
        private final int minutes;

        private IndependentLeg(LocalTime departure, LocalTime arrival, int minutes) {
            this.departure = departure;
            this.arrival = arrival;
            this.minutes = minutes;
        }
    }

    private List<LocalTime> departureOptions(LocalTime cursor, List<TimeWindow> blocked) {
        final List<LocalTime> options = new ArrayList<>();
        options.add(cursor);
        for (TimeWindow window : blocked) {
            if (window.getEnd().isAfter(cursor) && !options.contains(window.getEnd())) {
                options.add(window.getEnd());
            }
        }
        java.util.Collections.sort(options);
        return options;
    }

    private boolean overlapsAny(LocalTime start, LocalTime end, List<TimeWindow> blocked) {
        if (!end.isAfter(start)) {
            return false;
        }
        final TimeWindow candidate = new TimeWindow(start, end);
        for (TimeWindow window : blocked) {
            if (window.overlaps(candidate)) {
                return true;
            }
        }
        return false;
    }

    private int blockedMinutes(LocalTime from, LocalTime to, List<TimeWindow> blocked) {
        int total = 0;
        LocalTime covered = from;
        final List<TimeWindow> sorted = new ArrayList<>(blocked);
        java.util.Collections.sort(sorted, (l, r) -> l.getStart().compareTo(r.getStart()));
        for (TimeWindow window : sorted) {
            final LocalTime overlapStart = window.getStart().isAfter(covered) ? window.getStart() : covered;
            final LocalTime overlapEnd = window.getEnd().isBefore(to) ? window.getEnd() : to;
            if (overlapEnd.isAfter(overlapStart)) {
                total += (overlapEnd.toSecondOfDay() - overlapStart.toSecondOfDay()) / 60;
                covered = overlapEnd;
            }
        }
        return total;
    }

    private String describe(ScheduleProblem problem, ScheduleSearchResult result) {
        final StringBuilder text = new StringBuilder("\nblocked=")
                .append(problem.getUnavailableWindows()).append('\n');
        for (ScheduleTask task : problem.allTasks()) {
            text.append(task.getEventId()).append(" duration=")
                    .append(task.getDurationMinutes()).append(" hours=")
                    .append(task.getOpeningWindows()).append('\n');
        }
        for (ScheduleTask from : problem.allTasks()) {
            for (ScheduleTask to : problem.allTasks()) {
                if (from == to) {
                    continue;
                }
                text.append(from.getEventId()).append("->").append(to.getEventId())
                        .append(':');
                for (DeparturePeriod period : DeparturePeriod.values()) {
                    text.append(' ').append(period).append('=')
                            .append(problem.getTravel().estimateAt(from.getEventId(),
                                    to.getEventId(), period.getStart()).getMinutes());
                }
                text.append('\n');
            }
        }
        if (result.isFound()) {
            for (PlacedActivity placed : result.getPlan().getPlacements()) {
                text.append("engine ").append(placed).append(" travel=")
                        .append(placed.getTravelMinutesBefore()).append(" depart=")
                        .append(placed.getTravelDeparture()).append(" avoidable=")
                        .append(placed.getAvoidableIdleMinutes()).append('\n');
            }
        }
        return text.toString();
    }
}
