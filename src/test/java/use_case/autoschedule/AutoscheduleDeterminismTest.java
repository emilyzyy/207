package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static use_case.autoschedule.ProblemFixtures.at;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import interface_adapter.presenters.AutoSchedulePresenter;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import interface_adapter.viewmodels.PreviewRowView;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.SoftPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import use_case.autoschedule.testdoubles.FakeTravelTimeEstimator;
import use_case.autoschedule.testdoubles.FakeTripRepository;
import use_case.autoschedule.testdoubles.FakeWeatherContextGateway;

/**
 * The same request, asked twice, must answer the same way.
 *
 * <p>Reported from the running application: Autoschedule refused with "no arrangement of this
 * day fits your available hours once travel between these activities is included", and a second
 * press with nothing visibly changed succeeded. Two attempts, identical inputs, different
 * outcomes.</p>
 *
 * <p>The contract these tests hold down: a given input snapshot and a given set of external
 * service results always produce the same typed outcome. Where the outside world genuinely
 * changes between attempts that is not a defect — but it must be reported as what it is, and a
 * provider that fails must never be reported as a day that cannot be arranged.</p>
 */
class AutoscheduleDeterminismTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);
    private static final List<SoftPolicy> POLICIES = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    /** Answers differently every call, the way a live traffic service legitimately might. */
    private static final class DriftingEstimator implements TravelTimeEstimator {
        private int calls;
        private boolean failing;

        @Override
        public TravelEstimate estimate(Location from, Location to, TransportationMode mode,
                                       LocalDateTime departure) {
            if (failing) {
                throw new IllegalStateException("routing provider is down");
            }
            calls++;
            return TravelEstimate.routed(10 + calls % 7);
        }

        @Override
        public boolean isTimeSensitive(TransportationMode mode) {
            return false;
        }
    }

    private static Activity place(String id, double latitude, double longitude) {
        return new Activity(id, "Place " + id, ActivityCategory.MUSEUM,
                new Location(latitude, longitude, id), 4.5, 60,
                at(8, 0), at(21, 0), IndoorOutdoorType.INDOOR, "none");
    }

    private static Trip dayOf(int activityCount) {
        final List<ScheduledEvent> events = new ArrayList<>();
        for (int i = 0; i < activityCount; i++) {
            final String id = "e" + i;
            events.add(new ScheduledEvent(id, place(id, 43.6 + i * 0.02, -79.4 + i * 0.02),
                    at(9 + i * 2, 0), at(10 + i * 2, 0), EventType.ACTIVITY, ""));
        }
        final Trip trip = new Trip("trip-1", "Toronto", DATE, at(9, 0), at(21, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(events);
        return trip;
    }

    private static AutoScheduleInputData request(List<TimeWindow> unavailable) {
        return new AutoScheduleInputData("trip-1", at(9, 0), at(21, 0),
                TransportationMode.WALKING, Collections.emptySet(), unavailable, false, true);
    }

    private static DayPlanState runOnce(Trip trip, TravelTimeEstimator estimator,
                                        AutoScheduleInputData input) {
        final DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState("trip-1",
                trip.getScheduledEvents(), "", false, Collections.emptyList()));
        new AutoScheduleInteractor(new FakeTripRepository(trip), estimator,
                new FakeWeatherContextGateway(), new AutoSchedulePresenter(viewModel),
                POLICIES, new ScheduleEngine()).preview(input);
        return viewModel.getState();
    }

    /** Everything about the answer that a user could see, as one comparable string. */
    private static String fingerprintOf(DayPlanState state) {
        final StringBuilder print = new StringBuilder(state.getStatus().name()).append('|');
        for (PreviewRowView row : state.getPreviewRows()) {
            print.append(row.getKind()).append(' ').append(row.getEventId()).append(' ')
                    .append(row.getStart()).append('-').append(row.getEnd()).append(';');
        }
        if (state.getMetrics() != null) {
            print.append("travel=").append(state.getMetrics().getTravelAfterMinutes())
                    .append(",wait=").append(state.getMetrics().getIdleAfterMinutes());
        }
        return print.append('|').append(state.getMessage()).toString();
    }

    // 1
    @Test
    void identicalInputAndTravelMatrixProduceIdenticalResultsEveryTime() {
        String first = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            final FakeTravelTimeEstimator estimator =
                    new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(11);
            final String print = fingerprintOf(runOnce(dayOf(4), estimator, request(
                    Collections.emptyList())));
            if (first == null) {
                first = print;
            }
            assertEquals(first, print, "attempt " + attempt + " differed from the first");
        }
    }

    // 2 and 3: the same set of activities offered in a different order must still resolve the
    // same way, so no hash iteration order can decide the winner.
    @Test
    void theWinnerDoesNotDependOnTheOrderTheActivitiesArriveIn() {
        final FakeTravelTimeEstimator estimator =
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(11);
        final Trip forwards = dayOf(4);

        final List<ScheduledEvent> shuffled = new ArrayList<>(forwards.getScheduledEvents());
        Collections.reverse(shuffled);
        Collections.sort(shuffled, (left, right) -> {
            return left.getStartTime().compareTo(right.getStartTime());
        });
        final Trip rebuilt = new Trip("trip-1", "Toronto", DATE, at(9, 0), at(21, 0),
                TransportationMode.WALKING);
        rebuilt.replaceSchedule(shuffled);

        assertEquals(fingerprintOf(runOnce(forwards, estimator, request(Collections.emptyList()))),
                fingerprintOf(runOnce(rebuilt, estimator, request(Collections.emptyList()))));
    }

    /**
     * A routing provider that fails must not be reported as a day that cannot be arranged.
     *
     * <p>This is the shape of the reported inconsistency: one attempt hit a provider problem
     * and was told the day was impossible, the next attempt got an answer and worked.</p>
     */
    // 4
    @Test
    void aProviderFailureIsReportedAsAProviderFailureNotAnImpossibleDay() {
        final DriftingEstimator failing = new DriftingEstimator();
        failing.failing = true;

        final DayPlanState state = runOnce(dayOf(3), failing, request(Collections.emptyList()));

        assertNotEquals(AutoScheduleStatus.PREVIEW, state.getStatus());
        assertTrue(state.getMessage().toLowerCase().contains("travel times are unavailable"),
                "the message must name the provider, not the day: " + state.getMessage());
        assertTrue(!state.getMessage().contains("no arrangement"),
                "a failed lookup is not proof that the day cannot be arranged: "
                        + state.getMessage());
    }

    /**
     * Refinement re-asks the provider for exact departure times after the search. A failure
     * there used to escape the request entirely; the schedule the search already found must
     * survive it.
     */
    @Test
    void aFailureWhileRefiningKeepsTheScheduleTheSearchAlreadyFound() {
        final FakeTravelTimeEstimator prefetch =
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(11);
        final DayPlanState healthy = runOnce(dayOf(3), prefetch, request(Collections.emptyList()));
        assertEquals(AutoScheduleStatus.PREVIEW, healthy.getStatus(), healthy.getMessage());

        // Same day, but the provider breaks after the prefetch has already succeeded.
        final TravelTimeEstimator breaksAfterPrefetch = new TravelTimeEstimator() {
            private int calls;

            @Override
            public TravelEstimate estimate(Location from, Location to, TransportationMode mode,
                                           LocalDateTime departure) {
                calls++;
                if (calls > 6) {
                    throw new IllegalStateException("provider died mid-request");
                }
                return TravelEstimate.routed(11);
            }

            @Override
            public boolean isTimeSensitive(TransportationMode mode) {
                return false;
            }
        };

        final DayPlanState state = runOnce(dayOf(3), breaksAfterPrefetch, request(
                Collections.emptyList()));

        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(),
                "a refinement failure must not lose the schedule: " + state.getMessage());
    }

    /**
     * The reported unavailable window, on a day that genuinely fits around it. Whatever the
     * answer is, it must be the same answer every time.
     */
    // 10
    @Test
    void aRetainedUnavailableWindowGivesTheSameAnswerOnEveryAttempt() {
        final List<TimeWindow> unavailable = Collections.singletonList(
                new TimeWindow(at(10, 0), at(13, 0)));

        String first = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            final FakeTravelTimeEstimator estimator =
                    new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(11);
            final DayPlanState state = runOnce(dayOf(3), estimator, request(unavailable));
            final String print = fingerprintOf(state);
            if (first == null) {
                first = print;
            }
            assertEquals(first, print, "attempt " + attempt + " differed: " + print);
            for (PreviewRowView row : state.getPreviewRows()) {
                assertTrue(!row.getStart().isBefore(at(13, 0))
                                || !row.getEnd().isAfter(at(10, 0)),
                        "nothing may sit inside the unavailable window: " + row.getTitle()
                                + " " + row.getStart() + "-" + row.getEnd());
            }
        }
    }

    /**
     * One request must not mix two versions of the world.
     *
     * <p>The prefetch and the exact refinement are two conversations with a live service. An
     * estimator that answers differently every call stands in for traffic moving between them:
     * whatever it says, the request must settle on one set of numbers, and the travel it reports
     * must be the travel it drew.</p>
     */
    @Test
    void oneRequestUsesOneCoherentSetOfEstimates() {
        final DayPlanState state = runOnce(dayOf(4), new DriftingEstimator(),
                request(Collections.emptyList()));

        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(), state.getMessage());
        int drawn = 0;
        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getKind() == PreviewRowView.Kind.TRAVEL) {
                drawn += (row.getEnd().toSecondOfDay() - row.getStart().toSecondOfDay()) / 60;
            }
        }
        assertEquals(drawn, state.getMetrics().getTravelAfterMinutes(),
                "a request that searched on one set of estimates and reported another would "
                        + "disagree with its own timeline");
    }

    // 9
    @Test
    void appliedTravelRowsAreNeverScheduledAsActivities() {
        final Trip trip = dayOf(3);
        final List<ScheduledEvent> withTravel = new ArrayList<>(trip.getScheduledEvents());
        withTravel.add(new ScheduledEvent("travel-e1", null, at(10, 0), at(10, 20),
                EventType.TRAVEL, "Travel to Place e1"));
        Collections.sort(withTravel, (left, right) -> {
            return left.getStartTime().compareTo(right.getStartTime());
        });
        trip.replaceSchedule(withTravel);

        final DayPlanState state = runOnce(trip, new FakeTravelTimeEstimator()
                .timeSensitive(false).defaultMinutes(11), request(Collections.emptyList()));

        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(), state.getMessage());
        int activities = 0;
        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getKind() == PreviewRowView.Kind.ACTIVITY) {
                activities++;
                assertTrue(!row.getEventId().startsWith("travel-"),
                        "a generated journey was scheduled as an activity: " + row.getEventId());
            }
        }
        assertEquals(3, activities, "the day has three activities, not four");
    }
}
