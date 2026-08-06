package closeai.application.autoschedule.engine;

import static closeai.application.autoschedule.ProblemFixtures.at;
import static closeai.application.autoschedule.ProblemFixtures.noBlockedWindows;
import static closeai.application.autoschedule.ProblemFixtures.task;
import static closeai.application.autoschedule.ProblemFixtures.tasks;
import static closeai.application.autoschedule.ProblemFixtures.window;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import closeai.application.autoschedule.DeparturePeriod;
import closeai.application.autoschedule.PeriodPlan;
import closeai.application.autoschedule.PlacedActivity;
import closeai.application.autoschedule.ScheduleProblem;
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
            ScheduleScore score = ScheduleEngine.score(placements);
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

    private List<PlacedActivity> placeInOrder(ScheduleProblem problem, List<ScheduleTask> order) {
        List<PlacedActivity> placements = new ArrayList<>();
        LocalTime cursor = problem.getAvailability().getStart();
        ScheduleTask previous = null;

        for (ScheduleTask task : order) {
            int travel = previous == null ? 0
                    : problem.getTravel()
                            .estimateAt(previous.getEventId(), task.getEventId(), cursor).getMinutes();
            LocalTime arrival = cursor.plusMinutes(travel);
            LocalTime start = arrival.isBefore(task.getOpeningTime()) ? task.getOpeningTime() : arrival;
            if (start.isBefore(problem.getAvailability().getStart())) {
                start = problem.getAvailability().getStart();
            }
            LocalTime end = start.plusMinutes(task.getDurationMinutes());
            if (end.isAfter(task.getClosingTime()) || end.isAfter(problem.getAvailability().getEnd())
                    || !end.isAfter(start)) {
                return null;
            }
            int idle = (start.toSecondOfDay() - arrival.toSecondOfDay()) / 60;
            int unavoidable = arrival.isBefore(task.getOpeningTime())
                    ? (Math.min(task.getOpeningTime().toSecondOfDay(), start.toSecondOfDay())
                        - arrival.toSecondOfDay()) / 60
                    : 0;
            placements.add(new PlacedActivity(task, start, end, travel, idle, unavoidable));
            cursor = end;
            previous = task;
        }
        return placements;
    }
}
