package use_case.autoschedule.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static use_case.autoschedule.ProblemFixtures.at;
import static use_case.autoschedule.ProblemFixtures.noBlockedWindows;
import static use_case.autoschedule.ProblemFixtures.task;
import static use_case.autoschedule.ProblemFixtures.tasks;
import static use_case.autoschedule.ProblemFixtures.window;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import use_case.autoschedule.DeparturePeriod;
import use_case.autoschedule.PeriodPlan;
import use_case.autoschedule.ScheduleProblem;
import use_case.autoschedule.ScheduleTask;
import use_case.autoschedule.TimeWindow;
import use_case.autoschedule.TravelEstimate;
import use_case.autoschedule.TravelMatrix;

/**
 * Proves that travel really does depend on when a leg is taken, and that the schedule
 * changes accordingly. These are the tests that would fail if the engine quietly went
 * back to reading a single fixed matrix.
 */
class TimeDependentSchedulingTest {

    private final ScheduleEngine engine = new ScheduleEngine();

    /** Builds a matrix by naming a duration for each route in each period. */
    private static final class MatrixBuilder {
        private final TimeWindow availability;
        private final PeriodPlan plan;
        private final TravelMatrix.Builder builder;

        MatrixBuilder(TimeWindow availability, List<ScheduleTask> items) {
            this.availability = availability;
            this.plan = PeriodPlan.forRun(availability, true, items.size() * (items.size() - 1));
            this.builder = TravelMatrix.builder(plan);
            for (ScheduleTask from : items) {
                for (ScheduleTask to : items) {
                    if (!from.getEventId().equals(to.getEventId())) {
                        for (DeparturePeriod period : plan.activePeriods()) {
                            builder.put(from.getEventId(), to.getEventId(), period,
                                    TravelEstimate.routed(30));
                        }
                    }
                }
            }
        }

        MatrixBuilder leg(String from, String to, DeparturePeriod period, int minutes) {
            builder.put(from, to, period, TravelEstimate.routed(minutes));
            return this;
        }

        MatrixBuilder leg(String from, String to, int minutes) {
            for (DeparturePeriod period : plan.activePeriods()) {
                builder.put(from, to, period, TravelEstimate.routed(minutes));
            }
            return this;
        }

        TravelMatrix build() {
            return builder.build();
        }
    }

