package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import entity.valueobjects.TransportationMode;
import use_case.autoschedule.testdoubles.FakeTravelTimeEstimator;

class TravelMatrixPrefetcherTest {

    private static List<ScheduleTask> threeTasks() {
        return ProblemFixtures.tasks(
                ProblemFixtures.task("a", 60, 0, ProblemFixtures.at(9, 0), ProblemFixtures.at(21, 0)),
                ProblemFixtures.task("b", 60, 1, ProblemFixtures.at(9, 0), ProblemFixtures.at(21, 0)),
                ProblemFixtures.task("c", 60, 2, ProblemFixtures.at(9, 0), ProblemFixtures.at(21, 0)));
    }

    @Test
    void requestsExactlyPairsTimesBuckets() {
        final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().timeSensitive(true);
        final TimeWindow availability = ProblemFixtures.window(9, 21);

        final TravelMatrix matrix = new TravelMatrixPrefetcher(estimator)
                .prefetch(threeTasks(), TransportationMode.TRANSIT,
                        ProblemFixtures.TRIP_DATE, availability);

        final int pairs = 3 * 2;
        final int buckets = matrix.getPeriods().size();
        assertEquals(4, buckets);
        assertEquals(pairs * buckets, estimator.callCount());
        assertEquals(pairs * buckets, matrix.legCount());
    }

    @Test
    void timeInsensitiveModeCostsOneMatrix() {
        final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().timeSensitive(false);

        new TravelMatrixPrefetcher(estimator).prefetch(threeTasks(), TransportationMode.WALKING,
                ProblemFixtures.TRIP_DATE, ProblemFixtures.window(9, 21));

        assertEquals(6, estimator.callCount(), "walking should cost exactly one matrix");
    }

    @Test
    void neverRequestsALegFromAnActivityToItself() {
        final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().timeSensitive(false);

        new TravelMatrixPrefetcher(estimator).prefetch(threeTasks(), TransportationMode.WALKING,
                ProblemFixtures.TRIP_DATE, ProblemFixtures.window(9, 21));

        for (FakeTravelTimeEstimator.Call call : estimator.getCalls()) {
            assertTrue(!call.getFromId().equals(call.getToId()));
        }
    }

    @Test
    void storesDifferentDurationsPerPeriod() {
        final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator()
                .timeSensitive(true)
                .route("a", "b", DeparturePeriod.EARLY, 10)
                .route("a", "b", DeparturePeriod.PEAK, 40);

        final TravelMatrix matrix = new TravelMatrixPrefetcher(estimator)
                .prefetch(threeTasks(), TransportationMode.DRIVING,
                        ProblemFixtures.TRIP_DATE, ProblemFixtures.window(9, 21));

        assertEquals(10, matrix.estimateAt("a", "b", LocalTime.of(9, 30)).getMinutes());
        assertEquals(40, matrix.estimateAt("a", "b", LocalTime.of(17, 0)).getMinutes());
    }

    @Test
    void minMinutesIsNeverAboveAnyBucketValue() {
        final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator()
                .timeSensitive(true)
                .route("a", "b", DeparturePeriod.EARLY, 10)
                .route("a", "b", DeparturePeriod.MIDDAY, 25)
                .route("a", "b", DeparturePeriod.PEAK, 40)
                .route("a", "b", DeparturePeriod.LATE, 15);

        final TravelMatrix matrix = new TravelMatrixPrefetcher(estimator)
                .prefetch(threeTasks(), TransportationMode.DRIVING,
                        ProblemFixtures.TRIP_DATE, ProblemFixtures.window(9, 21));

        final int min = matrix.minMinutes("a", "b");
        assertEquals(10, min);
        for (DeparturePeriod period : matrix.getPeriods().activePeriods()) {
            final LocalTime sample = period.sampleWithin(ProblemFixtures.window(9, 21));
            assertTrue(matrix.estimateAt("a", "b", sample).getMinutes() >= min,
                    "the lower bound must never exceed a real bucket value");
        }
    }

    @Test
    void exactOverridesReplaceTheBucketTheyFallIn() {
        final FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().timeSensitive(true)
                .defaultMinutes(20);
        final TravelMatrix matrix = new TravelMatrixPrefetcher(estimator)
                .prefetch(threeTasks(), TransportationMode.DRIVING,
                        ProblemFixtures.TRIP_DATE, ProblemFixtures.window(9, 21));

        final java.util.Map<TravelLegKey, TravelEstimate> overrides = new java.util.HashMap<>();
        overrides.put(new TravelLegKey("a", "b", LocalTime.of(17, 0)), TravelEstimate.routed(55));
        final TravelMatrix refined = matrix.withOverrides(overrides);

        assertEquals(55, refined.estimateAt("a", "b", LocalTime.of(17, 30)).getMinutes());
        assertEquals(20, refined.estimateAt("a", "b", LocalTime.of(9, 30)).getMinutes());
        assertEquals(20, matrix.estimateAt("a", "b", LocalTime.of(17, 30)).getMinutes(),
                "the original matrix must be untouched");
    }
}
