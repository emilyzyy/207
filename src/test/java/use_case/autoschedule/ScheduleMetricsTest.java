package use_case.autoschedule;

import static use_case.autoschedule.ProblemFixtures.at;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import use_case.autoschedule.testdoubles.FakeTravelTimeEstimator;
import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The "before" half of the Preview's comparison.
 *
 * <p>These exist because the original reading was wrong in a way that flattered the
 * feature: it summed only explicit travel rows, and a plan the traveller built by hand has
 * none, so a day spread across a city reported zero minutes of travel. Every "before/after"
 * figure and the travel improvement card rest on this being honest.</p>
 */
class ScheduleMetricsTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

    private static Activity somewhere(String id) {
        return new Activity(id, id, ActivityCategory.MUSEUM, new Location(43.65, -79.38, id),
                4.5, 60, at(0, 0), at(23, 59), IndoorOutdoorType.INDOOR, "none");
    }

    private static ScheduledEvent activity(String id, int startHour) {
        return new ScheduledEvent(id, somewhere(id), at(startHour, 0), at(startHour + 1, 0),
                EventType.ACTIVITY, "");
    }

    @Test
    void aHandBuiltPlanIsChargedTheTravelItsOrderActuallyRequires() {
        List<ScheduledEvent> plan = Arrays.asList(activity("a", 9), activity("b", 12));
        FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().defaultMinutes(25);

        ScheduleMetrics naive = ScheduleMetrics.ofExistingSchedule(plan);
        ScheduleMetrics honest = ScheduleMetrics.ofExistingSchedule(plan, estimator,
                TransportationMode.WALKING, DATE);

        assertEquals(0, naive.getTravelMinutes(),
                "the old reading sees no travel rows and reports nothing");
        assertEquals(25, honest.getTravelMinutes(),
                "two activities in two places cost a journey, recorded or not");
    }

    @Test
    void theGapIsSplitBetweenTravelAndWaitingRatherThanCountedTwice() {
        // 10:00 to 12:00 is a two-hour gap; 25 minutes of it is the journey.
        List<ScheduledEvent> plan = Arrays.asList(activity("a", 9), activity("b", 12));
        FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().defaultMinutes(25);

        ScheduleMetrics honest = ScheduleMetrics.ofExistingSchedule(plan, estimator,
                TransportationMode.WALKING, DATE);

        assertEquals(25, honest.getTravelMinutes());
        assertEquals(95, honest.getIdleMinutes(),
                "waiting is what is left of the gap once the journey is paid for");
    }

    @Test
    void explicitTravelRowsAreTrustedAndNotDoubleCounted() {
        // A plan Autoschedule already applied records its own journeys.
        List<ScheduledEvent> applied = Arrays.asList(
                activity("a", 9),
                new ScheduledEvent("t", null, at(10, 0), at(10, 30), EventType.TRAVEL,
                        "Travel to b"),
                activity("b", 11));
        FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().defaultMinutes(25);

        ScheduleMetrics honest = ScheduleMetrics.ofExistingSchedule(applied, estimator,
                TransportationMode.WALKING, DATE);

        assertEquals(30, honest.getTravelMinutes(),
                "the recorded 30-minute journey stands; the estimate must not be added to it");
    }

    @Test
    void aSingleActivityDayHasNoTravelToCharge() {
        ScheduleMetrics honest = ScheduleMetrics.ofExistingSchedule(
                Collections.singletonList(activity("a", 9)),
                new FakeTravelTimeEstimator().defaultMinutes(25),
                TransportationMode.WALKING, DATE);

        assertEquals(0, honest.getTravelMinutes());
    }

    @Test
    void anEstimatorThatFailsDegradesRatherThanLosingTheComparison() {
        List<ScheduledEvent> plan = Arrays.asList(activity("a", 9), activity("b", 12));

        TravelTimeEstimator failing = new TravelTimeEstimator() {
            @Override
            public TravelEstimate estimate(Location from, Location to,
                                           TransportationMode mode,
                                           java.time.LocalDateTime departure) {
                throw new IllegalStateException("routing unavailable");
            }

            @Override
            public boolean isTimeSensitive(TransportationMode mode) {
                return false;
            }
        };

        ScheduleMetrics honest = ScheduleMetrics.ofExistingSchedule(plan, failing,
                TransportationMode.WALKING, DATE);

        assertEquals(0, honest.getTravelMinutes(),
                "an unavailable estimator costs the estimate, not the Preview");
        assertTrue(honest.getIdleMinutes() > 0, "the rest of the comparison still works");
    }

    @Test
    void missingArgumentsFallBackToTheExplicitRowReading() {
        List<ScheduledEvent> plan = Arrays.asList(activity("a", 9), activity("b", 12));

        assertEquals(ScheduleMetrics.ofExistingSchedule(plan).getTravelMinutes(),
                ScheduleMetrics.ofExistingSchedule(plan, null, null, null).getTravelMinutes());
    }
}
