package use_case.autoschedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Removing an activity from a Preview edits the unsaved proposal and nothing else.
 *
 * <p>Reported from the running application: pressing Remove on a proposed activity left Preview
 * and the schedule appeared to have been applied. It had been — the button drove the saved-plan
 * use case.</p>
 *
 * <p>The model these tests hold down is a direct edit, not a re-solve. The traveller is reading
 * a schedule they have mostly accepted; taking one thing out of it should leave everything else
 * exactly where the search put it. Only the journeys either side of the removed activity change,
 * and nothing reaches the repository until Apply.</p>
 */
class ProposalDraftRemovalTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);
    private static final List<SoftPolicy> POLICIES = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    private static Activity place(String id, double latitude, double longitude) {
        return new Activity(id, "Place " + id, ActivityCategory.MUSEUM,
                new Location(latitude, longitude, id), 4.5, 60,
                at(8, 0), at(21, 0), IndoorOutdoorType.INDOOR, "none");
    }

    /** A saved day of four activities, well spread out so a proposal has room to move. */
    private static Trip savedDay() {
        List<ScheduledEvent> events = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String id = "e" + i;
            events.add(new ScheduledEvent(id, place(id, 43.6 + i * 0.01, -79.4 + i * 0.01),
                    at(9 + i * 3, 0), at(10 + i * 3, 0), EventType.ACTIVITY, ""));
        }
        Trip trip = new Trip("trip-1", "Toronto", DATE, at(9, 0), at(21, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(events);
        return trip;
    }

    private static final class Session {
        private final Trip trip = savedDay();
        private final FakeTripRepository trips = new FakeTripRepository(trip);
        private final DayPlanViewModel viewModel = new DayPlanViewModel(
                new DayPlanState("trip-1", trip.getScheduledEvents(), "", false,
                        Collections.emptyList()));
        private final FakeTravelTimeEstimator estimator =
                new FakeTravelTimeEstimator().timeSensitive(false).defaultMinutes(8);
        private final AutoScheduleInteractor interactor = new AutoScheduleInteractor(trips,
                estimator, new FakeWeatherContextGateway(), new AutoSchedulePresenter(viewModel),
                POLICIES, new ScheduleEngine());

        private Session preview() {
            interactor.preview(new AutoScheduleInputData("trip-1", at(9, 0), at(21, 0),
                    TransportationMode.WALKING, Collections.emptySet(),
                    Collections.emptyList(), false, true));
            return this;
        }

        private Session removeFromProposal(String eventId) {
            interactor.removeFromProposal(new ProposalEditInputData("trip-1",
                    proposedRows(), eventId, TransportationMode.WALKING,
                    viewModel.getState().getPreviewFingerprint()));
            return this;
        }

        private List<ProposedEventData> proposedRows() {
            List<ProposedEventData> rows = new ArrayList<>();
            for (PreviewRowView row : viewModel.getState().getPreviewRows()) {
                rows.add(new ProposedEventData(row.getEventId(), "", row.getTitle(),
                        row.getKind() == PreviewRowView.Kind.TRAVEL
                                ? ProposedEventData.Kind.TRAVEL
                                : ProposedEventData.Kind.ACTIVITY,
                        row.getStart(), row.getEnd(), row.isLocked(), row.isMoved()));
            }
            return rows;
        }

        private DayPlanState state() {
            return viewModel.getState();
        }

        private int savedActivityCount() {
            int count = 0;
            for (ScheduledEvent event : trips.findById("trip-1").get().getScheduledEvents()) {
                if (event.getEventType() == EventType.ACTIVITY) {
                    count++;
                }
            }
            return count;
        }
    }

    private static List<String> activityIds(DayPlanState state) {
        List<String> ids = new ArrayList<>();
        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getKind() == PreviewRowView.Kind.ACTIVITY) {
                ids.add(row.getEventId());
            }
        }
        return ids;
    }

    private static List<String> travelIds(DayPlanState state) {
        List<String> ids = new ArrayList<>();
        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getKind() == PreviewRowView.Kind.TRAVEL) {
                ids.add(row.getEventId());
            }
        }
        return ids;
    }

    private static LocalTime startOf(DayPlanState state, String eventId) {
        for (PreviewRowView row : state.getPreviewRows()) {
            if (row.getEventId().equals(eventId)) {
                return row.getStart();
            }
        }
        return null;
    }

    private static String describe(DayPlanState state) {
        StringBuilder text = new StringBuilder("\n");
        for (PreviewRowView row : state.getPreviewRows()) {
            text.append("  ").append(row.getKind()).append(' ').append(row.getStart())
                    .append('-').append(row.getEnd()).append(' ').append(row.getEventId())
                    .append('\n');
        }
        return text.toString();
    }

    // 2, 3
    @Test
    void removingFromTheProposalSavesNothingAndStaysInPreview() {
        Session session = new Session().preview();
        assertEquals(AutoScheduleStatus.PREVIEW, session.state().getStatus());

        session.removeFromProposal("e1");

        assertEquals(AutoScheduleStatus.PREVIEW, session.state().getStatus(),
                "the traveller is still reading a proposal: " + session.state().getMessage());
        assertEquals(4, session.savedActivityCount(),
                "the saved Day Plan may not change until Apply");
    }

    // 4: the remaining activities keep the times the search gave them.
    @Test
    void theRemainingActivitiesKeepTheirProposedTimesAndOrder() {
        Session session = new Session().preview();
        List<String> before = activityIds(session.state());
        LocalTime firstStart = startOf(session.state(), before.get(0));
        LocalTime lastStart = startOf(session.state(), before.get(before.size() - 1));

        session.removeFromProposal(before.get(1));

        assertEquals(firstStart, startOf(session.state(), before.get(0)),
                "nothing else may move: this is an edit, not a re-solve"
                        + describe(session.state()));
        assertEquals(lastStart, startOf(session.state(), before.get(before.size() - 1)),
                describe(session.state()));
    }

    // 5, 6, 8
    @Test
    void removingAMiddleActivityKeepsUnaffectedLegsAndReplacesTheAdjacentPair() {
        Session session = new Session().preview();
        List<String> ids = activityIds(session.state());
        String removed = ids.get(1);

        session.removeFromProposal(removed);

        List<String> remaining = activityIds(session.state());
        assertEquals(3, remaining.size(), describe(session.state()));
        assertFalse(remaining.contains(removed));
        assertFalse(travelIds(session.state()).contains("travel-" + removed),
                "the journey into the removed activity must go" + describe(session.state()));
        assertEquals(2, travelIds(session.state()).size(),
                "three activities in a row need two journeys" + describe(session.state()));
    }

    // 7
    @Test
    void removingTheFirstProposedActivityDropsOnlyItsOutgoingLeg() {
        Session session = new Session().preview();
        List<String> ids = activityIds(session.state());

        session.removeFromProposal(ids.get(0));

        assertEquals(3, activityIds(session.state()).size());
        assertEquals(2, travelIds(session.state()).size(), describe(session.state()));
    }

    // 9
    @Test
    void removingTheFinalProposedActivityDropsOnlyItsIncomingLeg() {
        Session session = new Session().preview();
        List<String> ids = activityIds(session.state());

        session.removeFromProposal(ids.get(ids.size() - 1));

        assertEquals(3, activityIds(session.state()).size());
        assertEquals(2, travelIds(session.state()).size(), describe(session.state()));
    }

    // 10, 11
    @Test
    void removingDownToOneAndThenNoneLeavesNoJourneys() {
        Session session = new Session().preview();
        List<String> ids = new ArrayList<>(activityIds(session.state()));

        session.removeFromProposal(ids.get(0)).removeFromProposal(ids.get(1))
                .removeFromProposal(ids.get(2));

        assertEquals(1, activityIds(session.state()).size(), describe(session.state()));
        assertTrue(travelIds(session.state()).isEmpty(),
                "one activity has nothing to travel between" + describe(session.state()));

        session.removeFromProposal(ids.get(3));
        assertTrue(activityIds(session.state()).isEmpty(), describe(session.state()));
        assertTrue(travelIds(session.state()).isEmpty(), describe(session.state()));
    }

    // 14
    @Test
    void theFiguresAreRecomputedFromTheEditedDraft() {
        Session session = new Session().preview();
        int travelBefore = session.state().getMetrics().getTravelAfterMinutes();

        session.removeFromProposal(activityIds(session.state()).get(1));

        int travelAfter = session.state().getMetrics().getTravelAfterMinutes();
        assertEquals(3, session.state().getMetrics().getActivityCount(),
                "the figures must describe the edited proposal, not the original one");
        assertTrue(travelAfter <= travelBefore,
                "a shorter day cannot need more journeys than it did: " + travelAfter
                        + " vs " + travelBefore);

        int drawn = 0;
        for (PreviewRowView row : session.state().getPreviewRows()) {
            if (row.getKind() == PreviewRowView.Kind.TRAVEL) {
                drawn += (row.getEnd().toSecondOfDay() - row.getStart().toSecondOfDay()) / 60;
            }
        }
        assertEquals(drawn, travelAfter, "the figure must be the travel drawn"
                + describe(session.state()));
    }

    // 17
    @Test
    void repeatedRemovalsDoNotDuplicateRows() {
        Session session = new Session().preview();
        List<String> ids = new ArrayList<>(activityIds(session.state()));

        session.removeFromProposal(ids.get(1)).removeFromProposal(ids.get(1));

        List<String> remaining = activityIds(session.state());
        assertEquals(remaining.size(), new java.util.HashSet<>(remaining).size(),
                "no duplicates" + describe(session.state()));
        List<String> legs = travelIds(session.state());
        assertEquals(legs.size(), new java.util.HashSet<>(legs).size(),
                "no duplicate journeys" + describe(session.state()));
    }

    // 12
    @Test
    void cancellingAfterADraftRemovalRestoresTheOriginalSavedDay() {
        Session session = new Session().preview();
        session.removeFromProposal(activityIds(session.state()).get(1));

        DayPlanState cancelled = session.state().clearedPreview("Autoschedule cancelled.");

        assertEquals(AutoScheduleStatus.IDLE, cancelled.getStatus());
        assertEquals(4, session.savedActivityCount(),
                "the saved day was never touched, so there is nothing to undo");
        int savedActivities = 0;
        for (ScheduledEvent event : cancelled.getEvents()) {
            if (event.getEventType() == EventType.ACTIVITY) {
                savedActivities++;
            }
        }
        assertEquals(4, savedActivities, "and the screen shows all four again");
    }

    // 16
    @Test
    void aReplacementJourneyThatWillNotFitRefusesRatherThanInventingOne() {
        Session session = new Session().preview();
        // Far enough apart that no gap in a fixed proposal could hold the journey.
        session.estimator.defaultMinutes(600);
        List<String> ids = activityIds(session.state());
        List<String> rowsBefore = activityIds(session.state());

        session.removeFromProposal(ids.get(1));

        assertEquals(AutoScheduleStatus.PREVIEW, session.state().getStatus(),
                "a refused edit leaves the proposal usable");
        assertEquals(rowsBefore, activityIds(session.state()),
                "and leaves it exactly as it was" + describe(session.state()));
        assertTrue(session.state().getMessage().contains("not enough time"),
                "and says why: " + session.state().getMessage());
        assertEquals(4, session.savedActivityCount(), "with nothing saved either way");
    }
}
