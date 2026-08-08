package closeai.application.autoschedule.engine;

import static closeai.application.autoschedule.ProblemFixtures.at;
import static closeai.application.autoschedule.ProblemFixtures.noBlockedWindows;
import static closeai.application.autoschedule.ProblemFixtures.task;
import static closeai.application.autoschedule.ProblemFixtures.window;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.application.autoschedule.DeparturePeriod;
import closeai.application.autoschedule.PeriodPlan;
import closeai.application.autoschedule.PlacedActivity;
import closeai.application.autoschedule.ScheduleProblem;
import closeai.application.autoschedule.SchedulingPreferences;
import closeai.application.autoschedule.ScheduleScore;
import closeai.application.autoschedule.ScheduleTask;
import closeai.application.autoschedule.TimeWindow;
import closeai.application.autoschedule.TravelEstimate;
import closeai.application.autoschedule.TravelMatrix;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

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
        Random random = new Random(20260805L);
        int compared = 0;

        for (int trial = 0; trial < 60; trial++) {
            int taskCount = 3 + random.nextInt(3);
            TimeWindow availability = window(9, 21);
            List<ScheduleTask> items = randomTasks(taskCount, random);
            TravelMatrix matrix = randomBucketedMatrix(items, availability, random);
            ScheduleProblem problem = new ScheduleProblem(availability, items,
                    noBlockedWindows(), matrix);

            ScheduleSearchResult searched = engine.search(problem, SearchBudget.defaultBudget());
            ScheduleScore exhaustive = bestByExhaustiveEnumeration(problem);

            if (exhaustive == null) {
                assertTrue(!searched.isFound(),
                        "search found a schedule where exhaustive enumeration found none");
                continue;
            }
            assertTrue(searched.isFound(),
                    "exhaustive enumeration found a schedule the pruned search missed");
            assertEquals(exhaustive, searched.getPlan().getScore(),
                    "pruning changed the answer on trial " + trial);
            compared++;
        }
        assertTrue(compared > 20, "expected a meaningful number of feasible trials, got " + compared);
    }

    @Test
    void prunedSearchMatchesExhaustiveSearchWithUnavailablePeriods() {
        Random random = new Random(90210L);
        int feasible = 0;

        for (int trial = 0; trial < 50; trial++) {
            TimeWindow availability = window(9, 21);
            List<ScheduleTask> items = randomTasks(3 + random.nextInt(2), random);
            TravelMatrix matrix = randomBucketedMatrix(items, availability, random);
            int blockStart = 11 + random.nextInt(5);
            List<TimeWindow> blocked = java.util.Arrays.asList(
                    new TimeWindow(at(blockStart, 0), at(blockStart + 1, 0)));
            ScheduleProblem problem = new ScheduleProblem(availability, items, blocked, matrix);

            ScheduleSearchResult searched = engine.search(problem, SearchBudget.defaultBudget());
            ScheduleScore exhaustive = bestByExhaustiveEnumeration(problem);

            if (exhaustive == null) {
                assertTrue(!searched.isFound(),
                        "search found a schedule where exhaustive enumeration found none");
                continue;
            }
            assertTrue(searched.isFound(),
                    "exhaustive enumeration found a schedule the pruned search missed");
            assertEquals(exhaustive, searched.getPlan().getScore(),
                    "pruning changed the answer on trial " + trial);

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
        Random random = new Random(4242L);
        for (int trial = 0; trial < 40; trial++) {
            TimeWindow availability = window(9, 15);
            List<ScheduleTask> items = randomTightTasks(4, random);
            TravelMatrix matrix = randomBucketedMatrix(items, availability, random);
            ScheduleProblem problem = new ScheduleProblem(availability, items,
                    noBlockedWindows(), matrix);

            ScheduleSearchResult searched = engine.search(problem, SearchBudget.defaultBudget());
            ScheduleScore exhaustive = bestByExhaustiveEnumeration(problem);

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
        Random random = new Random(20260808L);
        int compared = 0;

        for (int trial = 0; trial < 60; trial++) {
            TimeWindow availability = window(9, 21);
            List<ScheduleTask> items = randomTasks(3 + random.nextInt(3), random);
            TravelMatrix matrix = randomBucketedMatrix(items, availability, random);
            // Travel and gaps both switched off: only the tie-break separates the days.
            SchedulingPreferences ignoringTravel = new SchedulingPreferences(
                    java.util.Collections.emptyList(), false,
                    closeai.application.autoschedule.PolicyContext.empty(), false, false);
            ScheduleProblem problem = new ScheduleProblem(availability, items,
                    noBlockedWindows(), matrix, ignoringTravel);

            ScheduleSearchResult searched = engine.search(problem, SearchBudget.defaultBudget());
            ScheduleScore exhaustive = bestByExhaustiveEnumeration(problem);

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
        List<ScheduleTask> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int duration = 30 + random.nextInt(4) * 30;
            int openHour = 9 + random.nextInt(3);
            int closeHour = 17 + random.nextInt(5);
            items.add(task(String.valueOf((char) ('a' + i)), duration, i,
                    at(openHour, 0), at(closeHour, 0)));
        }
        return items;
    }

    private List<ScheduleTask> randomTightTasks(int count, Random random) {
        List<ScheduleTask> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int duration = 45 + random.nextInt(3) * 15;
            int openHour = 9 + random.nextInt(2);
            int closeHour = 13 + random.nextInt(3);
            items.add(task(String.valueOf((char) ('a' + i)), duration, i,
                    at(openHour, 0), at(closeHour, 0)));
        }
        return items;
    }

    /** A matrix where each route genuinely differs between departure periods. */
    private TravelMatrix randomBucketedMatrix(List<ScheduleTask> items, TimeWindow availability,
                                              Random random) {
        PeriodPlan plan = PeriodPlan.forRun(availability, true,
                items.size() * (items.size() - 1));
        TravelMatrix.Builder builder = TravelMatrix.builder(plan);
        for (ScheduleTask from : items) {
            for (ScheduleTask to : items) {
                if (from.getEventId().equals(to.getEventId())) {
                    continue;
                }
                for (DeparturePeriod period : plan.activePeriods()) {
                    int minutes = 5 + random.nextInt(40);
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
        List<ScheduleTask> items = new ArrayList<>(problem.getMovableTasks());
        List<List<ScheduleTask>> permutations = new ArrayList<>();
        permute(items, new ArrayList<>(), permutations);

        ScheduleScore best = null;
        for (List<ScheduleTask> order : permutations) {
            List<PlacedActivity> placements = placeInOrder(problem, order);
            if (placements == null) {
                continue;
            }
            ScheduleScore score = ScheduleEngine.score(placements, problem.getPreferences());
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
            List<ScheduleTask> rest = new ArrayList<>(remaining);
            ScheduleTask chosen = rest.remove(i);
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
        List<PlacedActivity> placements = new ArrayList<>();
        LocalTime cursor = problem.getAvailability().getStart();
        ScheduleTask previous = null;
        List<TimeWindow> blocked = problem.getUnavailableWindows();

        for (ScheduleTask task : order) {
            LocalTime departure = null;
            int travel = 0;
            LocalTime arrival = cursor;

            if (previous != null) {
                LocalTime bestArrival = null;
                for (LocalTime option : departureOptions(cursor, blocked)) {
                    int minutes = problem.getTravel()
                            .estimateAt(previous.getEventId(), task.getEventId(), option).getMinutes();
                    LocalTime landed = option.plusMinutes(minutes);
                    if (minutes > 0 && overlapsAny(option, landed, blocked)) {
                        continue;
                    }
                    if (landed.isAfter(problem.getAvailability().getEnd())) {
                        continue;
                    }
                    if (bestArrival == null || landed.isBefore(bestArrival)) {
                        bestArrival = landed;
                        departure = option;
                        travel = minutes;
                    }
                }
                if (bestArrival == null) {
                    return null;
                }
                arrival = bestArrival;
            }

            LocalTime start = arrival.isBefore(task.getOpeningTime()) ? task.getOpeningTime() : arrival;
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
                    break;
                }
                start = pushed;
                end = start.plusMinutes(task.getDurationMinutes());
            }

            if (end.isAfter(task.getClosingTime()) || end.isAfter(problem.getAvailability().getEnd())
                    || !end.isAfter(start) || overlapsAny(start, end, blocked)) {
                return null;
            }

            int idle = Math.max(0,
                    (start.toSecondOfDay() - cursor.toSecondOfDay()) / 60 - travel);
            LocalTime avoidableFrom = arrival.isBefore(task.getOpeningTime())
                    ? task.getOpeningTime() : arrival;
            int avoidable = 0;
            if (start.isAfter(avoidableFrom)) {
                avoidable = (start.toSecondOfDay() - avoidableFrom.toSecondOfDay()) / 60
                        - blockedMinutes(avoidableFrom, start, blocked);
                avoidable = Math.max(0, avoidable);
            }

            placements.add(new PlacedActivity(task, start, end, departure, travel, idle, avoidable));
            cursor = end;
            previous = task;
        }
        return placements;
    }

    private List<LocalTime> departureOptions(LocalTime cursor, List<TimeWindow> blocked) {
        List<LocalTime> options = new ArrayList<>();
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
        TimeWindow candidate = new TimeWindow(start, end);
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
        List<TimeWindow> sorted = new ArrayList<>(blocked);
        java.util.Collections.sort(sorted, (l, r) -> l.getStart().compareTo(r.getStart()));
        for (TimeWindow window : sorted) {
            LocalTime overlapStart = window.getStart().isAfter(covered) ? window.getStart() : covered;
            LocalTime overlapEnd = window.getEnd().isBefore(to) ? window.getEnd() : to;
            if (overlapEnd.isAfter(overlapStart)) {
                total += (overlapEnd.toSecondOfDay() - overlapStart.toSecondOfDay()) / 60;
                covered = overlapEnd;
            }
        }
        return total;
    }
}
