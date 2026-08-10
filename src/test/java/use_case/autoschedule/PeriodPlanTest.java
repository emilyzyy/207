package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class PeriodPlanTest {

    @Test
    void mapsEachTimeToTheContainingPeriod() {
        assertEquals(DeparturePeriod.EARLY, DeparturePeriod.containing(LocalTime.of(10, 59)));
        assertEquals(DeparturePeriod.MIDDAY, DeparturePeriod.containing(LocalTime.of(11, 0)));
        assertEquals(DeparturePeriod.MIDDAY, DeparturePeriod.containing(LocalTime.of(15, 59)));
        assertEquals(DeparturePeriod.PEAK, DeparturePeriod.containing(LocalTime.of(16, 0)));
        assertEquals(DeparturePeriod.PEAK, DeparturePeriod.containing(LocalTime.of(18, 59)));
        assertEquals(DeparturePeriod.LATE, DeparturePeriod.containing(LocalTime.of(19, 0)));
    }

    @Test
    void fullDayWindowUsesEveryOverlappingPeriod() {
        final PeriodPlan plan = PeriodPlan.forRun(ProblemFixtures.window(9, 21), true, 6);
        assertEquals(4, plan.size());
    }

    @Test
    void narrowWindowOnlyUsesPeriodsItTouches() {
        final PeriodPlan plan = PeriodPlan.forRun(ProblemFixtures.window(12, 15), true, 6);
        assertEquals(1, plan.size());
        assertEquals(DeparturePeriod.MIDDAY, plan.activePeriods().get(0));
    }

    @Test
    void timeInsensitiveModeCollapsesToOneBucket() {
        final PeriodPlan plan = PeriodPlan.forRun(ProblemFixtures.window(9, 21), false, 56);
        assertEquals(1, plan.size(), "walking has no time input, so one matrix is enough");
        assertEquals(56, plan.prefetchCallCount(56));
    }

    @Test
    void everyTimeStillResolvesAfterCollapsing() {
        final PeriodPlan plan = PeriodPlan.forRun(ProblemFixtures.window(9, 21), false, 56);
        final DeparturePeriod only = plan.activePeriods().get(0);
        assertEquals(only, plan.resolve(LocalTime.of(9, 30)));
        assertEquals(only, plan.resolve(LocalTime.of(17, 45)));
        assertEquals(only, plan.resolve(LocalTime.of(20, 30)));
    }

    @Test
    void budgetOverflowMergesPeriodsDeterministically() {
        // 8 activities = 56 directed pairs; 4 periods would be 224 calls, over the 120 cap.
        final PeriodPlan plan = PeriodPlan.forRun(ProblemFixtures.window(9, 21), true, 56);
        assertTrue(plan.prefetchCallCount(56) <= PeriodPlan.MAX_PREFETCH_CALLS,
                "prefetch must stay inside the documented budget");
        assertEquals(2, plan.size());
        assertTrue(plan.activePeriods().contains(DeparturePeriod.PEAK),
                "PEAK is kept longest because rush hour is the variation buckets exist for");
    }

    @Test
    void smallProblemsKeepFullGranularity() {
        final PeriodPlan plan = PeriodPlan.forRun(ProblemFixtures.window(9, 21), true, 6);
        assertEquals(4, plan.size());
        assertEquals(24, plan.prefetchCallCount(6));
    }

    @Test
    void mergingIsRepeatableForTheSameInput() {
        final PeriodPlan first = PeriodPlan.forRun(ProblemFixtures.window(9, 21), true, 56);
        final PeriodPlan second = PeriodPlan.forRun(ProblemFixtures.window(9, 21), true, 56);
        assertEquals(first.activePeriods(), second.activePeriods());
        assertEquals(first.resolve(LocalTime.of(17, 0)), second.resolve(LocalTime.of(17, 0)));
    }
}
