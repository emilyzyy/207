package interface_adapter.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import use_case.autoschedule.AutoSchedulePreviewOutputData;
import use_case.autoschedule.PolicyId;
import use_case.autoschedule.ProposedEventData;
import use_case.autoschedule.Reason;
import use_case.autoschedule.ScheduleImprovement;
import use_case.autoschedule.ScheduleImprovementType;
import use_case.autoschedule.TravelEstimateQuality;

/**
 * The sentence under the proposed schedule has to describe the schedule.
 *
 * <p>Reported from the running application: a proposal with more travel <em>and</em> more
 * waiting than the day it replaced, described underneath as "Arranged for less travel, fewer
 * wasted gaps". The sentence was assembled from which preferences were switched on, so it
 * announced the objective and called it the outcome — directly contradicting the figures
 * printed a few pixels above it.</p>
 *
 * <p>Every other claim on this screen is earned: a tile needs a real saving, a chip needs a
 * constraint that actually bound, a trade-off needs an exchange the figures show. This one was
 * the exception.</p>
 */
class ObjectiveSummaryTruthTest {

    private static final AutoSchedulePresenter PRESENTER =
            new AutoSchedulePresenter(new DayPlanViewModel(
                    new DayPlanState("t", Collections.emptyList(), "", false,
                            Collections.emptyList())));

    private static ProposedEventData row(String id) {
        return new ProposedEventData(id, id, id, ProposedEventData.Kind.ACTIVITY,
                LocalTime.of(9, 0), LocalTime.of(10, 0), false, true);
    }

    /**
     * @param travelBefore/travelAfter and idleBefore/idleAfter are the figures the screen
     *                                 prints directly above this sentence
     */
    private static AutoSchedulePreviewOutputData outcome(int travelBefore, int travelAfter,
                                                         int idleBefore, int idleAfter,
                                                         List<ScheduleImprovement> improvements,
                                                         boolean keptOrder) {
        return new AutoSchedulePreviewOutputData(Collections.singletonList(row("a")),
                travelBefore, travelAfter, idleBefore, idleAfter, 1, 1,
                Collections.<Reason>emptyList(), Collections.<String>emptyList(),
                Arrays.asList(PolicyId.MEAL_TIME, PolicyId.DAYLIGHT), "fingerprint",
                true, TravelEstimateQuality.ROUTED, keptOrder, improvements, 0);
    }

    /** The reported defect, stated as plainly as it can be. */
    @Test
    void aScheduleThatIsWorseOnBothCountsDoesNotClaimToBeBetter() {
        final String summary = PRESENTER.objectiveSummary(outcome(40, 75, 20, 130,
                Collections.<ScheduleImprovement>emptyList(), false));

        assertFalse(summary.contains("less travel"),
                "travel went up by 35 minutes: " + summary);
        assertFalse(summary.contains("fewer wasted gaps"),
                "waiting went up by 110 minutes: " + summary);
        assertTrue(summary.contains("does not reduce travel or waiting"),
                "and it has to say so rather than stay silent: " + summary);
    }

    /** Equal is not better. A saving of nothing is not a saving. */
    @Test
    void anUnchangedFigureIsNotAnImprovement() {
        final String summary = PRESENTER.objectiveSummary(outcome(65, 65, 90, 90,
                Collections.<ScheduleImprovement>emptyList(), false));

        assertFalse(summary.contains("less travel"), summary);
        assertFalse(summary.contains("fewer wasted gaps"), summary);
    }

    @Test
    void onlyTheFigureThatActuallyFellIsNamed() {
        final String summary = PRESENTER.objectiveSummary(outcome(65, 65, 295, 0,
                Collections.<ScheduleImprovement>emptyList(), false));

        assertTrue(summary.contains("fewer wasted gaps"), summary);
        assertFalse(summary.contains("less travel"),
                "travel was unchanged, so it may not be claimed: " + summary);
    }

    @Test
    void bothAreNamedWhenBothImproved() {
        final String summary = PRESENTER.objectiveSummary(outcome(80, 40, 200, 30,
                Collections.<ScheduleImprovement>emptyList(), false));

        assertEquals("Arranged for less travel and fewer wasted gaps.", summary);
    }

    /**
     * A soft preference being enabled is not an achievement either. Mealtimes are named only
     * when a meal actually moved into its window.
     */
    @Test
    void aSoftPreferenceIsNamedOnlyWhenItProducedAnImprovement() {
        final String withoutEvidence = PRESENTER.objectiveSummary(outcome(80, 40, 200, 30,
                Collections.<ScheduleImprovement>emptyList(), false));
        assertFalse(withoutEvidence.contains("mealtimes"),
                "the meal preference was on, but nothing moved into a window: "
                        + withoutEvidence);

        final String withEvidence = PRESENTER.objectiveSummary(outcome(80, 40, 200, 30,
                Collections.singletonList(ScheduleImprovement.forActivity(
                        ScheduleImprovementType.MEAL_MOVED_TOWARD_WINDOW, 30, "La Zucca")),
                false));
        assertTrue(withEvidence.contains("sensible mealtimes"), withEvidence);
    }

    @Test
    void daylightAndWeatherAreAlsoEarnedRatherThanAssumed() {
        final String summary = PRESENTER.objectiveSummary(outcome(80, 40, 200, 30, Arrays.asList(
                ScheduleImprovement.forActivity(
                        ScheduleImprovementType.MOVED_INTO_DAYLIGHT, 20, "High Park"),
                ScheduleImprovement.forActivity(
                        ScheduleImprovementType.MOVED_TO_BETTER_WEATHER, 15, "High Park")),
                false));

        assertTrue(summary.contains("daylight for outdoor activities"), summary);
        assertTrue(summary.contains("better weather"), summary);
    }

    /** Several clauses still read as a sentence rather than a comma splice. */
    @Test
    void theClausesReadAsEnglish() {
        final String summary = PRESENTER.objectiveSummary(outcome(80, 40, 200, 30,
                Collections.singletonList(ScheduleImprovement.forActivity(
                        ScheduleImprovementType.MEAL_MOVED_TOWARD_WINDOW, 30, "La Zucca")),
                false));

        assertEquals("Arranged for less travel, fewer wasted gaps and sensible mealtimes.",
                summary);
    }

    /** Order preservation is a separate, independently true statement. */
    @Test
    void keepingTheOrderIsStillReportedEvenWhenNothingElseImproved() {
        final String summary = PRESENTER.objectiveSummary(outcome(40, 75, 20, 130,
                Collections.<ScheduleImprovement>emptyList(), true));

        assertTrue(summary.contains("does not reduce travel or waiting"), summary);
        assertTrue(summary.contains("original order was kept"), summary);
    }
}