    @Test
    void readsTheBucketForTheActualDepartureTimeNotTheStartOfTheDay() {
        // "long" runs 09:00-12:00, so the leg out of it departs during MIDDAY, not EARLY.
        final List<ScheduleTask> items = tasks(
                task("long", 180, 0, at(9, 0), at(21, 0)),
                task("next", 60, 1, at(9, 0), at(21, 0)));
        final TravelMatrix matrix = new MatrixBuilder(window(9, 21), items)
                .leg("long", "next", DeparturePeriod.EARLY, 90)
                .leg("long", "next", DeparturePeriod.MIDDAY, 5)
                .leg("next", "long", 200)
                .build();
        final ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), matrix);

        final ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertTrue(result.isFound());
        assertEquals(Arrays.asList("long", "next"), result.getPlan().orderedEventIds());
        assertEquals(5, result.getPlan().getPlacements().get(1).getTravelMinutesBefore(),
                "the leg departs at 12:00, so the midday estimate applies");
        assertEquals(at(12, 5), result.getPlan().getPlacements().get(1).getStart());
    }

    @Test
    void choosesTheOrderThatAvoidsTheExpensivePeriod() {
        final List<ScheduleTask> items = tasks(
                task("a", 180, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        // Leaving a at midday is cheap; leaving b in the morning is expensive.
        final TravelMatrix matrix = new MatrixBuilder(window(9, 21), items)
                .leg("a", "b", DeparturePeriod.MIDDAY, 5)
                .leg("a", "b", DeparturePeriod.EARLY, 5)
                .leg("b", "a", DeparturePeriod.EARLY, 120)
                .leg("b", "a", DeparturePeriod.MIDDAY, 120)
                .build();
        final ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), matrix);

        final ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertEquals(Arrays.asList("a", "b"), result.getPlan().orderedEventIds());
        assertEquals(5, result.getPlan().totalTravelMinutes());
    }

    @Test
    void theOppositeBucketValuesProduceTheOppositeOrder() {
        final List<ScheduleTask> items = tasks(
                task("a", 180, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)));
        // Same problem shape, mirrored costs: now going b first is the cheap option.
        final TravelMatrix matrix = new MatrixBuilder(window(9, 21), items)
                .leg("a", "b", DeparturePeriod.MIDDAY, 120)
                .leg("a", "b", DeparturePeriod.EARLY, 120)
                .leg("b", "a", DeparturePeriod.EARLY, 5)
                .leg("b", "a", DeparturePeriod.MIDDAY, 5)
                .build();
        final ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), matrix);

        final ScheduleSearchResult result = engine.search(problem, SearchBudget.defaultBudget());

        assertEquals(Arrays.asList("b", "a"), result.getPlan().orderedEventIds());
        assertEquals(5, result.getPlan().totalTravelMinutes());
    }

    @Test
    void searchBeatsTheGreedyIncumbentWhenUrgencyIsMisleading() {
        // Greedy visits "urgent" first because it closes soonest, but every leg out of it
        // is slow; the good plan visits it last, still inside its window.
        final List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)),
                task("urgent", 60, 2, at(9, 0), at(14, 0)));
        final TravelMatrix matrix = new MatrixBuilder(window(9, 21), items)
                .leg("urgent", "a", 60)
                .leg("urgent", "b", 60)
                .leg("a", "b", 5)
                .leg("b", "a", 5)
                .leg("a", "urgent", 5)
                .leg("b", "urgent", 5)
                .build();
        final ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), matrix);

        final ScheduleSearchResult searched = engine.search(problem, SearchBudget.defaultBudget());
        final int greedyTravel = new GreedyPlanner()
                .plan(problem, problem.getLockedTasks(),
                        new ActivityPlacer(java.util.Collections.emptyList()))
                .totalTravelMinutes();

        assertTrue(searched.isFound());
        assertEquals("urgent", searched.getPlan().orderedEventIds().get(2),
                "the urgent venue is visited last, which is still inside its window");
        assertEquals(10, searched.getPlan().totalTravelMinutes());
        assertTrue(searched.getPlan().totalTravelMinutes() < greedyTravel,
                "the search must strictly improve on greedy here (greedy=" + greedyTravel + ")");
    }

    @Test
    void theSameProblemAlwaysProducesTheSameSchedule() {
        final List<ScheduleTask> items = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)),
                task("c", 60, 2, at(9, 0), at(21, 0)),
                task("d", 60, 3, at(9, 0), at(21, 0)));
        final TravelMatrix matrix = new MatrixBuilder(window(9, 21), items)
                .leg("a", "b", 10).leg("b", "c", 10).leg("c", "d", 10)
                .leg("d", "a", 10).leg("a", "c", 20).leg("c", "a", 20)
                .build();
        final ScheduleProblem problem = new ScheduleProblem(window(9, 21), items,
                noBlockedWindows(), matrix);

        final ScheduleSearchResult first = engine.search(problem, SearchBudget.defaultBudget());
        final ScheduleSearchResult second = engine.search(problem, SearchBudget.defaultBudget());

        assertEquals(first.getPlan().orderedEventIds(), second.getPlan().orderedEventIds());
        assertEquals(first.getPlan().getScore(), second.getPlan().getScore());
        assertEquals(first.getNodesExplored(), second.getNodesExplored(),
                "the same input must explore the same tree");
    }

    @Test
    void theInputOrderOfActivitiesDoesNotChangeTheResult() {
        final List<ScheduleTask> forward = tasks(
                task("a", 60, 0, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)),
                task("c", 60, 2, at(9, 0), at(21, 0)));
        final List<ScheduleTask> reversed = tasks(
                task("c", 60, 2, at(9, 0), at(21, 0)),
                task("b", 60, 1, at(9, 0), at(21, 0)),
                task("a", 60, 0, at(9, 0), at(21, 0)));

        final TravelMatrix forwardMatrix = new MatrixBuilder(window(9, 21), forward)
                .leg("a", "b", 8).leg("b", "c", 8).leg("a", "c", 25)
                .leg("c", "b", 8).leg("b", "a", 8).leg("c", "a", 25).build();
        final TravelMatrix reversedMatrix = new MatrixBuilder(window(9, 21), reversed)
                .leg("a", "b", 8).leg("b", "c", 8).leg("a", "c", 25)
                .leg("c", "b", 8).leg("b", "a", 8).leg("c", "a", 25).build();

        final ScheduleSearchResult first = engine.search(new ScheduleProblem(window(9, 21), forward,
                noBlockedWindows(), forwardMatrix), SearchBudget.defaultBudget());
        final ScheduleSearchResult second = engine.search(new ScheduleProblem(window(9, 21), reversed,
                noBlockedWindows(), reversedMatrix), SearchBudget.defaultBudget());

        assertEquals(first.getPlan().orderedEventIds(), second.getPlan().orderedEventIds());
        assertEquals(first.getPlan().getScore(), second.getPlan().getScore());
    }
}
