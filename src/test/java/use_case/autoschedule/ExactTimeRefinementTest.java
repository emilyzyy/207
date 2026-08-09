package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.engine.ScheduleSearchResult;
import use_case.autoschedule.engine.SearchBudget;
import use_case.autoschedule.testdoubles.FakeTripRepository;
import use_case.autoschedule.testdoubles.FakeWeatherContextGateway;
import use_case.autoschedule.testdoubles.RecordingPresenter;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.EventType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The search compares orders using estimates prefetched for a few departure periods; the
 * order it chooses is then re-estimated for the times it will really be travelled. These
 * tests cover what happens when those truer numbers agree, when they break the schedule,
 * and when nothing can be salvaged.
 */
class ExactTimeRefinementTest {

    private final RecordingPresenter presenter = new RecordingPresenter();
    private final FakeWeatherContextGateway weather = new FakeWeatherContextGateway();

    /** The instants the prefetch samples, one per departure period, for a 09:00-21:00 day. */
    private static final List<LocalTime> BUCKET_SAMPLES = Arrays.asList(
            LocalTime.of(9, 0), LocalTime.of(11, 0), LocalTime.of(16, 0), LocalTime.of(19, 0));

    /**
     * Cheap when asked about a bucket sample, expensive when asked about a real departure.
     * Keying on the time rather than on a call counter keeps the fake deterministic no
     * matter what order the interactor happens to ask in.
     */
    private static final class OptimisticBucketEstimator implements TravelTimeEstimator {
        private final int bucketMinutes;
        private final int actualMinutes;
        private int calls;

        OptimisticBucketEstimator(int bucketMinutes, int actualMinutes) {
            this.bucketMinutes = bucketMinutes;
            this.actualMinutes = actualMinutes;
        }

        @Override
        public TravelEstimate estimate(Location from, Location to, TransportationMode mode,
                                       LocalDateTime departure) {
            calls++;
            boolean prefetchSample = BUCKET_SAMPLES.contains(departure.toLocalTime());
            return TravelEstimate.routed(prefetchSample ? bucketMinutes : actualMinutes);
        }

        @Override
        public boolean isTimeSensitive(TransportationMode mode) {
            return true;
        }

        int callCount() {
            return calls;
        }
    }

    private static Trip tripWith(ScheduledEvent... events) {
        Trip trip = new Trip("trip-1", "Toronto", ProblemFixtures.TRIP_DATE,
                LocalTime.of(9, 0), LocalTime.of(21, 0), TransportationMode.TRANSIT);
        trip.replaceSchedule(Arrays.asList(events));
        return trip;
    }

    private static ScheduledEvent event(String id, LocalTime start, int durationMinutes) {
        Activity activity = ProblemFixtures.activity(id, LocalTime.of(9, 0), LocalTime.of(21, 0));
        return new ScheduledEvent(id, activity, start, start.plusMinutes(durationMinutes),
                EventType.ACTIVITY, "");
    }

    private static AutoScheduleInputData input() {
        return new AutoScheduleInputData("trip-1", LocalTime.of(9, 0), LocalTime.of(21, 0),
                TransportationMode.TRANSIT, Collections.emptySet(), Collections.emptyList(),
                true, true);
    }

    private AutoScheduleInteractor interactorWith(TravelTimeEstimator estimator,
                                                  FakeTripRepository trips) {
        return new AutoScheduleInteractor(trips, estimator, weather, presenter,
                Collections.emptyList(), new ScheduleEngine());
    }

    @Test
    void thePreviewShowsTheTravelTimeTheTravellerWillActuallyExperience() {
        // Activities of 100 minutes mean departures never land on a bucket sample.
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(event("a", LocalTime.of(9, 0), 100),
                        event("b", LocalTime.of(13, 0), 100)));

        interactorWith(new OptimisticBucketEstimator(20, 35), trips).preview(input());

