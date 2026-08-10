package use_case.autoschedule;

import static use_case.autoschedule.ProblemFixtures.at;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import entity.entities.Activity;
import entity.entities.ScheduledEvent;
import entity.entities.Trip;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.EventType;
import entity.valueobjects.IndoorOutdoorType;
import entity.valueobjects.Location;
import entity.valueobjects.TransportationMode;
import interface_adapter.controllers.AutoScheduleController;
import interface_adapter.controllers.TaskRunner;
import interface_adapter.presenters.AutoSchedulePresenter;
import interface_adapter.viewmodels.AutoScheduleStatus;
import interface_adapter.viewmodels.DayPlanState;
import interface_adapter.viewmodels.DayPlanViewModel;
import use_case.autoschedule.engine.ScheduleEngine;
import use_case.autoschedule.policy.DaylightPolicy;
import use_case.autoschedule.policy.MealWindowPolicy;
import use_case.autoschedule.policy.SoftPolicy;
import use_case.autoschedule.policy.WeatherSuitabilityPolicy;
import use_case.autoschedule.testdoubles.FakeTripRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Approving a Preview must save it, even though the world has moved on a little.
 *
 * <p>Reported from the running application: a correct, fully scrollable Preview was approved
 * and Apply refused with "the travel shown ... no longer fits". The gate was re-asking the
 * routing provider at Apply time and rejecting the proposal whenever the fresh answer came
 * back longer than the one the traveller had just been shown — which live traffic does
 * routinely, and which the Preview's own per-request snapshot deliberately insulates the
 * search from.</p>
 *
 * <p>The refusal was also unactionable: its advice was to run Autoschedule again, which simply
 * re-rolls the same dice. The checks that protect the day are arithmetic on the approved rows
 * — a journey that is missing, orphaned, or does not fit between its neighbours — and none of
 * them needs the network.</p>
 */
class ApplySurvivesRoutingDriftTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);
    private static final List<SoftPolicy> POLICIES = Arrays.asList(
            new WeatherSuitabilityPolicy(), new MealWindowPolicy(), new DaylightPolicy());

    /**
     * A live service whose answer worsens between the Preview and the Apply, which is the
     * whole of the reported failure. Flipped explicitly rather than by call count so the
     * moment the world moves is the moment the test says it does.
     */
    private static final class DriftingUpwards implements TravelTimeEstimator {
        private boolean trafficWorsened;

        private void trafficWorsens() {
            trafficWorsened = true;
        }

        @Override
        public TravelEstimate estimate(Location from, Location to, TransportationMode mode,
                                       LocalDateTime departure) {
            return TravelEstimate.routed(trafficWorsened ? 45 : 10);
        }

        @Override
        public boolean isTimeSensitive(TransportationMode mode) {
            return false;
        }
    }

    private static Activity place(String id, String name, double latitude, double longitude) {
        return new Activity(id, name, ActivityCategory.MUSEUM,
                new Location(latitude, longitude, id), 4.5, 60, at(8, 0), at(21, 0),
                IndoorOutdoorType.INDOOR, "none");
    }

    /** Two real, separated venues, in the shape the report described. */
    private static Trip twoPlaceDay() {
        Trip trip = new Trip("t", "Toronto", DATE, at(9, 0), at(21, 0),
                TransportationMode.WALKING);
        trip.replaceSchedule(Arrays.asList(
                new ScheduledEvent("coffee", place("coffee", "Mofer Coffee Front St",
                        43.6447, -79.3733), at(9, 0), at(10, 0), EventType.ACTIVITY, ""),
                new ScheduledEvent("cinema", place("cinema",
                        "Cineplex Cinemas Yonge-Dundas and VIP", 43.6563, -79.3806),
                        at(15, 0), at(16, 0), EventType.ACTIVITY, "")));
        return trip;
    }

    /** Preview, then Apply, through the real controller exactly as the button does. */
    @Test
    void approvingAPreviewSavesItEvenWhenRoutingDriftsUpwardsAfterwards() {
        Trip trip = twoPlaceDay();
        FakeTripRepository trips = new FakeTripRepository(trip);
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState("t",
                trip.getScheduledEvents(), "", false, Collections.emptyList()));
        DriftingUpwards routing = new DriftingUpwards();
        AutoScheduleInteractor interactor = new AutoScheduleInteractor(trips,
                routing, new use_case.autoschedule.testdoubles.FakeWeatherContextGateway(),
                new AutoSchedulePresenter(viewModel), POLICIES, new ScheduleEngine());
        AutoScheduleController controller =
                new AutoScheduleController(interactor, viewModel, TaskRunner.immediate());

        interactor.preview(new AutoScheduleInputData("t", at(9, 0), at(21, 0),
                TransportationMode.WALKING, Collections.emptySet(),
                Collections.emptyList(), false, true));
        assertEquals(AutoScheduleStatus.PREVIEW, viewModel.getState().getStatus(),
                viewModel.getState().getMessage());

        // The traveller reads the proposal, is satisfied, and presses Apply. Traffic has
        // meanwhile got worse.
        routing.trafficWorsens();
        controller.apply();

        DayPlanState after = viewModel.getState();
        assertFalse(after.isError(),
                "approving a valid Preview must save it: " + after.getMessage());
        assertFalse(after.getMessage().contains("no longer fits"),
                "the outside world moving is not a corrupted proposal: " + after.getMessage());
        assertFalse(after.getMessage().contains("does not fit"), after.getMessage());
        assertEquals(AutoScheduleStatus.APPLIED, after.getStatus(), after.getMessage());
    }

    /** The message must never send the traveller round a loop that cannot help them. */
    @Test
    void applyDoesNotAskTheTravellerToRerunAgainstTheSameRandomness() {
        Trip trip = twoPlaceDay();
        FakeTripRepository trips = new FakeTripRepository(trip);
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState("t",
                trip.getScheduledEvents(), "", false, Collections.emptyList()));
        DriftingUpwards routing = new DriftingUpwards();
        AutoScheduleInteractor interactor = new AutoScheduleInteractor(trips,
                routing, new use_case.autoschedule.testdoubles.FakeWeatherContextGateway(),
                new AutoSchedulePresenter(viewModel), POLICIES, new ScheduleEngine());
        new AutoScheduleController(interactor, viewModel, TaskRunner.immediate());

        interactor.preview(new AutoScheduleInputData("t", at(9, 0), at(21, 0),
                TransportationMode.WALKING, Collections.emptySet(),
                Collections.emptyList(), false, true));
        routing.trafficWorsens();
        int activitiesBefore = trips.findById("t").get().getScheduledEvents().size();

        interactor.apply(new AutoScheduleApplyInputData("t",
                viewModel.getState().getPreviewFingerprint(),
                proposedRows(viewModel.getState())));

        assertEquals(AutoScheduleStatus.APPLIED, viewModel.getState().getStatus(),
                viewModel.getState().getMessage());
        assertTrue(trips.findById("t").get().getScheduledEvents().size() >= activitiesBefore,
                "and the day is saved rather than left untouched");
    }

    /** The structural guarantees the gate exists for are still enforced. */
    @Test
    void aProposalMissingAJourneyBetweenTwoDifferentPlacesIsStillRefused() {
        Trip trip = twoPlaceDay();
        FakeTripRepository trips = new FakeTripRepository(trip);
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState("t",
                trip.getScheduledEvents(), "", false, Collections.emptyList()));
        AutoScheduleInteractor interactor = new AutoScheduleInteractor(trips,
                new DriftingUpwards(), new use_case.autoschedule.testdoubles
                        .FakeWeatherContextGateway(),
                new AutoSchedulePresenter(viewModel), POLICIES, new ScheduleEngine());

        interactor.preview(new AutoScheduleInputData("t", at(9, 0), at(21, 0),
                TransportationMode.WALKING, Collections.emptySet(),
                Collections.emptyList(), false, true));
        String fingerprint = viewModel.getState().getPreviewFingerprint();

        // Two separated venues back to back with the journey stripped out.
        List<ProposedEventData> tampered = Arrays.asList(
                new ProposedEventData("coffee", "coffee", "Mofer Coffee Front St",
                        ProposedEventData.Kind.ACTIVITY, at(9, 0), at(10, 0), false, false),
                new ProposedEventData("cinema", "cinema",
                        "Cineplex Cinemas Yonge-Dundas and VIP",
                        ProposedEventData.Kind.ACTIVITY, at(10, 0), at(11, 0), false, false));

        interactor.apply(new AutoScheduleApplyInputData("t", fingerprint, tampered));

        assertTrue(viewModel.getState().isError(),
                "saving two different places with no journey between them is the hole this "
                        + "gate exists to catch");
        assertTrue(viewModel.getState().getMessage().contains("missing the required travel"),
                viewModel.getState().getMessage());
        assertEquals(2, trips.findById("t").get().getScheduledEvents().size(),
                "and nothing was written");
    }

    /** A journey longer than the gap it sits in is arithmetic, not a provider opinion. */
    @Test
    void aJourneyThatCannotFitBetweenItsNeighboursIsStillRefused() {
        Trip trip = twoPlaceDay();
        FakeTripRepository trips = new FakeTripRepository(trip);
        DayPlanViewModel viewModel = new DayPlanViewModel(new DayPlanState("t",
                trip.getScheduledEvents(), "", false, Collections.emptyList()));
        AutoScheduleInteractor interactor = new AutoScheduleInteractor(trips,
                new DriftingUpwards(), new use_case.autoschedule.testdoubles
                        .FakeWeatherContextGateway(),
                new AutoSchedulePresenter(viewModel), POLICIES, new ScheduleEngine());

        interactor.preview(new AutoScheduleInputData("t", at(9, 0), at(21, 0),
                TransportationMode.WALKING, Collections.emptySet(),
                Collections.emptyList(), false, true));
        String fingerprint = viewModel.getState().getPreviewFingerprint();

        // A 40-minute journey wedged into a 10-minute gap.
        List<ProposedEventData> tampered = Arrays.asList(
                new ProposedEventData("coffee", "coffee", "Mofer Coffee Front St",
                        ProposedEventData.Kind.ACTIVITY, at(9, 0), at(10, 0), false, false),
                new ProposedEventData("travel-cinema", "", "Travel",
                        ProposedEventData.Kind.TRAVEL, at(9, 50), at(10, 30), false, false),
                new ProposedEventData("cinema", "cinema",
                        "Cineplex Cinemas Yonge-Dundas and VIP",
                        ProposedEventData.Kind.ACTIVITY, at(10, 10), at(11, 10), false, false));

        interactor.apply(new AutoScheduleApplyInputData("t", fingerprint, tampered));

        assertTrue(viewModel.getState().isError(), viewModel.getState().getMessage());
        assertNotNull(viewModel.getState().getMessage());
        assertEquals(2, trips.findById("t").get().getScheduledEvents().size(),
                "and nothing was written");
    }

    private static List<ProposedEventData> proposedRows(DayPlanState state) {
        List<ProposedEventData> rows = new java.util.ArrayList<>();
        for (interface_adapter.viewmodels.PreviewRowView row : state.getPreviewRows()) {
            rows.add(new ProposedEventData(row.getEventId(), "", row.getTitle(),
                    row.getKind() == interface_adapter.viewmodels.PreviewRowView.Kind.TRAVEL
                            ? ProposedEventData.Kind.TRAVEL : ProposedEventData.Kind.ACTIVITY,
                    row.getStart(), row.getEnd(), row.isLocked(), row.isMoved()));
        }
        return rows;
    }
}
