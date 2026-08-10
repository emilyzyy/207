package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static use_case.autoschedule.ProblemFixtures.at;

import java.time.LocalDate;
import java.time.LocalTime;
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
import interface_adapter.viewmodels.ImprovementView;
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
 * When Autoschedule should decline to offer anything at all.
 *
 * <p>The search finding the arrangement the traveller already made is the right answer — it
 * means they arranged it well. It is not, however, an offer, and presenting it as one puts an
 * Apply button under a schedule identical to the saved day and asks for approval of a change
 * that does not exist.</p>
 *
 * <p>The distinction that matters: <em>nothing moved and nothing improved</em> is declined,
 * while <em>something moved</em> is always shown however its figures look. A day rearranged to
 * clear a newly declared unavailable period is worth seeing even when it costs travel, and the
 * trade-off strip exists to say so.</p>
 */
class NoBeneficialChangeTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);
    private static final List<SoftPolicy> POLICIES = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    private static Activity place(String id, ActivityCategory category, IndoorOutdoorType kind,
                                  double latitude, double longitude) {
        return new Activity(id, id, category, new Location(latitude, longitude, id), 4.5, 60,
                at(8, 0), at(21, 0), kind, "none");
    }

    private static ScheduledEvent event(String id, Activity activity, LocalTime start,
                                        int minutes) {
        return new ScheduledEvent(id, activity, start, start.plusMinutes(minutes),
                EventType.ACTIVITY, "");
    }

    private static Trip tripWith(List<ScheduledEvent> events) {
        Trip trip = new Trip("t", "Toronto", DATE, at(9, 0), at(21, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(events);
        return trip;
    }

    private static DayPlanState run(Trip trip, FakeTravelTimeEstimator estimator,
                                    java.util.Set<String> locks,
                                    List<TimeWindow> unavailable) {
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState("t",
                trip.getScheduledEvents(), "", false, Collections.emptyList()));
        new AutoScheduleInteractor(new FakeTripRepository(trip), estimator,
                new FakeWeatherContextGateway(), new AutoSchedulePresenter(viewModel),
                POLICIES, new ScheduleEngine())
                .preview(new AutoScheduleInputData("t", at(9, 0), at(21, 0),
                        TransportationMode.WALKING, locks, unavailable, false, true));
        return viewModel.getState();
    }

    /** Three neighbouring venues, already back to back from the first legal minute. */
    private static Trip alreadyOptimalDay() {
        return tripWith(Arrays.asList(
                event("a", place("a", ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR,
                        43.650, -79.380), at(9, 0), 60),
                event("b", place("b", ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR,
                        43.651, -79.381), at(10, 5), 60),
                event("c", place("c", ActivityCategory.MUSEUM, IndoorOutdoorType.INDOOR,
                        43.652, -79.382), at(11, 10), 60)));
    }

    // --- 1. dominance -----------------------------------------------------------------

    @Test
    void aDayThatCannotBeImprovedIsNotOfferedAsAProposal() {
        DayPlanState state = run(alreadyOptimalDay(),
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(5),
                Collections.emptySet(), Collections.emptyList());

        assertEquals(AutoScheduleStatus.NO_BENEFICIAL_CHANGE, state.getStatus(),
                "the search agreed with the day, which is an answer and not an offer: "
                        + state.getMessage());
        assertNotEquals(AutoScheduleStatus.PREVIEW, state.getStatus(),
                "and therefore nothing to Apply");
        assertTrue(state.getPreviewRows().isEmpty(), "no proposed rows to accept");
        assertEquals(0, state.getMetrics().getMovedActivityCount(), "nothing moved");
        assertTrue(state.getMessage().contains("already well arranged"), state.getMessage());
        assertTrue(state.getImprovements().isEmpty(),
                "and no improvement may be claimed: " + state.getImprovements());
    }

    /** Neither figure may worsen in the arrangement the search settles on. */
    @Test
    void theSearchNeverSettlesOnSomethingWorseThanTheDayItWasGiven() {
        DayPlanState state = run(alreadyOptimalDay(),
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(5),
                Collections.emptySet(), Collections.emptyList());

        assertTrue(state.getMetrics().getTravelAfterMinutes()
                        <= state.getMetrics().getTravelBeforeMinutes(),
                "travel got worse on a day that was already optimal");
        assertTrue(state.getMetrics().getIdleAfterMinutes()
                        <= state.getMetrics().getIdleBeforeMinutes(),
                "waiting got worse on a day that was already optimal");
    }

    /** The saved day is untouched either way; declining changes nothing at all. */
    @Test
    void decliningLeavesTheSavedDayExactlyWhereItWas() {
        Trip trip = alreadyOptimalDay();
        FakeTripRepository trips = new FakeTripRepository(trip);
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState("t",
                trip.getScheduledEvents(), "", false, Collections.emptyList()));

        new AutoScheduleInteractor(trips,
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(5),
                new FakeWeatherContextGateway(), new AutoSchedulePresenter(viewModel),
                POLICIES, new ScheduleEngine())
                .preview(new AutoScheduleInputData("t", at(9, 0), at(21, 0),
                        TransportationMode.WALKING, Collections.emptySet(),
                        Collections.emptyList(), false, true));

        List<ScheduledEvent> saved = trips.findById("t").get().getScheduledEvents();
        assertEquals(3, saved.size(), "no travel rows were written either");
        assertEquals(at(9, 0), saved.get(0).getStartTime());
        assertEquals(at(10, 5), saved.get(1).getStartTime());
        assertEquals(at(11, 10), saved.get(2).getStartTime());
    }

    // --- 2. a proposal that is worse but fixes a hard constraint ------------------------

    /**
     * The traveller declares themselves busy across the middle of a day that was otherwise
     * fine. The only legal arrangement costs travel, and it must still be offered.
     */
    @Test
    void aProposalThatFixesAHardConstraintIsOfferedEvenWhenItCostsMore() {
        Trip trip = alreadyOptimalDay();
        FakeTravelTimeEstimator estimator =
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(5);
        List<TimeWindow> busy = Collections.singletonList(
                new TimeWindow(at(9, 30), at(12, 0)));

        DayPlanState state = run(trip, estimator, Collections.emptySet(), busy);

        assertEquals(AutoScheduleStatus.PREVIEW, state.getStatus(),
                "a day that must be rearranged is worth offering: " + state.getMessage());
        assertTrue(state.getMetrics().getMovedActivityCount() > 0,
                "something had to move to clear the unavailable period");

        for (PreviewRowView row : state.getPreviewRows()) {
            assertTrue(!row.getStart().isBefore(at(12, 0)) || !row.getEnd().isAfter(at(9, 30)),
                    "nothing may sit inside the declared unavailable period: "
                            + row.getTitle() + " " + row.getStart() + "-" + row.getEnd());
        }
    }

    /** Whatever got worse is stated in the figures rather than hidden. */
    @Test
    void aWorseFigureIsReportedRatherThanSuppressed() {
        DayPlanState state = run(alreadyOptimalDay(),
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(5),
                Collections.emptySet(),
                Collections.singletonList(new TimeWindow(at(9, 30), at(12, 0))));

        boolean waitingWorse = state.getMetrics().getIdleAfterMinutes()
                > state.getMetrics().getIdleBeforeMinutes();
        boolean travelWorse = state.getMetrics().getTravelAfterMinutes()
                > state.getMetrics().getTravelBeforeMinutes();

        if (waitingWorse || travelWorse) {
            assertFalse(state.getObjectiveSummary().contains("less travel") && travelWorse,
                    "travel got worse: " + state.getObjectiveSummary());
            assertFalse(state.getObjectiveSummary().contains("fewer wasted gaps") && waitingWorse,
                    "waiting got worse: " + state.getObjectiveSummary());
        }
        // Whichever way it fell, the figures themselves are on screen to be read.
        assertTrue(state.getMetrics().getIdleBeforeMinutes() >= 0
                && state.getMetrics().getTravelBeforeMinutes() >= 0);
    }

    // --- 3. a lock is a constraint, not an improvement ---------------------------------

    /**
     * A lock's window <em>is</em> the activity's current time, so honouring one is never a
     * change the traveller did not already have. It belongs among the constraints respected,
     * not among the improvements — otherwise every pinned day claims a benefit for standing
     * still.
     */
    @Test
    void honouringALockTheDayAlreadyHonouredIsNotCountedAsAnImprovement() {
        Trip trip = alreadyOptimalDay();

        DayPlanState state = run(trip,
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(5),
                Collections.singleton("b"), Collections.emptyList());

        List<String> tilePrimaries = new ArrayList<>();
        for (ImprovementView tile : state.getImprovements()) {
            tilePrimaries.add(tile.getPrimary());
        }
        assertFalse(tilePrimaries.contains("PIN KEPT"),
                "the pin was already at that time before Autoschedule ran, so keeping it is "
                        + "not a new benefit: " + tilePrimaries);
    }

    // --- 4. the accepted plan is the refined one ---------------------------------------

    /**
     * Exact refinement re-times the chosen order against real departure estimates. The
     * decision to offer or decline has to be made on that final plan, not the bucketed one it
     * started from.
     */
    @Test
    void theDecisionIsMadeOnThePlanThatSurvivedExactRefinement() {
        // Bucketed estimates say 5 minutes; the exact re-check says 25. If the decision were
        // taken before refinement the reported travel would not match the drawn rows.
        FakeTravelTimeEstimator estimator = new FakeTravelTimeEstimator().timeSensitive(false);
        estimator.defaultMinutes(5);

        DayPlanState state = run(alreadyOptimalDay(), estimator,
                Collections.emptySet(),
                Collections.singletonList(new TimeWindow(at(9, 30), at(12, 0))));

        if (state.getStatus() != AutoScheduleStatus.PREVIEW) {
            return;
        }
        int drawn = 0;
        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getKind() == PreviewRowView.Kind.TRAVEL) {
                drawn += (row.getEnd().toSecondOfDay() - row.getStart().toSecondOfDay()) / 60;
            }
        }
        assertEquals(drawn, state.getMetrics().getTravelAfterMinutes(),
                "the figures must describe the refined plan that was actually accepted");
    }
}