        AutoSchedulePreviewOutputData preview = presenter.getPreview();
        assertNotNull(preview);
        assertEquals(35, preview.getTravelAfterMinutes(),
                "the Preview should show the refined time, not the bucketed guess");
    }

    @Test
    void refinedTimesAreReflectedInTheRowsThemselves() {
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(event("a", LocalTime.of(9, 0), 100),
                        event("b", LocalTime.of(13, 0), 100)));

        interactorWith(new OptimisticBucketEstimator(20, 35), trips).preview(input());

        List<ProposedEventData> rows = presenter.getPreview().getRows();
        ProposedEventData travel = rows.stream()
                .filter(row -> row.getKind() == ProposedEventData.Kind.TRAVEL)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(35, (travel.getEnd().toSecondOfDay() - travel.getStart().toSecondOfDay()) / 60);
    }

    @Test
    void aScheduleIsNeverShownWithOverlapsOrTooLittleTravelTime() {
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(event("a", LocalTime.of(9, 0), 100),
                        event("b", LocalTime.of(13, 0), 100)));

        interactorWith(new OptimisticBucketEstimator(10, 90), trips).preview(input());

        AutoSchedulePreviewOutputData preview = presenter.getPreview();
        if (preview == null) {
            assertNotNull(presenter.getConflict());
            return;
        }
        List<ProposedEventData> rows = preview.getRows();
        for (int i = 1; i < rows.size(); i++) {
            assertFalse(rows.get(i).getStart().isBefore(rows.get(i - 1).getEnd()),
                    "rows overlap: " + rows.get(i - 1).getEnd() + " then " + rows.get(i).getStart());
        }
    }

    @Test
    void whenRealTravelTimesBreakTheDayTheUserGetsAConflictNotABadSchedule() {
        // Three 100-minute activities and ten-hour journeys: nothing can be salvaged.
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(event("a", LocalTime.of(9, 0), 100),
                        event("b", LocalTime.of(13, 0), 100),
                        event("c", LocalTime.of(17, 0), 100)));

        interactorWith(new OptimisticBucketEstimator(5, 600), trips).preview(input());

        assertNull(presenter.getPreview(),
                "a schedule known to be invalid must never reach the user");
        assertNotNull(presenter.getConflict());
        assertEquals(ScheduleConflict.Kind.REFINED_TRAVEL_INFEASIBLE,
                presenter.getConflict().getKind());
        assertEquals(0, trips.getSaveCount());
    }

    @Test
    void refinementIsBoundedRatherThanLoopingForever() {
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(event("a", LocalTime.of(9, 0), 100),
                        event("b", LocalTime.of(13, 0), 100),
                        event("c", LocalTime.of(17, 0), 100)));
        OptimisticBucketEstimator estimator = new OptimisticBucketEstimator(5, 600);

        interactorWith(estimator, trips).preview(input());

        int prefetch = 3 * 2 * 4;
        int refinementCalls = estimator.callCount() - prefetch;
        assertTrue(refinementCalls <= 2 * (AutoScheduleInteractor.MAX_REFINEMENT_ROUNDS + 1) + 2,
                "refinement should stop after the bounded rounds, made " + refinementCalls);
    }

    @Test
    void theSearchItselfNeverAsksTheEstimatorForAnything() {
        List<ScheduleTask> items = ProblemFixtures.tasks(
                ProblemFixtures.task("a", 60, 0, LocalTime.of(9, 0), LocalTime.of(21, 0)),
                ProblemFixtures.task("b", 60, 1, LocalTime.of(9, 0), LocalTime.of(21, 0)),
                ProblemFixtures.task("c", 60, 2, LocalTime.of(9, 0), LocalTime.of(21, 0)));
        OptimisticBucketEstimator estimator = new OptimisticBucketEstimator(15, 15);
        TravelMatrix matrix = new TravelMatrixPrefetcher(estimator).prefetch(items,
                TransportationMode.TRANSIT, ProblemFixtures.TRIP_DATE,
                ProblemFixtures.window(9, 21));
        int callsAfterPrefetch = estimator.callCount();

        ScheduleSearchResult result = new ScheduleEngine().search(
                new ScheduleProblem(ProblemFixtures.window(9, 21), items,
                        ProblemFixtures.noBlockedWindows(), matrix),
                SearchBudget.defaultBudget());

        assertTrue(result.isFound());
        assertEquals(callsAfterPrefetch, estimator.callCount(),
                "the recursive search must be pure: no estimator calls during search");
    }

    @Test
    void prefetchAsksForEachLegOncePerBucketAndNoMore() {
        List<ScheduleTask> items = ProblemFixtures.tasks(
                ProblemFixtures.task("a", 60, 0, LocalTime.of(9, 0), LocalTime.of(21, 0)),
                ProblemFixtures.task("b", 60, 1, LocalTime.of(9, 0), LocalTime.of(21, 0)));
        OptimisticBucketEstimator estimator = new OptimisticBucketEstimator(15, 15);

        TravelMatrix matrix = new TravelMatrixPrefetcher(estimator).prefetch(items,
                TransportationMode.TRANSIT, ProblemFixtures.TRIP_DATE,
                ProblemFixtures.window(9, 21));

        assertEquals(2 * matrix.getPeriods().size(), estimator.callCount());
    }

    @Test
    void whenTheBucketedGuessWasAlreadyRightNothingIsRecomputed() {
        FakeTripRepository trips = new FakeTripRepository(
                tripWith(event("a", LocalTime.of(9, 0), 100),
                        event("b", LocalTime.of(13, 0), 100)));
        OptimisticBucketEstimator agreeing = new OptimisticBucketEstimator(20, 20);

        interactorWith(agreeing, trips).preview(input());

        assertNotNull(presenter.getPreview());
        assertEquals(20, presenter.getPreview().getTravelAfterMinutes());
    }
}
