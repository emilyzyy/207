package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import entity.valueobjects.TransportationMode;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.engine.ScheduleSearchResult;
import use_case.autoschedule.engine.SearchBudget;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.SoftPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import use_case.autoschedule.testdoubles.FakeTravelTimeEstimator;

/**
 * Evidence about how the feature behaves as a day gets bigger.
 *
 * <p>Everything here runs against a fake travel provider, so the numbers are repeatable
 * and no network is involved. They are project evidence measured on one machine, not a
 * performance guarantee: the useful parts are the shapes and the limits, not the
 * milliseconds.</p>
 */
class SchedulingBenchmarkTest {

    private static final List<SoftPolicy> BUILT_IN = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    private static List<ScheduleTask> dayOf(int activityCount) {
        final List<ScheduleTask> tasks = new ArrayList<>();
        for (int i = 0; i < activityCount; i++) {
            // Staggered opening hours so the day is genuinely constrained, not trivial.
            final int openHour = 9 + (i % 3);
            final int closeHour = 18 + (i % 4);
            tasks.add(ProblemFixtures.task("a" + i, 45, i,
                    ProblemFixtures.at(openHour, 0), ProblemFixtures.at(closeHour, 0)));
        }
        return tasks;
    }

    private static ScheduleProblem problemFor(int activityCount, FakeTravelTimeEstimator estimator) {
        final List<ScheduleTask> tasks = dayOf(activityCount);
        final TimeWindow availability = ProblemFixtures.window(9, 21);
        final TravelMatrix matrix = new TravelMatrixPrefetcher(estimator).prefetch(
                tasks, TransportationMode.TRANSIT, ProblemFixtures.TRIP_DATE, availability);
        return new ScheduleProblem(availability, tasks, ProblemFixtures.noBlockedWindows(), matrix,
                SchedulingPreferences.builtIn(BUILT_IN, true, PolicyContext.empty()));
    }

    @Test
    void benchmarkAcrossRepresentativeDayPlans() {
        System.out.println();
        System.out.println("Autoschedule benchmark (fake travel provider, no network)");
        System.out.println("  java " + System.getProperty("java.version")
                + " on " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println(String.format("  %-11s %8s %8s %9s %9s %8s %9s %-10s",
                "activities", "pairs", "buckets", "prefetch", "nodes", "ms", "exhausted",
                "outcome"));

        for (int size : new int[] {5, 8, 12, 15}) {
            final FakeTravelTimeEstimator estimator =
                    new FakeTravelTimeEstimator().timeSensitive(true).defaultMinutes(12);
            final ScheduleProblem problem = problemFor(size, estimator);
            final int prefetchCalls = estimator.callCount();
            final int buckets = problem.getTravel().getPeriods().size();

            final long started = System.nanoTime();
            final ScheduleSearchResult result =
                    new ScheduleEngine().search(problem, SearchBudget.defaultBudget());
            final long millis = (System.nanoTime() - started) / 1_000_000;

            System.out.println(String.format("  %-11d %8d %8d %9d %9d %8d %9s %-10s",
                    size, size * (size - 1), buckets, prefetchCalls, result.getNodesExplored(),
                    millis, result.isCompletedWithinLimit() ? "no" : "yes",
                    result.isFound() ? "scheduled" : "conflict"));

            assertNotNull(result, "every representative day should produce an outcome");
            assertTrue(problem.getTravel().getPeriods()
                            .withinPrefetchBudget(size * (size - 1)),
                    "prefetch for " + size + " activities broke the documented contract: "
                            + "either within the ceiling, or collapsed to one matrix");
            if (buckets > 1) {
                assertTrue(prefetchCalls <= PeriodPlan.MAX_PREFETCH_CALLS,
                        "while more than one period is fetched the ceiling must hold");
            }
        }
        System.out.println();
    }

    @Test
    void theSearchNeverContactsTheTravelProvider() {
        final FakeTravelTimeEstimator estimator =
                new FakeTravelTimeEstimator().timeSensitive(true).defaultMinutes(12);
        final ScheduleProblem problem = problemFor(8, estimator);
        final int callsAfterPrefetch = estimator.callCount();

        new ScheduleEngine().search(problem, SearchBudget.defaultBudget());

        assertEquals(callsAfterPrefetch, estimator.callCount(),
                "a network call inside the recursion would make the search neither "
                        + "deterministic nor affordable");
    }

    @Test
    void largerDaysDegradeTheirDepartureBucketsAsDesigned() {
        final FakeTravelTimeEstimator small =
                new FakeTravelTimeEstimator().timeSensitive(true).defaultMinutes(12);
        final FakeTravelTimeEstimator large =
                new FakeTravelTimeEstimator().timeSensitive(true).defaultMinutes(12);

        final int smallBuckets = problemFor(5, small).getTravel().getPeriods().size();
        final int largeBuckets = problemFor(15, large).getTravel().getPeriods().size();

        System.out.println("[benchmark] buckets: 5 activities -> " + smallBuckets
                + ", 15 activities -> " + largeBuckets);
        assertTrue(largeBuckets <= smallBuckets,
                "a bigger day must not fetch more finely; the budget forces it coarser");
        assertEquals(1, largeBuckets,
                "a 15-activity day collapses to the single irreducible matrix");
        assertEquals(15 * 14, large.callCount(),
                "that matrix is the floor: one request per directed pair, nothing more");
    }

    @Test
    void repeatedRunsOfTheSameDayGiveTheSameAnswer() {
        final FakeTravelTimeEstimator first =
                new FakeTravelTimeEstimator().timeSensitive(true).defaultMinutes(12);
        final FakeTravelTimeEstimator second =
                new FakeTravelTimeEstimator().timeSensitive(true).defaultMinutes(12);

        final ScheduleSearchResult one =
                new ScheduleEngine().search(problemFor(8, first), SearchBudget.defaultBudget());
        final ScheduleSearchResult two =
                new ScheduleEngine().search(problemFor(8, second), SearchBudget.defaultBudget());

        assertEquals(one.getPlan().orderedEventIds(), two.getPlan().orderedEventIds());
        assertEquals(one.getPlan().getScore(), two.getPlan().getScore());
        assertEquals(one.getNodesExplored(), two.getNodesExplored(),
                "the same day should explore the same tree every time");
    }

    @Test
    void aWalkingDayCostsExactlyOneMatrix() {
        final FakeTravelTimeEstimator walking =
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(12);

        problemFor(8, walking);

        assertEquals(8 * 7, walking.callCount(),
                "walking has no departure-time input, so buckets would fetch the same "
                        + "number repeatedly");
    }
}
